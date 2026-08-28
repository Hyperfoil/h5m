package io.hyperfoil.tools.h5m.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * A sent notification (a notification log).
 * <p>
 * The api counterpart of the notification entity.
 */
@Schema(description = "A sent notification (log entry)")
public record Notification(
        @Schema(description = "Log entry ID") Long id,
        @Schema(description = "Folder ID") Long folderId,
        @Schema(description = "Folder name") String folderName,
        @Schema(description = "Notification method used") NotificationMethod method,
        @Schema(description = "ID of the channel this notification was sent through; null if the channel was deleted") Long channelId,
        @Schema(description = "Name of the channel this notification was sent through; null if the channel was deleted") String channelName,
        @Schema(description = "Delivery status") Status status,
        @Schema(description = "Error message on failure") String errorMessage,
        @Schema(description = "Detection node ID") long nodeId,
        @Schema(description = "Detection node name") String nodeName,
        @Schema(description = "Number of changes") int changeCount,
        @Schema(description = "When the notification was sent") LocalDateTime sentAt) {

    public enum Status {
        SENT,
        FAILED,
        SUPPRESSED
    }
}
