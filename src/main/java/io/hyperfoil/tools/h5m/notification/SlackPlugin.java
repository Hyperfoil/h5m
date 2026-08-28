package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Notification plugin that posts change notifications to Slack using the
 * <a href="https://api.slack.com/methods/chat.postMessage">Slack Web API</a>
 * with <a href="https://api.slack.com/reference/block-kit">Block Kit</a> formatting.
 * Uses the Vert.x Web Client for HTTP requests.
 * <p>
 * Configuration: {@link SlackConfig} — {@code channel}.
 * Secret: {@link TokenSecret} — {@code token}.
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
    public void send(ChangeEvent event, NotificationConfiguration config, NotificationSecret secret, String template) {
        SlackConfig cfg = configAs(SlackConfig.class, config);
        String channel = cfg.channel();
        String token = secret instanceof TokenSecret(var _, String t) ? t : null;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Slack secret must contain a 'token' field");
        }

        JqObject payload = buildSlackPayload(channel, event, template);

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

    private JqObject buildSlackPayload(String channel, ChangeEvent event, String template) {
        Change first = event.changes().getFirst();
        String fallbackText = String.format("Change detected in %s by %s: %d change(s)",
            event.folderName(), first.nodeName(), event.changes().size());

        // Build blocks array
        List<JqValue> blockList = new ArrayList<>();

        // Header block
        blockList.add(JqObject.of("type", JqString.of("header"),
            "text", JqObject.of("type", JqString.of("plain_text"),
                "text", JqString.of(String.format("Change detected in %s", event.folderName())))));

        // Main section with markdown
        blockList.add(JqObject.of("type", JqString.of("section"),
            "text", JqObject.of("type", JqString.of("mrkdwn"),
                "text", JqString.of(buildMarkdownBody(event, template)))));

        // Change details sections
        for (int i = 0; i < event.changes().size(); i++) {
            Change change = event.changes().get(i);
            blockList.add(JqObject.of("type", JqString.of("section"),
                "text", JqObject.of("type", JqString.of("mrkdwn"),
                    "text", JqString.of(formatChangeDetail(i + 1, change, first.nodeType())))));
        }

        // Divider
        blockList.add(JqObject.of("type", JqString.of("divider")));

        // Context block
        blockList.add(JqObject.of("type", JqString.of("context"),
            "elements", JqArray.of(
                JqObject.of("type", JqString.of("mrkdwn"),
                    "text", JqString.of(String.format("Node: `%s` (%s) | Changes: %d",
                        first.nodeName(), first.nodeType(), event.changes().size()))))));

        return JqObject.of("channel", JqString.of(channel),
            "text", JqString.of(fallbackText),
            "blocks", JqArray.of(blockList.toArray(new JqValue[0])));
    }

    private String buildMarkdownBody(ChangeEvent event, String template) {
        if (template != null && !template.isBlank()) {
            return applyTemplate(template, event);
        }
        Change first = event.changes().getFirst();
        return NotificationTemplates.slack(
            event.folderName(), first.nodeName(), first.nodeType(), event.changes().size(), event.changes())
            .render();
    }

    private String applyTemplate(String template, ChangeEvent event) {
        Change first = event.changes().getFirst();
        return Qute.fmt(template)
            .data("folderName", event.folderName())
            .data("nodeName", first.nodeName())
            .data("nodeType", first.nodeType())
            .data("changeCount", event.changes().size())
            .data("changes", event.changes())
            .render();
    }

    private String formatChangeDetail(int index, Change change, NodeType nodeType) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Change ").append(index).append("*");
        if (change.fingerprint() != null) {
            sb.append(" — Fingerprint: `").append(change.fingerprint().toJsonString()).append("`");
        }
        sb.append("\n");
        if (change.data() instanceof JqObject obj) {
            switch (nodeType) {
                case FIXED_THRESHOLD -> {
                    if (obj.has("value")) sb.append("• Value: `").append(obj.get("value").toJsonString()).append("`\n");
                    if (obj.has("bound")) sb.append("• Bound: `").append(obj.get("bound").toJsonString()).append("`\n");
                    if (obj.has("direction")) sb.append("• Direction: ").append(obj.get("direction").asText()).append("\n");
                }
                case RELATIVE_DIFFERENCE -> {
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
}
