package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.GitHubIssueConfig;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;

import io.hyperfoil.tools.h5m.api.Change;
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

import java.time.Duration;

/**
 * Notification plugin that creates GitHub issues for detected changes.
 * Uses the Vert.x Web Client for HTTP requests.
 * <p>
 * Configuration: {@link GitHubIssueConfig} — {@code owner}, {@code repo}, optional {@code title}/{@code labels}.
 * Secret: {@link TokenSecret} — {@code token}.
 */
@ApplicationScoped
public class GitHubIssuePlugin implements NotificationPlugin {

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
        return NotificationMethod.GITHUB_ISSUE;
    }

    @Override
    public void send(ChangeEvent event, NotificationConfiguration config, NotificationSecret secret, String template) {
        GitHubIssueConfig cfg = configAs(GitHubIssueConfig.class, config);
        String token = secret instanceof TokenSecret(var _, String t) ? t : null;
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("GitHub issue secret must contain a 'token' field");
        }
        String title = buildTitle(cfg, event);
        String body = buildBody(event, template);

        JqObject.Builder payloadBuilder = JqObject.builder();
        payloadBuilder.put("title", title);
        payloadBuilder.put("body", body);

        // Add labels if configured, defaulting to a single "h5m" label
        JqValue labels;
        if (cfg.labels() != null && !cfg.labels().isEmpty()) {
            JqValue[] labelValues = cfg.labels().stream().map(JqString::of).toArray(JqValue[]::new);
            labels = JqArray.of(labelValues);
        } else {
            labels = JqArray.of(JqString.of("h5m"));
        }
        payloadBuilder.put("labels", labels);

        JqObject payload = payloadBuilder.build();
        String path = "/repos/" + cfg.owner() + "/" + cfg.repo() + "/issues";

        HttpResponse<Buffer> response = webClient.postAbs("https://api.github.com" + path)
            .putHeader("Content-Type", "application/vnd.github+json")
            .putHeader("Authorization", "Bearer " + token)
            .putHeader("User-Agent", "h5m")
            .sendBuffer(Buffer.buffer(payload.toJsonString()))
            .await().atMost(TIMEOUT);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("GitHub API returned HTTP " + response.statusCode()
                + " for " + path + ": " + response.bodyAsString());
        }

        try {
            JqValue responseJson = JqValues.parse(response.bodyAsString());
            if (responseJson instanceof JqObject obj && obj.has("html_url")) {
                Log.infof("Created GitHub issue: %s", obj.get("html_url").asString(""));
            } else {
                Log.infof("Created GitHub issue in %s (HTTP %d)", path, response.statusCode());
            }
        } catch (Exception e) {
            Log.infof("Created GitHub issue in %s (HTTP %d)", path, response.statusCode());
        }
    }

    private String buildTitle(GitHubIssueConfig cfg, ChangeEvent event) {
        Change first = event.changes().getFirst();
        String customTitle = cfg.title();
        if (customTitle != null && !customTitle.isBlank()) {
            return applyTemplate(customTitle, event);
        }
        return String.format("[h5m] Change detected in %s by %s", event.folderName(), first.nodeName());
    }

    private String buildBody(ChangeEvent event, String template) {
        Change first = event.changes().getFirst();
        if (template != null && !template.isBlank()) {
            return applyTemplate(template, event);
        }
        return NotificationTemplates.github_issue(
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
}
