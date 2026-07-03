package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Qute;
import io.quarkus.qute.Template;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URL;
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

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    Vertx vertx;

    @Location("webhook_notification")
    Template defaultTemplate;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx);
    }

    @Override
    public NotificationMethod method() {
        return NotificationMethod.WEBHOOK;
    }

    @Override
    public void send(ChangeNotification notification) {
        String urlStr = notification.configData().getField("url").asString(null);
        if (urlStr == null || urlStr.isBlank()) {
            throw new IllegalArgumentException("Webhook config is missing required 'url' field");
        }
        String authHeader = notification.configSecrets().getField("authHeader").asString(null);

        JqObject payload = buildPayload(notification);

        URL url;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid webhook URL: " + urlStr, e);
        }

        RequestOptions options = new RequestOptions()
            .setHost(url.getHost())
            .setPort(url.getPort() != -1 ? url.getPort() : url.getDefaultPort())
            .setURI(url.getPath() + (url.getQuery() != null ? "?" + url.getQuery() : ""))
            .setSsl("https".equalsIgnoreCase(url.getProtocol()));

        var request = webClient.request(HttpMethod.POST, options)
            .putHeader("Content-Type", "application/json")
            .putHeader("User-Agent", "h5m");

        if (authHeader != null) {
            request.putHeader("Authorization", authHeader);
        }

        HttpResponse<Buffer> response = request
            .sendBuffer(Buffer.buffer(payload.toJsonString()))
            .await().atMost(TIMEOUT);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Webhook returned HTTP " + response.statusCode()
                + ": " + response.bodyAsString());
        }
        Log.debugf("Webhook delivered to %s (HTTP %d)", urlStr, response.statusCode());
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
            new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid webhook URL: " + url, e);
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("Webhook URL must start with http:// or https://");
        }
    }

    private JqObject buildPayload(ChangeNotification notification) {
        JqObject.Builder payloadBuilder = JqObject.builder();
        payloadBuilder.put("folder", notification.folderName());
        payloadBuilder.put("nodeId", notification.nodeId());
        payloadBuilder.put("nodeName", notification.nodeName());
        payloadBuilder.put("nodeType", notification.nodeType());
        payloadBuilder.put("changeCount", (long) notification.changes().size());

        // Include formatted text — compatible with Slack incoming webhooks
        String text = formatMessage(notification);
        payloadBuilder.put("text", text);

        JqValue[] changeElements = new JqValue[notification.changes().size()];
        for (int i = 0; i < notification.changes().size(); i++) {
            ChangeDetail change = notification.changes().get(i);
            JqObject.Builder changeBuilder = JqObject.builder();
            changeBuilder.put("valueId", change.valueId());
            if (change.data() != null) {
                changeBuilder.put("data", change.data());
            }
            if (change.fingerprint() != null) {
                changeBuilder.put("fingerprint", change.fingerprint());
            }
            changeElements[i] = changeBuilder.build();
        }
        payloadBuilder.put("changes", JqArray.of(changeElements));

        return payloadBuilder.build();
    }

    private String formatMessage(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return applyTemplate(notification.template(), notification);
        }
        return defaultTemplate
            .data("folderName", notification.folderName())
            .data("nodeName", notification.nodeName())
            .data("nodeType", notification.nodeType())
            .data("changeCount", notification.changes().size())
            .data("changes", notification.changes())
            .render();
    }

    private String applyTemplate(String template, ChangeNotification notification) {
        return Qute.fmt(template)
            .data("folderName", notification.folderName())
            .data("nodeName", notification.nodeName())
            .data("nodeType", notification.nodeType())
            .data("changeCount", notification.changes().size())
            .data("changes", notification.changes())
            .render();
    }

    private String extractUrl(String configData) {
        try {
            JqValue config = JqValues.parse(configData);
            if (config instanceof JqObject obj && obj.has("url")) {
                return obj.get("url").asString("");
            }
            // If it's not a JSON object, treat the entire string as the URL
            return configData.trim();
        } catch (Exception e) {
            // Not valid JSON — treat as a plain URL
            return configData.trim();
        }
    }
}
