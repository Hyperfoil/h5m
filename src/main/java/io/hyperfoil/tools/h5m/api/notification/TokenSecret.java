package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Token-based secret configuration, used by the GitHub issue and Slack plugins.
 * <p>
 * The {@code method} echoes the channel's method ({@code SLACK} or {@code GITHUB_ISSUE}).
 * Secrets are write-only: they are accepted on create/update but are never
 * serialized back from the server in read responses.
 */
@Schema(description = "Token-based secret configuration (never returned from the server)")
public record TokenSecret(@NotNull NotificationMethod method, @NotBlank String token) implements NotificationSecret {

    public static TokenSecret slack(String token) {
        return new TokenSecret(NotificationMethod.SLACK, token);
    }

    public static TokenSecret github(String token) {
        return new TokenSecret(NotificationMethod.GITHUB_ISSUE, token);
    }
}
