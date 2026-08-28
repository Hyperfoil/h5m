package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Authorization-header secret configuration, used by the Webhook plugin.
 * <p>
 * Secrets are write-only: they are accepted on create/update but are never
 * serialized back from the server in read responses.
 */
@Schema(description = "Authorization-header secret configuration (never returned from the server)")
public record AuthHeaderSecret(NotificationMethod method, @NotBlank String authHeader) implements NotificationSecret {

    public static AuthHeaderSecret webHook(String authHeader) {
        return new AuthHeaderSecret(NotificationMethod.WEBHOOK, authHeader);
    }
}
