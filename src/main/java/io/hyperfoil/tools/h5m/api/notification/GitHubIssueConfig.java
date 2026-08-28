package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Configuration for the GitHub issue notification plugin
 */
@Schema(description = "Configuration for the GitHub issue notification plugin")
public record GitHubIssueConfig(
        NotificationMethod method,
        @NotBlank String owner,
        @NotBlank String repo,
        String title,
        List<@NotNull String> labels) implements NotificationConfiguration {
    public GitHubIssueConfig {
        method = NotificationMethod.GITHUB_ISSUE;
    }

    public static GitHubIssueConfig of(String owner, String repo, String title, List<String> labels) {
        return new GitHubIssueConfig(null, owner, repo, title, labels);
    }
}
