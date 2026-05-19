package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.qute.Location;
import io.quarkus.qute.Qute;
import io.quarkus.qute.Template;
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
 * Configuration data (JSON):
 * <pre>{"owner": "myorg", "repo": "perf-results", "title": "Regression in {folderName}"}</pre>
 * <p>
 * Optional fields:
 * <pre>{"owner": "myorg", "repo": "perf-results", "labels": ["regression", "automated"]}</pre>
 * <p>
 * Secret data (JSON):
 * <pre>{"token": "ghp_xxxxxxxxxxxx"}</pre>
 */
@ApplicationScoped
public class GitHubIssuePlugin implements NotificationPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Inject
    Vertx vertx;

    @Location("github_issue_body.md")
    Template defaultBodyTemplate;

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
    public void send(ChangeNotification notification) {
        String owner = extractField(notification.configData(), "owner");
        String repo = extractField(notification.configData(), "repo");
        String token = extractToken(notification.configSecrets());
        String title = buildTitle(notification);
        String body = buildBody(notification);

        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("title", title);
        payload.put("body", body);

        // Add labels if configured
        String labelsStr = extractField(notification.configData(), "labels");
        if (labelsStr == null) {
            payload.set("labels", MAPPER.createArrayNode().add("h5m"));
        } else {
            try {
                JsonNode labels = MAPPER.readTree(notification.configData()).get("labels");
                if (labels != null && labels.isArray()) {
                    payload.set("labels", labels);
                }
            } catch (JsonProcessingException e) {
                payload.set("labels", MAPPER.createArrayNode().add("h5m"));
            }
        }

        String path = "/repos/" + owner + "/" + repo + "/issues";

        HttpResponse<Buffer> response = webClient.postAbs("https://api.github.com" + path)
            .putHeader("Content-Type", "application/vnd.github+json")
            .putHeader("Authorization", "Bearer " + token)
            .putHeader("User-Agent", "h5m")
            .sendBuffer(Buffer.buffer(payload.toString()))
            .await().atMost(TIMEOUT);

        if (response.statusCode() >= 400) {
            throw new RuntimeException("GitHub API returned HTTP " + response.statusCode()
                + " for " + path + ": " + response.bodyAsString());
        }

        try {
            JsonNode responseJson = MAPPER.readTree(response.bodyAsString());
            String issueUrl = responseJson.has("html_url") ? responseJson.get("html_url").asText() : path;
            Log.infof("Created GitHub issue: %s", issueUrl);
        } catch (JsonProcessingException e) {
            Log.infof("Created GitHub issue in %s (HTTP %d)", path, response.statusCode());
        }
    }

    @Override
    public void validate(String configData) {
        if (configData == null || configData.isBlank()) {
            throw new IllegalArgumentException("GitHub issue config is required");
        }
        String owner = extractField(configData, "owner");
        String repo = extractField(configData, "repo");
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("GitHub issue config must contain an 'owner' field");
        }
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("GitHub issue config must contain a 'repo' field");
        }
    }

    private String buildTitle(ChangeNotification notification) {
        String customTitle = extractField(notification.configData(), "title");
        if (customTitle != null && !customTitle.isBlank()) {
            return applyTemplate(customTitle, notification);
        }
        return String.format("[h5m] Change detected in %s by %s",
            notification.folderName(), notification.nodeName());
    }

    private String buildBody(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return applyTemplate(notification.template(), notification);
        }
        return defaultBodyTemplate
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
            throw new IllegalArgumentException("GitHub issue secrets must contain a 'token' field");
        }
        String token = extractField(secrets, "token");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("GitHub issue secrets must contain a 'token' field");
        }
        return token;
    }
}
