package io.hyperfoil.tools.h5m.api.svc;

import java.util.concurrent.TimeUnit;

import io.hyperfoil.tools.h5m.api.ProcessingState;

/**
 * Service interface for managing Work operations.
 */
public interface WorkServiceInterface {

    boolean isIdle();

    /**
     * Terminates the current work process within a specified timeout.
     *
     * @param timeout  The maximum time to wait.
     * @param timeUnit The time unit of the timeout argument.
     * @return true if the termination was successful before the timeout elapsed, false otherwise.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    boolean terminate(long timeout, TimeUnit timeUnit) throws InterruptedException;

    /**
     * Returns the processing status for an upload by its root value ID.
     * Checks in-memory trackers first, then falls back to a DB query.
     */
    ProcessingState getProcessingStatus(long rootValueId);

}
