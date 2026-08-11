package io.hyperfoil.tools.h5m.api;

/**
 * Processing status for an upload operation.
 * Used by both the CLI ({@code status} command) and the REST API ({@code /api/processing}).
 */
public enum ProcessingState {
    PROCESSING,
    COMPLETED,
    FAILED,
    NOT_FOUND
}
