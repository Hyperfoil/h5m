package io.hyperfoil.tools.h5m.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * REST response DTO for notification log entries.
 * Replaces direct entity serialization to avoid lazy proxy issues
 * and to exclude the folder entity relationship.
 */
@Schema(description = "Notification log entry")
public record NotificationLogResponse(
        @Schema(description = "Log entry ID") Long id,
        @Schema(description = "Folder ID") Long folderId,
        @Schema(description = "Notification method used") String method,
        @Schema(description = "Resolved target (URL, email, channel)") String destination,
        @Schema(description = "Delivery status: sent, failed, suppressed") String status,
        @Schema(description = "Error message on failure") String errorMessage,
        @Schema(description = "Detection node ID") long nodeId,
        @Schema(description = "Detection node name") String nodeName,
        @Schema(description = "Number of changes") int changeCount,
        @Schema(description = "When the notification was sent") LocalDateTime sentAt) {
}
