package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REST response DTO for notification configs.
 * Excludes the secrets field to prevent sensitive data from appearing in API responses.
 */
@Schema(description = "Notification configuration for a folder (secrets excluded)")
public record NotificationConfigResponse(
        @Schema(description = "Unique config ID") Long id,
        @Schema(description = "Folder ID") Long folderId,
        @Schema(description = "Notification method") NotificationMethod method,
        @Schema(description = "Plugin-specific configuration data (JSON)") String data,
        @Schema(description = "User-defined message template") String template,
        @Schema(description = "Whether this config is enabled") boolean enabled) {
}
