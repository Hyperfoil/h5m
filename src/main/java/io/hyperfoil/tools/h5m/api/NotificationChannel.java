package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A notification channel configured on a folder.
 * <p>
 * Used for both requests and responses. On the way out {@code secret} is
 * always {@code null}: the backend deliberately does not map the
 * entity's secret data, so the secret is never returned from the server.
 */
@Schema(description = "Notification channel for a folder (the secret is write-only and never returned)")
public record NotificationChannel(
        @Schema(description = "Unique channel ID") Long id,
        @Schema(description = "Channel name (optional; auto-generated if absent)")
        @Pattern(regexp = ReservedNamespace.ALLOWED_NAME_PATTERN, message = "names starting with 'h5m.' are reserved for internal use") String name,
        @Schema(description = "Folder ID") Long folderId,
        @Schema(description = "Folder name (read-only; populated on responses)") String folderName,
        @Schema(description = "Notification method (read-only; derived from config.method, populated on responses)") NotificationMethod method,
        @Schema(description = "Plugin-specific configuration") @NotNull @Valid NotificationConfiguration config,
        @Schema(description = "Plugin-specific secret; write-only, never returned from the server") @Valid NotificationSecret secret,
        @Schema(description = "User-defined message template") String template,
        @Schema(description = "Whether this channel is enabled; null on update means leave unchanged") Boolean enabled) {
}
