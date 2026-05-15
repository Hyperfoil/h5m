package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Notification plugin that sends change notifications via HTTP POST (webhook).
 * <p>
 * Configuration data (JSON):
 * <pre>{"url": "https://hooks.example.com/endpoint"}</pre>
 * <p>
 * Secret data (JSON, optional):
 * <pre>{"authHeader": "Bearer token123"}</pre>
 * <p>
 * The payload is a JSON object containing the folder name, detection node info,
 * and change details. If a custom template is provided, it is included as a
 * {@code text} field in the payload — this makes it compatible with Slack
 * incoming webhooks which use the {@code text} field for the message body.
 */
@ApplicationScoped
public class WebhookPlugin implements NotificationPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Override
    public NotificationMethod method() {
        return NotificationMethod.WEBHOOK;
    }

    @Override
    public void send(ChangeNotification notification) {
        String url = extractUrl(notification.configData());
        String authHeader = extractAuthHeader(notification.configSecrets());

        ObjectNode payload = buildPayload(notification);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader);
        }

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Webhook returned HTTP " + response.statusCode()
                    + ": " + response.body());
            }
            Log.debugf("Webhook delivered to %s (HTTP %d)", url, response.statusCode());
        } catch (IOException e) {
            throw new RuntimeException("Failed to send webhook to " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Webhook request interrupted", e);
        }
    }

    @Override
    public void validate(String configData) {
        if (configData == null || configData.isBlank()) {
            throw new IllegalArgumentException("Webhook config data is required");
        }
        String url = extractUrl(configData);
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook config must contain a 'url' field");
        }
        try {
            URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + url, e);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Webhook URL must start with http:// or https://");
        }
    }

    private ObjectNode buildPayload(ChangeNotification notification) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("folder", notification.folderName());
        payload.put("nodeId", notification.nodeId());
        payload.put("nodeName", notification.nodeName());
        payload.put("nodeType", notification.nodeType());
        payload.put("changeCount", notification.changes().size());

        // Include formatted text — compatible with Slack incoming webhooks
        String text = formatMessage(notification);
        payload.put("text", text);

        ArrayNode changesArray = payload.putArray("changes");
        for (ChangeDetail change : notification.changes()) {
            ObjectNode changeNode = changesArray.addObject();
            changeNode.put("valueId", change.valueId());
            if (change.data() != null) {
                changeNode.set("data", change.data());
            }
            if (change.fingerprint() != null) {
                changeNode.set("fingerprint", change.fingerprint());
            }
        }

        return payload;
    }

    private String formatMessage(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return applyTemplate(notification.template(), notification);
        }
        return String.format("Change detected in %s by %s (%s): %d change(s)",
            notification.folderName(),
            notification.nodeName(),
            notification.nodeType(),
            notification.changes().size());
    }

    private String applyTemplate(String template, ChangeNotification notification) {
        return template
            .replace("{folderName}", notification.folderName())
            .replace("{nodeName}", notification.nodeName())
            .replace("{nodeType}", notification.nodeType())
            .replace("{changeCount}", String.valueOf(notification.changes().size()));
    }

    private String extractUrl(String configData) {
        try {
            JsonNode config = MAPPER.readTree(configData);
            if (config.has("url")) {
                return config.get("url").asText();
            }
            // If it's not JSON, treat the entire string as the URL
            return configData.trim();
        } catch (JsonProcessingException e) {
            // Not valid JSON — treat as a plain URL
            return configData.trim();
        }
    }

    private String extractAuthHeader(String configSecrets) {
        if (configSecrets == null || configSecrets.isBlank()) {
            return null;
        }
        try {
            JsonNode secrets = MAPPER.readTree(configSecrets);
            if (secrets.has("authHeader")) {
                return secrets.get("authHeader").asText();
            }
        } catch (JsonProcessingException e) {
            // ignore malformed secrets
        }
        return null;
    }
}
