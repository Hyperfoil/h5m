package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Configuration for the Webhook notification plugin
 */
@Schema(description = "Configuration for the Webhook notification plugin")
public record WebhookConfig(NotificationMethod method, @NotBlank String url) implements NotificationConfiguration {
    public WebhookConfig {
        method = NotificationMethod.WEBHOOK;
    }

    public static WebhookConfig of(String url) {
        return new WebhookConfig(null, url);
    }
}
