package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.svc.WorkServiceInterface;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.work.Work;
import io.hyperfoil.tools.h5m.queue.UploadTracker;
import io.hyperfoil.tools.h5m.queue.WorkQueue;
import io.hyperfoil.tools.h5m.queue.WorkQueueExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.quarkus.runtime.StartupEvent;
import io.hyperfoil.tools.h5m.event.ChangeDetectedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionManager;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.Hibernate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkService implements WorkServiceInterface {

    private static final int RETRY_LIMIT = 0;

    @Inject
    EntityManager em;

    @Inject
    TransactionManager tm;

    @Inject
    NodeService nodeService;

    @Inject
    ValueService valueService;

    @Inject
    MeterRegistry registry;

    @Inject
    Event<ChangeDetectedEvent> changeDetectedEvent;

    @ConfigProperty(name="quarkus.datasource.db-kind")
    String dbKind;

    @ConfigProperty(name = "h5m.worker.core", defaultValue = "1")
    int corePoolSize;

    @ConfigProperty(name = "h5m.worker.maxPoolSize", defaultValue = "50")
    int maxPoolSize;

    @ConfigProperty(name = "h5m.worker.keepalive", defaultValue = "PT60S")
    Duration keepAlive;

    private WorkQueueExecutor workExecutor;

    private final ConcurrentHashMap<Long, UploadTracker> trackers = new ConcurrentHashMap<>();

    // Recalculation tracker IDs that have been cancelled. Checked by execute()
    // to skip processing for in-flight work items belonging to a cancelled recalculation.
    private final Set<Long> cancelledRecalcIds = ConcurrentHashMap.newKeySet();

    // Tracks which folders have active recalculations. Uploads for a folder
    // are re-queued (held) while any recalculation is active for that folder,
    // ensuring the node graph is in a consistent state before processing new data.
    private final Map<Long, Set<Long>> activeRecalcsByFolder = new ConcurrentHashMap<>();

    /**
     * Finds trackers associated with the work's source values.
     * A tracker exists for each root value ID that was registered via createTracked().
     * This derives the association from sourceValues rather than duplicating state on Work.
     */
    private List<UploadTracker> findTrackers(Work work) {
        if (work.sourceValueIds == null || trackers.isEmpty()) {
            return List.of();
        }
        List<UploadTracker> found = new ArrayList<>();
        for (Long valueId : work.sourceValueIds) {
            if (valueId != null) {
                UploadTracker tracker = trackers.get(valueId);
                if (tracker != null) {
                    found.add(tracker);
                }
            }
        }
        return found;
    }

    private void incrementTrackers(Work work, int count) {
        for (UploadTracker tracker : findTrackers(work)) {
            tracker.increment(count);
        }
    }

    private void decrementTrackers(Work work) {
        for (UploadTracker tracker : findTrackers(work)) {
            tracker.decrement();
        }
    }

    private void failTrackers(Work work, Throwable t) {
        for (UploadTracker tracker : findTrackers(work)) {
            tracker.fail(t);
        }
    }

    @Transactional
    void onStart(@Observes @Priority(1) StartupEvent ev) {
        workExecutor = new WorkQueueExecutor(corePoolSize, maxPoolSize, keepAlive.toSeconds(), TimeUnit.SECONDS, new WorkQueue());
        workExecutor.allowCoreThreadTimeOut(false);
        workExecutor.prestartAllCoreThreads();
        new ExecutorServiceMetrics(workExecutor, "h5mWorkExecutor", null).bindTo(registry);
    }

    @PreDestroy
    void shutdown() {
        if (workExecutor != null) {
            workExecutor.shutdown();
        }
    }

    /**
     * Creates work items and queues them for execution.
     * Work items are NOT persisted to the DB — they exist only in memory.
     * Queue insertion is deferred until the current transaction commits
     * to ensure source values are visible to worker threads.
     */
    @Transactional
    public void create(List<Work> works) {
        WorkQueue workQueue = workExecutor.getWorkQueue();
        List<Work> newWorks = new ArrayList<>();
        for (Work work : works) {
            if (workQueue.hasWork(work)) {
                continue;
            }
            newWorks.add(work);
        }
        if (!newWorks.isEmpty()) {
            List<Work> toQueue = List.copyOf(newWorks);
            for (Work work : toQueue) {
                // Pre-compute ancestor caches while session is open — needed by
                // WorkQueue.sort() → dependsOn() which runs in afterCompletion
                // outside the session. Without this, dependsOn() would try to
                // lazily traverse NodeEntity.sources and fail.
                if (work.activeNodes != null) {
                    work.dependsOn(work);
                }
                // Increment trackers for each work item (before afterCompletion decrement)
                incrementTrackers(work, 1);
            }
            workQueue.incrementDeferred(toQueue.size());
            try {
                tm.getTransaction().registerSynchronization(new Synchronization() {
                    @Override
                    public void beforeCompletion() {}

                    @Override
                    public void afterCompletion(int status) {
                        if (status == Status.STATUS_COMMITTED) {
                            Log.debugf("afterCompletion: queueing %d Work items", toQueue.size());
                            Collection<Work> accepted = workQueue.addWorks(toQueue);
                            // Decrement trackers for rejected duplicates — they were
                            // counted in the increment but will never be executed
                            for (Work work : toQueue) {
                                if (!accepted.contains(work)) {
                                    decrementTrackers(work);
                                }
                            }
                        } else {
                            Log.warnf("Transaction rolled back (status=%d), %d Work items not queued",
                                    status, toQueue.size());
                            // Decrement trackers for rolled-back work
                            for (Work work : toQueue) {
                                decrementTrackers(work);
                            }
                        }
                        workQueue.decrementDeferred(toQueue.size());
                    }
                });
            } catch (Exception e) {
                workQueue.decrementDeferred(toQueue.size());
                // Undo tracker increments
                for (Work work : toQueue) {
                    decrementTrackers(work);
                }
                throw new IllegalStateException(
                        "Failed to register transaction synchronization; refusing to queue before commit", e);
            }
        }
    }

    /**
     * Creates work items with upload completion tracking.
     * Trackers are keyed by root value IDs. The association between Work
     * and trackers is derived from Work.sourceValues — no duplicated state.
     * For cross-upload Work, a single Work can have source values from
     * multiple uploads, and all relevant trackers are updated automatically.
     * Returns a CompletableFuture that completes when all trackers complete.
     */
    public CompletableFuture<Void> createTracked(List<Work> works, Set<Long> rootValueIds) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (long rootValueId : rootValueIds) {
            UploadTracker tracker = trackers.computeIfAbsent(rootValueId, UploadTracker::new);
            // Clean up tracker when its future completes
            tracker.getFuture().whenComplete((v, t) -> trackers.remove(rootValueId));
            futures.add(tracker.getFuture());
        }
        create(works);
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Returns the tracker for the given root value ID, if one exists.
     */
    public Optional<UploadTracker> getTracker(long rootValueId) {
        return Optional.ofNullable(trackers.get(rootValueId));
    }

    @Override
    public boolean isIdle() {
        return workExecutor.getWorkQueue().isIdle();
    }

    @Override
    public boolean terminate(long timeout, TimeUnit unit) throws InterruptedException {
        workExecutor.shutdown();
        return workExecutor.awaitTermination(timeout, unit);
    }

    /**
     * Cancels a specific recalculation by its tracker ID.
     * Removes matching Work items from the queue and fails their trackers.
     * In-flight items will check cancelledRecalcIds and skip cascade creation.
     */
    public void cancelRecalculation(long recalcTrackerId) {
        cancelledRecalcIds.add(recalcTrackerId);
        WorkQueue workQueue = workExecutor.getWorkQueue();
        List<Work> removed = workQueue.removeByRecalcId(recalcTrackerId);
        for (Work w : removed) {
            decrementTrackers(w);
        }
        // Force-fail any remaining UploadTrackers for this recalculation's values
        // so their CompletableFutures complete and the RecalculationTracker
        // can transition to CANCELLED state
        for (Work w : removed) {
            failTrackers(w, new java.util.concurrent.CancellationException("Recalculation cancelled"));
        }
        workQueue.decrementDeferred(removed.size());
    }

    /**
     * Clears the cancellation flag for a recalculation tracker ID,
     * allowing new work with a different tracker ID to proceed.
     */
    public void clearCancellation(long recalcTrackerId) {
        cancelledRecalcIds.remove(recalcTrackerId);
    }

    /**
     * Registers a recalculation as active for the given folder.
     * While active, uploads for this folder are held (re-queued) to ensure
     * data consistency — the node graph must reach a consistent state before
     * processing new uploads.
     */
    public void registerRecalculation(long folderId, long recalcTrackerId) {
        activeRecalcsByFolder.computeIfAbsent(folderId, k -> ConcurrentHashMap.newKeySet())
                .add(recalcTrackerId);
    }

    /**
     * Marks a recalculation as completed for the given folder.
     * If no more recalculations are active, uploads for this folder will resume.
     */
    public void completeRecalculation(long folderId, long recalcTrackerId) {
        Set<Long> active = activeRecalcsByFolder.get(folderId);
        if (active != null) {
            active.remove(recalcTrackerId);
            if (active.isEmpty()) {
                activeRecalcsByFolder.remove(folderId);
            }
        }
    }

    /**
     * Returns true if the given folder has any active recalculations.
     * Used by execute() to gate uploads.
     */
    public boolean isFolderRecalculating(long folderId) {
        Set<Long> active = activeRecalcsByFolder.get(folderId);
        return active != null && !active.isEmpty();
    }

    @Transactional
    public void execute(Work w){
        WorkQueue workQueue = workExecutor.getWorkQueue();
        boolean decrementDeferred = false;
        try {
            // Load source values by ID from the database (or 2LC cache).
            // Work only carries value IDs — full entities are loaded here in
            // the transaction that needs them.
            List<ValueEntity> sourceValues = new ArrayList<>();
            for (Long valueId : w.sourceValueIds) {
                ValueEntity managed = em.find(ValueEntity.class, valueId);
                if (managed != null) {
                    Hibernate.initialize(managed.data);
                    sourceValues.add(managed);
                }
            }

            // Reload active nodes in this transaction's persistence context —
            // calculateValues() accesses node.sources which is lazy
            Set<NodeEntity> activeNodes = new HashSet<>();
            for (NodeEntity an : w.activeNodes) {
                NodeEntity managed = em.find(NodeEntity.class, an.id);
                if (managed != null) {
                    activeNodes.add(managed);
                }
            }
            if(activeNodes.isEmpty() || sourceValues.isEmpty()){
                // Nothing to process — still need to decrement trackers
                decrementTrackers(w);
                return;
            }

            // Check if this recalculation was cancelled
            if (w.recalcTrackerId != null && cancelledRecalcIds.contains(w.recalcTrackerId)) {
                decrementTrackers(w);
                return;
            }
            // Gate uploads while a recalculation is active for this folder —
            // the node graph must be consistent before processing new data
            if (w.recalcTrackerId == null && w.folderId >= 0 && isFolderRecalculating(w.folderId)) {
                workQueue.add(w);
                return;
            }

            //looping over values works for Jq / Js nodes but what about cross test comparison
            //calculateValue should probably accept all sourceValues and leave it to the node function to decide
            List<ValueEntity> calculated = new ArrayList<>();
            for(NodeEntity node : activeNodes){
                List<ValueEntity> thisIteration = nodeService.calculateValues(node, sourceValues);
                calculated.addAll(thisIteration);
            }
            if (calculated.isEmpty()) {
                // Node produced no values (e.g., JQ expression didn't match the data).
                // Skip the dedup loop and cascade — no DB queries needed.
                return;
            }
            List<ValueEntity> newOrUpdated = new ArrayList<>();
            for(ValueEntity v : sourceValues) {
                for(NodeEntity activeNode : activeNodes){
                    Map<String, ValueEntity> descendants = valueService.getDescendantValueByPath(v, activeNode);
                    for(Iterator<ValueEntity> iter = calculated.iterator(); iter.hasNext();){
                        ValueEntity newValue = iter.next();
                        String path = newValue.getPath();
                        if(descendants.containsKey(path)){
                            ValueEntity existingValue = descendants.get(path);
                            if(existingValue.getId().equals(newValue.getId())) {
                                //if it's the same value we don't have to work with it
                            }else if( newValue.data.equals(existingValue.data)){
                                if(newValue.id != null){
                                    valueService.delete(newValue);
                                }
                                iter.remove();
                            }else{
                                //update the existing value's data via native SQL
                                //(@Immutable entities can't be updated through Hibernate)
                                String updateSql = switch (dbKind) {
                                    case "postgresql" -> "UPDATE value SET data = cast(:data as jsonb) WHERE id = :id";
                                    case "sqlite"     -> "UPDATE value SET data = json(:data) WHERE id = :id";
                                    default           -> "UPDATE value SET data = :data WHERE id = :id";
                                };
                                em.createNativeQuery(updateSql)
                                    .setParameter("data", newValue.data.toJsonString())
                                    .setParameter("id", existingValue.getId())
                                    .executeUpdate();
                                // Evict from 2LC since cached value is now stale
                                em.getEntityManagerFactory().getCache().evict(ValueEntity.class, existingValue.getId());
                                newOrUpdated.add(existingValue);
                            }
                            descendants.remove(path);//remove it so we know what is left over
                        }else{
                            //we need to persist the newValue
                            valueService.create(newValue);
                        }
                    }
                    if(!descendants.isEmpty()){//values that need to be deleted
                        descendants.values().forEach(valueService::delete);
                    }
                }
            }
            newOrUpdated.addAll(calculated);
            // Check cancellation again before cascade — don't spawn new work for
            // a cancelled recalculation even if the current item completed
            if (w.recalcTrackerId != null && cancelledRecalcIds.contains(w.recalcTrackerId)) {
                return;
            }
            if(!newOrUpdated.isEmpty()){
                Set<NodeEntity> createdValues = newOrUpdated.stream().map(v->v.node).collect(Collectors.toSet());
                for(NodeEntity node : createdValues){
                    if(node.isDetection()){
                        List<Long> valueIds = newOrUpdated.stream().map(ValueEntity::getId).toList();
                        long folderId = sourceValues.stream()
                                .filter(v -> v.folder != null)
                                .map(v -> v.folder.id)
                                .findFirst()
                                .orElse(-1L);
                        changeDetectedEvent.fire(new ChangeDetectedEvent(folderId, node.getId(), node.name, valueIds, w.dispatch));
                    }
                    // Cascade work inherits source value IDs and dispatch flag, so
                    // tracker association is derived automatically via findTrackers()
                    List<Long> sourceValueIds = sourceValues.stream().map(ValueEntity::getId).toList();
                    List<Work> cascadeWork = nodeService.getDependentNodes(node).stream()
                            .map(n -> {
                                Work cascaded = new Work(n, n.sources, sourceValueIds);
                                cascaded.dispatch = w.dispatch;
                                cascaded.folderId = w.folderId;
                                cascaded.recalcTrackerId = w.recalcTrackerId;
                                return cascaded;
                            })
                            .toList();
                    create(cascadeWork);
                }
            }

            // Release entities from the persistence context to prevent memory
            // accumulation during bulk imports.  All new/updated values have
            // already been flushed to the DB, cascade Work items carry entity
            // IDs and will reload via em.find() in their own transactions, and
            // the change-detected events have already been fired.
            em.flush();
            em.clear();

            // Defer decrement until after this transaction commits so that
            // isIdle() cannot return true while the DB commit is still in flight.
            if(w.activeNodes != null && !w.activeNodes.isEmpty()){
                decrementDeferred = true;
                tm.getTransaction().registerSynchronization(new Synchronization() {
                    @Override public void beforeCompletion() {}
                    @Override public void afterCompletion(int status) {
                        workQueue.decrement(w);
                        decrementTrackers(w);
                    }
                });
            }
        }catch( Exception e){
            Log.errorf(e, "WorkRunner caught: %s\n work=%s", e.getMessage(), w);
            w.retryCount++;
            if(w.retryCount > RETRY_LIMIT){
                Log.error("Work exceeded retry limit");
                // Fail trackers so CompletableFutures complete exceptionally
                failTrackers(w, e);
            } else {
                Log.info("Adding work to retry in queue");
                workQueue.add(w);
                // Skip decrement in finally — work is re-queued and will be
                // decremented when the retry completes
                decrementDeferred = true;
            }
        } finally {
            if(!decrementDeferred && w.activeNodes != null && !w.activeNodes.isEmpty()){
                workQueue.decrement(w);
                decrementTrackers(w);
            }
        }
    }

}
