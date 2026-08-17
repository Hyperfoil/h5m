package io.hyperfoil.tools.h5m.api.svc;

import io.hyperfoil.tools.h5m.api.Processing;

import java.util.concurrent.TimeUnit;

/**
 * Manages the lifecycle of pipeline processing operations: ingestion of new
 * root values and selective node recalculation.
 *
 * <p>Each operation is tracked as a {@link Processing} snapshot that reports
 * progress, state, and timing. Callers can poll for status or block until
 * the operation completes.</p>
 */
public interface ProcessingServiceInterface {

    /**
     * Recalculates a specific node and its dependents across all root values.
     *
     * @param nodeId the node to recalculate
     * @return a processing snapshot with initial progress
     */
    Processing recalculateNode(long nodeId);

    /**
     * Returns the current status of a node recalculation, or {@code null}
     * if no recalculation is tracked for the given node.
     */
    Processing getRecalculationStatus(long nodeId);

    /**
     * Blocks until a node recalculation completes or the timeout expires.
     *
     * @return {@code true} if completed (or no recalculation was in progress),
     *         {@code false} if the timeout expired
     */
    boolean awaitRecalculation(long nodeId, long timeout, TimeUnit unit);

    /**
     * Returns the current status of a root value ingestion, or {@code null}
     * if the given ID is not a known root value.
     */
    Processing getIngestionStatus(long rootValueId);

    /**
     * Blocks until ingestion of a root value completes or the timeout expires.
     *
     * @return {@code true} if completed (or no ingestion was in progress),
     *         {@code false} if the timeout expired
     */
    boolean awaitIngestion(long rootValueId, long timeout, TimeUnit unit);
}
