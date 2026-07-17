package io.hyperfoil.tools.h5m.api;

import java.util.List;

/**
 * Immutable snapshot of an upload operation's progress and results.
 * Used as a REST response DTO for polling upload completion and
 * checking whether the upload triggered any change detections.
 */
public record UploadStatus(
        long uploadId,
        State state,
        String error,
        long durationMs,
        List<Change> changes
) {
    public enum State { PROCESSING, COMPLETED, FAILED }
}
