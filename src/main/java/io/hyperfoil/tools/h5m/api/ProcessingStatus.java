package io.hyperfoil.tools.h5m.api;

/**
 * Processing status response for an upload operation.
 * Used by both the CLI ({@code status} command) and the REST API ({@code /api/processing}).
 *
 * @see ProcessingState
 * @see RecalculationStatus
 */
public record ProcessingStatus(long id, ProcessingState state, String error) {
}
