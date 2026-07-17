package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.UploadStatus;
import io.hyperfoil.tools.h5m.event.ChangeDetectedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory tracking of upload operations. Captures upload processing state
 * and change detection results so that external clients can poll for completion
 * and check whether an upload triggered any regressions.
 *
 * <p>Completed entries are retained for {@link #RETENTION_MS} (default 10 minutes),
 * then lazily evicted on subsequent reads. The map is bounded to {@link #MAX_TRACKERS}
 * entries to prevent memory leaks from high upload volumes.</p>
 */
@ApplicationScoped
public class UploadService {

    private static final long RETENTION_MS = 10 * 60 * 1000; // 10 minutes
    private static final int MAX_MONITORS = 1000;

    private final ConcurrentHashMap<Long, UploadMonitor> monitors = new ConcurrentHashMap<>();

    /**
     * Registers a new upload for status tracking.
     *
     * @param uploadId the root value ID (upload ID)
     * @param future   the future that completes when all processing finishes
     */
    public void register(long uploadId, CompletableFuture<Void> future) {
        evictStale();
        if (monitors.size() >= MAX_MONITORS) {
            evictOldestCompleted();
        }
        UploadMonitor monitor = new UploadMonitor(uploadId);
        monitors.put(uploadId, monitor);
        future.whenComplete((v, t) -> {
            if (t != null) {
                monitor.fail(t.getMessage());
            } else {
                monitor.complete();
            }
        });
    }

    /**
     * Returns the current status of an upload, or null if the upload is not
     * tracked (never registered, or already evicted).
     */
    public UploadStatus get(long uploadId) {
        UploadMonitor monitor = monitors.get(uploadId);
        if (monitor == null) return null;
        // Evict if completed and past retention
        if (monitor.state != UploadStatus.State.PROCESSING &&
                monitor.completedAt > 0 &&
                System.currentTimeMillis() - monitor.completedAt > RETENTION_MS) {
            monitors.remove(uploadId);
            return null;
        }
        return monitor.toStatus();
    }

    /**
     * Observes change detection events and records them on the corresponding
     * upload tracker. Only captures upload-triggered changes (dispatch=true).
     */
    void onChangeDetected(@Observes ChangeDetectedEvent event) {
        if (!event.dispatch()) return;
        if (event.rootValueId() < 0) return;
        UploadMonitor monitor = monitors.get(event.rootValueId());
        if (monitor == null) return;
        for (Long valueId : event.valueIds()) {
            monitor.changes.add(new Change(
                    valueId,
                    event.nodeId(),
                    event.nodeName(),
                    event.nodeType().toChangeType(),
                    null // data loaded lazily if needed via REST endpoint
            ));
        }
    }

    private void evictStale() {
        long now = System.currentTimeMillis();
        monitors.entrySet().removeIf(e -> {
            UploadMonitor m = e.getValue();
            return m.state != UploadStatus.State.PROCESSING &&
                    m.completedAt > 0 &&
                    now - m.completedAt > RETENTION_MS;
        });
    }

    private void evictOldestCompleted() {
        monitors.entrySet().stream()
                .filter(e -> e.getValue().state != UploadStatus.State.PROCESSING)
                .min((a, b) -> Long.compare(a.getValue().completedAt, b.getValue().completedAt))
                .ifPresent(e -> monitors.remove(e.getKey()));
    }

    /**
     * Monitors the lifecycle of a single upload — tracks processing state,
     * timing, and accumulated change detection results.
     */
    private static class UploadMonitor {
        final long uploadId;
        final long startedAt;
        volatile UploadStatus.State state = UploadStatus.State.PROCESSING;
        volatile String error;
        volatile long completedAt;
        final CopyOnWriteArrayList<Change> changes = new CopyOnWriteArrayList<>();

        UploadMonitor(long uploadId) {
            this.uploadId = uploadId;
            this.startedAt = System.currentTimeMillis();
        }

        void complete() {
            state = UploadStatus.State.COMPLETED;
            completedAt = System.currentTimeMillis();
        }

        void fail(String error) {
            state = UploadStatus.State.FAILED;
            this.error = error;
            completedAt = System.currentTimeMillis();
        }

        UploadStatus toStatus() {
            return new UploadStatus(
                    uploadId, state, error,
                    System.currentTimeMillis() - startedAt,
                    List.copyOf(changes)
            );
        }
    }
}
