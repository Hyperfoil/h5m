package io.hyperfoil.tools.h5m.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * Summary of a folder's status for the dashboard view.
 */
@Schema(description = "Dashboard summary of a folder's status")
public record FolderSummary(
    @Schema(description = "Folder ID") long id,
    @Schema(description = "Folder name") String name,
    @Schema(description = "Number of uploads") int uploadCount,
    @Schema(description = "Number of configured nodes") int nodeCount,
    @Schema(description = "Number of detected changes") int changeCount,
    @Schema(description = "Timestamp of last upload") LocalDateTime lastUpload,
    @Schema(description = "Timestamp of last detected change") LocalDateTime lastChange
) {}
