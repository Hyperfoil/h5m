package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.AuthHeaderSecret;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import io.quarkus.logging.Log;
import io.quarkus.qute.Qute;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.core.buffer.Buffer;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * Notification plugin that sends change notifications via HTTP POST (webhook).
 * <p>
 * Configuration: {@link WebhookConfig} — {@code url}.
 * Secret (optional): {@link AuthHeaderSecret} — {@code authHeader}.
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
    public void send(ChangeEvent event, NotificationConfiguration config, NotificationSecret secret, String template) {
        WebhookConfig cfg = configAs(WebhookConfig.class, config);
        String urlStr = cfg.url();
        if (urlStr == null || urlStr.isBlank()) {
            throw new IllegalArgumentException("Webhook config is missing required 'url' field");
        }
        String authHeader = secret instanceof AuthHeaderSecret(var _, String header) ? header : null;
        JqObject payload = buildPayload(event, template);

        URL url;
        try {
            url = URI.create(urlStr).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {
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

    private JqObject buildPayload(ChangeEvent event, String template) {
        Change first = event.changes().getFirst();
        JqObject.Builder payloadBuilder = JqObject.builder();
        payloadBuilder.put("folder", event.folderName());
        payloadBuilder.put("folderId", event.folderId());
        payloadBuilder.put("valueId", event.rootValueId());
        payloadBuilder.put("nodeId", first.nodeId());
        payloadBuilder.put("nodeName", first.nodeName());
        payloadBuilder.put("nodeType", first.nodeType().name());
        payloadBuilder.put("changeCount", (long) event.changes().size());

        // Include formatted text — compatible with Slack incoming webhooks
        String text = formatMessage(event, template);
        payloadBuilder.put("text", text);

        JqValue[] changeElements = new JqValue[event.changes().size()];
        for (int i = 0; i < event.changes().size(); i++) {
            Change change = event.changes().get(i);
            JqObject.Builder changeBuilder = JqObject.builder();
            changeBuilder.put("valueId", change.valueId());
            changeBuilder.put("nodeId", change.nodeId());
            changeBuilder.put("nodeName", change.nodeName());
            if (change.nodeType() != null) {
                changeBuilder.put("nodeType", change.nodeType().name());
            }
            if (change.data() != null) {
                changeBuilder.put("data", change.data());
            }
            if (change.fingerprint() != null) {
                changeBuilder.put("fingerprint", change.fingerprint());
            }
            changeElements[i] = changeBuilder.build();
        }
        payloadBuilder.put("changes", JqArray.of(changeElements));

        // API links for follow-up queries
        JqObject.Builder linksBuilder = JqObject.builder();
        linksBuilder.put("processing", "/api/processing/" + event.rootValueId());
        linksBuilder.put("labelValues", "/api/folder/" + event.folderId() + "/labelValues");
        payloadBuilder.put("links", linksBuilder.build());

        return payloadBuilder.build();
    }

    private String formatMessage(ChangeEvent event, String template) {
        Change first = event.changes().getFirst();
        if (template != null && !template.isBlank()) {
            return Qute.fmt(template)
                .data("folderName", event.folderName())
                .data("nodeName", first.nodeName())
                .data("nodeType", first.nodeType())
                .data("changeCount", event.changes().size())
                .data("changes", event.changes())
                .render();
        }
        return NotificationTemplates.webhook(
            event.folderName(), first.nodeName(), first.nodeType(), event.changes().size(), event.changes())
            .render();
    }
}
