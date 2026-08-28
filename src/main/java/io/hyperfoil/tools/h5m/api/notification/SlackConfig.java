package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Configuration for the Slack notification plugin
 */
@Schema(description = "Configuration for the Slack notification plugin")
public record SlackConfig(NotificationMethod method, @NotBlank String channel) implements NotificationConfiguration {
    public SlackConfig {
        method = NotificationMethod.SLACK;
    }

    public static SlackConfig of(String channel) {
        return new SlackConfig(null, channel);
    }
}
