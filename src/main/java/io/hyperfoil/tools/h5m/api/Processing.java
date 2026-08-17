package io.hyperfoil.tools.h5m.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

/**
 * Immutable snapshot of a tracked processing operation's progress.
 * Covers both ingestion (single value, {@code total=1}) and node
 * recalculations (multiple values, {@code total=N}).
 */
@Schema(description = "Progress snapshot of a pipeline processing operation (ingestion or recalculation)")
public record Processing(
        @Schema(description = "ID of the root node (ingestion) or target node (recalculation)")
        long nodeId,
        @Schema(description = "Root value IDs being processed")
        List<Long> valueIds,
        @Schema(description = "Name of the folder being processed")
        String folderName,
        @Schema(description = "Total number of root values to process")
        int total,
        @Schema(description = "Number of root values processed so far")
        int completed,
        @Schema(description = "Current state of the operation")
        State state,
        @Schema(description = "Error message if the operation failed")
        String error,
        @Schema(description = "Elapsed time in milliseconds since the operation started")
        long durationMs
) {
    public enum State { RUNNING, COMPLETED, FAILED }
}
