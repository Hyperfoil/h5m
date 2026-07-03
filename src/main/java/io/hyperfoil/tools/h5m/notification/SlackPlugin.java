package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.qute.Qute;
import io.vertx.mutiny.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
        String channel = notification.configData().get("channel").asString(null);
        if (notification.configSecrets() == null || !notification.configSecrets().has("token")) {
            throw new IllegalArgumentException("Slack secrets must contain a 'token' field");
        }
        String token = notification.configSecrets().get("token").asString(null);
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Slack secrets must contain a 'token' field");
        }

        JqObject payload = buildSlackPayload(channel, notification);

        HttpResponse<Buffer> response = webClient.postAbs(slackApiUrl)
            .putHeader("Content-Type", "application/json; charset=utf-8")
            .putHeader("Authorization", "Bearer " + token)
            .putHeader("User-Agent", "h5m")
            .sendBuffer(Buffer.buffer(payload.toJsonString()))
            .await().atMost(TIMEOUT);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Slack API returned HTTP " + response.statusCode()
                + ": " + response.bodyAsString());
        }
        // Slack returns 200 even on errors — check the "ok" field
        try {
            JqValue body = JqValues.parse(response.bodyAsString());
            if (body instanceof JqObject obj) {
                if (!obj.has("ok") || !obj.get("ok").asBoolean(false)) {
                    String error = obj.has("error") ? obj.get("error").asString("unknown error") : "unknown error";
                    throw new RuntimeException("Slack API error: " + error);
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
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

    private JqObject buildSlackPayload(String channel, ChangeNotification notification) {
        String fallbackText = String.format("Change detected in %s by %s: %d change(s)",
            notification.folderName(), notification.nodeName(), notification.changes().size());

        // Build blocks array
        java.util.List<JqValue> blockList = new java.util.ArrayList<>();

        // Header block
        blockList.add(JqObject.of("type", JqString.of("header"),
            "text", JqObject.of("type", JqString.of("plain_text"),
                "text", JqString.of(String.format("Change detected in %s", notification.folderName())))));

        // Main section with markdown
        blockList.add(JqObject.of("type", JqString.of("section"),
            "text", JqObject.of("type", JqString.of("mrkdwn"),
                "text", JqString.of(buildMarkdownBody(notification)))));

        // Change details sections
        for (int i = 0; i < notification.changes().size(); i++) {
            ChangeDetail change = notification.changes().get(i);
            blockList.add(JqObject.of("type", JqString.of("section"),
                "text", JqObject.of("type", JqString.of("mrkdwn"),
                    "text", JqString.of(formatChangeDetail(i + 1, change, notification.nodeType())))));
        }

        // Divider
        blockList.add(JqObject.of("type", JqString.of("divider")));

        // Context block
        blockList.add(JqObject.of("type", JqString.of("context"),
            "elements", JqArray.of(
                JqObject.of("type", JqString.of("mrkdwn"),
                    "text", JqString.of(String.format("Node: `%s` (%s) | Changes: %d",
                        notification.nodeName(), notification.nodeType(), notification.changes().size()))))));

        return JqObject.of("channel", JqString.of(channel),
            "text", JqString.of(fallbackText),
            "blocks", JqArray.of(blockList.toArray(new JqValue[0])));
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
            sb.append(" — Fingerprint: `").append(change.fingerprint().toJsonString()).append("`");
        }
        sb.append("\n");
        if (change.data() instanceof JqObject obj) {
            switch (nodeType) {
                case "ft" -> {
                    if (obj.has("value")) sb.append("• Value: `").append(obj.get("value").toJsonString()).append("`\n");
                    if (obj.has("bound")) sb.append("• Bound: `").append(obj.get("bound").toJsonString()).append("`\n");
                    if (obj.has("direction")) sb.append("• Direction: ").append(obj.get("direction").asText()).append("\n");
                }
                case "rd" -> {
                    if (obj.has("ratio")) sb.append("• Ratio: `").append(String.format("%.1f%%", obj.get("ratio").asDouble(0.0))).append("`\n");
                    if (obj.has("value")) sb.append("• Value: `").append(obj.get("value").toJsonString()).append("`\n");
                    if (obj.has("previous")) sb.append("• Previous: `").append(obj.get("previous").toJsonString()).append("`\n");
                }
                default -> sb.append("• Data: `").append(obj.toJsonString()).append("`\n");
            }
        } else if (change.data() != null) {
            sb.append("• Data: `").append(change.data().toJsonString()).append("`\n");
        }
        return sb.toString();
    }

    private String extractField(String json, String field) {
        if (json == null) return null;
        try {
            JqValue parsed = JqValues.parse(json);
            if (parsed instanceof JqObject obj && obj.has(field)) {
                return obj.get(field).asString("");
            }
        } catch (Exception e) {
            // not valid JSON
        }
        return null;
    }
}
