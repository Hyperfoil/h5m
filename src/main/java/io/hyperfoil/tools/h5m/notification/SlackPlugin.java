package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.qute.Qute;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * Notification plugin that posts change notifications to Slack using the
 * <a href="https://api.slack.com/methods/chat.postMessage">Slack Web API</a>
 * with <a href="https://api.slack.com/reference/block-kit">Block Kit</a> formatting.
 * Uses the Vert.x Web Client for HTTP requests.
 * <p>
 * Configuration data (JSON):
 * <pre>{"channel": "#perf-alerts"}</pre>
 * <p>
 * Secret data (JSON):
 * <pre>{"token": "xoxb-your-bot-token"}</pre>
 */
@ApplicationScoped
public class SlackPlugin implements NotificationPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "h5m.slack.api.url")
    String slackApiUrl;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx);
    }

    @Override
    public NotificationMethod method() {
        return NotificationMethod.SLACK;
    }

    @Override
    public void send(ChangeNotification notification) {
        String channel = extractField(notification.configData(), "channel");
        String token = extractToken(notification.configSecrets());

        ObjectNode payload = buildSlackPayload(channel, notification);

        HttpResponse<Buffer> response = webClient.postAbs(slackApiUrl)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .putHeader("Authorization", "Bearer " + token)
            .putHeader("User-Agent", "h5m")
            .sendBuffer(Buffer.buffer(payload.toString()))
            .await().atMost(TIMEOUT);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Slack API returned HTTP " + response.statusCode()
                + ": " + response.bodyAsString());
        }
        // Slack returns 200 even on errors — check the "ok" field
        try {
            JsonNode body = MAPPER.readTree(response.bodyAsString());
            if (!body.path("ok").asBoolean(false)) {
                String error = body.path("error").asText("unknown error");
                throw new RuntimeException("Slack API error: " + error);
            }
        } catch (JsonProcessingException e) {
            // Can't parse response — assume success if HTTP was 2xx
        }
        Log.debugf("Slack message posted to %s", channel);
    }

    @Override
    public void validate(String configData) {
        if (configData == null || configData.isBlank()) {
            throw new IllegalArgumentException("Slack config is required");
        }
        String channel = extractField(configData, "channel");
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Slack config must contain a 'channel' field (e.g. #perf-alerts)");
        }
    }

    private ObjectNode buildSlackPayload(String channel, ChangeNotification notification) {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("channel", channel);

        String fallbackText = String.format("Change detected in %s by %s: %d change(s)",
            notification.folderName(), notification.nodeName(), notification.changes().size());
        payload.put("text", fallbackText);

        ArrayNode blocks = payload.putArray("blocks");

        // Header block
        ObjectNode header = blocks.addObject();
        header.put("type", "header");
        ObjectNode headerText = header.putObject("text");
        headerText.put("type", "plain_text");
        headerText.put("text", String.format("Change detected in %s", notification.folderName()));

        // Main section with markdown
        ObjectNode section = blocks.addObject();
        section.put("type", "section");
        ObjectNode sectionText = section.putObject("text");
        sectionText.put("type", "mrkdwn");
        sectionText.put("text", buildMarkdownBody(notification));

        // Change details sections
        for (int i = 0; i < notification.changes().size(); i++) {
            ChangeDetail change = notification.changes().get(i);
            ObjectNode detailSection = blocks.addObject();
            detailSection.put("type", "section");
            ObjectNode detailText = detailSection.putObject("text");
            detailText.put("type", "mrkdwn");
            detailText.put("text", formatChangeDetail(i + 1, change, notification.nodeType()));
        }

        // Divider
        blocks.addObject().put("type", "divider");

        // Context block
        ObjectNode context = blocks.addObject();
        context.put("type", "context");
        ArrayNode elements = context.putArray("elements");
        ObjectNode contextText = elements.addObject();
        contextText.put("type", "mrkdwn");
        contextText.put("text", String.format("Node: `%s` (%s) | Changes: %d",
            notification.nodeName(), notification.nodeType(), notification.changes().size()));

        return payload;
    }

    private String buildMarkdownBody(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return applyTemplate(notification.template(), notification);
        }
        return String.format("*%s* detected by `%s` (%s)",
            notification.changes().size() == 1 ? "1 change" : notification.changes().size() + " changes",
            notification.nodeName(),
            notification.nodeType());
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

    private String formatChangeDetail(int index, ChangeDetail change, String nodeType) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Change ").append(index).append("*");
        if (change.fingerprint() != null) {
            sb.append(" — Fingerprint: `").append(change.fingerprint()).append("`");
        }
        sb.append("\n");
        if (change.data() != null) {
            switch (nodeType) {
                case "ft" -> {
                    if (change.data().has("value")) sb.append("• Value: `").append(change.data().get("value")).append("`\n");
                    if (change.data().has("bound")) sb.append("• Bound: `").append(change.data().get("bound")).append("`\n");
                    if (change.data().has("direction")) sb.append("• Direction: ").append(change.data().get("direction").asText()).append("\n");
                }
                case "rd" -> {
                    if (change.data().has("ratio")) sb.append("• Ratio: `").append(String.format("%.1f%%", change.data().get("ratio").asDouble())).append("`\n");
                    if (change.data().has("value")) sb.append("• Value: `").append(change.data().get("value")).append("`\n");
                    if (change.data().has("previous")) sb.append("• Previous: `").append(change.data().get("previous")).append("`\n");
                }
                default -> sb.append("• Data: `").append(change.data()).append("`\n");
            }
        }
        return sb.toString();
    }

    private String extractField(String json, String field) {
        if (json == null) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.has(field)) {
                return node.get(field).asText();
            }
        } catch (JsonProcessingException e) {
            // not valid JSON
        }
        return null;
    }

    private String extractToken(String secrets) {
        if (secrets == null || secrets.isBlank()) {
            throw new IllegalArgumentException("Slack secrets must contain a 'token' field");
        }
        String token = extractField(secrets, "token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Slack secrets must contain a 'token' field");
        }
        return token;
    }
}
