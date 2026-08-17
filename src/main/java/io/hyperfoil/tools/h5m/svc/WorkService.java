package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.ProcessingState;
import io.hyperfoil.tools.h5m.api.svc.WorkServiceInterface;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.work.Work;
import io.hyperfoil.tools.h5m.queue.UploadTracker;
import io.hyperfoil.tools.h5m.queue.WorkQueue;
import io.hyperfoil.tools.h5m.queue.WorkQueueExecutor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.event.ChangeDetectedEvent;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PessimisticLockException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.Transactional;
import jakarta.transaction.TransactionManager;
import io.hyperfoil.tools.h5m.provided.DatabaseEngine;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;


import java.time.Duration;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkService implements WorkServiceInterface {

    private static final int RETRY_LIMIT = 5;
    private static final long RETRY_BASE_MS = 5;

    private static boolean isPessimisticLock(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof PessimisticLockException) return true;
        }
        return false;
    }

    private static void backoffSleep(int attempt) {
        // exponential backoff to spread out SQLite writer contention
        // in each attempt the value increases: from RETRY_BASE_MS to RETRY_BASE_MS * 2, 4, 8, 16 ... -> (5-9, 5-19, 5-39, 5-79 ... ms)
        // enough to spread the load without being too aggressive
        long jitter = ThreadLocalRandom.current().nextLong(RETRY_BASE_MS, RETRY_BASE_MS * (1L << attempt));
        Log.infof("Database busy (retry %d/%d), retrying in %dms", attempt, RETRY_LIMIT - 1, jitter);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(jitter));
    }

    /**
     * Runs an action in a new transaction, independent of the caller's
     * transactional context. On SQLite, transparently retries on
     * {@link jakarta.persistence.PessimisticLockException} (SQLITE_BUSY /
     * SQLITE_BUSY_SNAPSHOT) up to {@link #RETRY_LIMIT} times with
     * exponential backoff and jitter to handle single-writer contention.
     * <p>
     * The action is wrapped to capture the original exception before Quarkus
     * transaction management can lose it during rollback (e.g., if rollback
     * itself hits SQLITE_BUSY, the original PessimisticLockException is
     * replaced by a QuarkusTransactionException wrapping SystemException).
     */
    <T> T callInNewTransaction(Callable<T> action) {
        if (!db.isSQLite()) {
            return QuarkusTransaction.requiringNew().call(action);
        }
        for (int attempt = 1; ; attempt++) {
            AtomicReference<Throwable> actionFailure = new AtomicReference<>();
            try {
                return QuarkusTransaction.requiringNew().call(() -> {
                    try {
                        return action.call();
                    } catch (Throwable t) {
                        actionFailure.set(t);
                        throw t;
                    }
                });
            } catch (Throwable t) {
                Throwable rootCause = actionFailure.get();
                if (attempt >= RETRY_LIMIT || !isPessimisticLock(rootCause != null ? rootCause : t)) {
                    throw t;
                }
                backoffSleep(attempt);
            }
        }
    }

    void runInNewTransaction(Runnable action) {
        callInNewTransaction(() -> { action.run(); return null; });
    }

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

    @Inject
    DatabaseEngine db;

    @ConfigProperty(name = "h5m.worker.core", defaultValue = "1")
    int corePoolSize;

    @ConfigProperty(name = "h5m.worker.maxPoolSize", defaultValue = "50")
    int maxPoolSize;

    @ConfigProperty(name = "h5m.worker.keepalive", defaultValue = "PT60S")
    Duration keepAlive;

    private WorkQueueExecutor workExecutor;

    private final ConcurrentHashMap<Long, UploadTracker> trackers = new ConcurrentHashMap<>();

    /**
     * Finds trackers associated with the work's source values.
     * A tracker exists for each root value ID that was registered via createTracked().
     * This derives the association from sourceValues rather than duplicating state on Work.
     */
    private List<UploadTracker> findTrackers(Work work) {
        if (work.getSourceValueIds() == null || trackers.isEmpty()) {
            return List.of();
        }
        List<UploadTracker> found = new ArrayList<>();
        for (Long valueId : work.getSourceValueIds()) {
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

    /**
     * Eagerly initializes the NodeEntity.sources chains for all active nodes
     * in the given Work item. This forces Hibernate to load the lazy source
     * collections while the session is still open, so that
     * NodeEntity.dependsOn() can traverse them in afterCompletion (outside
     * the session) without throwing LazyInitializationException.
     */
    private void initializeSources(Work work) {
        if (work.getActiveNodes() == null) return;
        for (NodeEntity node : work.getActiveNodes()) {
            initializeSourceChain(node, new HashSet<>());
        }
    }

    private void initializeSourceChain(NodeEntity node, Set<Long> visited) {
        if (node.sources == null || node.sources.isEmpty()) return;
        // Touch the collection to force Hibernate to initialize the lazy proxy
        int size = node.sources.size();
        for (int i = 0; i < size; i++) {
            NodeEntity source = node.sources.get(i);
            if (source.id != null && visited.add(source.id)) {
                initializeSourceChain(source, visited);
            }
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
                // Pre-compute ancestor node IDs while the Hibernate session is
                // open. WorkQueue.sort() → dependsOn() runs in afterCompletion
                // (outside the session) and needs these for O(1) dependency checks.
                work.precomputeAncestors();
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

    /**
     * Returns the processing status for an upload by its root value ID.
     * Checks in-memory trackers first (active processing), then falls back
     * to a DB query to determine if the upload completed previously.
     */
    @Override
    @Transactional
    public ProcessingState getProcessingStatus(long rootValueId) {
        Optional<UploadTracker> tracker = getTracker(rootValueId);
        if (tracker.isPresent()) {
            if (tracker.get().getFuture().isDone()) {
                return tracker.get().getFuture().isCompletedExceptionally()
                        ? ProcessingState.FAILED
                        : ProcessingState.COMPLETED;
            }
            return ProcessingState.PROCESSING;
        }
        // Tracker already cleaned up — check if root value exists in DB
        ValueEntity rootValue = ValueEntity.findById(rootValueId);
        if (rootValue != null && rootValue.node != null
                && rootValue.node.type() == NodeType.ROOT) {
            return ProcessingState.COMPLETED;
        }
        return ProcessingState.NOT_FOUND;
    }

    public WorkQueue getQueue(){return workExecutor.getWorkQueue();}

    @Override
    public boolean isIdle() {
        return workExecutor.getWorkQueue().isIdle();
    }

    @Override
    public boolean terminate(long timeout, TimeUnit unit) throws InterruptedException {
        workExecutor.shutdown();
        return workExecutor.awaitTermination(timeout, unit);
    }

    @Transactional
    public void execute(Work w){
        WorkQueue workQueue = workExecutor.getWorkQueue();
        boolean decrementDeferred = false;
        try {
            // Batch-load source values with sources eagerly fetched in a single
            // query. The 2LC does not cache @Basic(LAZY) properties for entities
            // with associations (HHH-20773), so em.find() cache hits still
            // trigger a DB round-trip for the lazy data field. This JPQL query
            // eagerly fetches the sources collection via LEFT JOIN FETCH.
            // Note: Entity Graph (fetchgraph/loadgraph) was tested but causes
            // a 3x regression despite generating identical SQL — the overhead
            // is in Hibernate's entity initialization, not in query generation.
            List<ValueEntity> sourceValues;
            List<Long> sourceIds = w.getSourceValueIds();
            if (sourceIds == null || sourceIds.isEmpty()) {
                sourceValues = List.of();
            } else {
                sourceValues = em.createQuery(
                        "SELECT v FROM value v LEFT JOIN FETCH v.sources WHERE v.id IN :ids",
                        ValueEntity.class)
                    .setParameter("ids", sourceIds)
                    .getResultList();
            }

            // Reload active nodes in this transaction's persistence context —
            // calculateValues() accesses node.sources which is lazy
            Set<NodeEntity> activeNodes = new HashSet<>();
            for (NodeEntity an : w.getActiveNodes()) {
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
            List<ValueEntity> toPersist = new ArrayList<>();
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
                                em.createNativeQuery("UPDATE value SET data = :data WHERE id = :id")
                                    .setParameter("data", JqValues.serializeToBytes(newValue.data))
                                    .setParameter("id", existingValue.getId())
                                    .executeUpdate();
                                // Evict from 2LC since cached value is now stale
                                em.getEntityManagerFactory().getCache().evict(ValueEntity.class, existingValue.getId());
                                newOrUpdated.add(existingValue);
                            }
                            descendants.remove(path);//remove it so we know what is left over
                        }else{
                            toPersist.add(newValue);
                        }
                    }
                    if(!descendants.isEmpty()){//values that need to be deleted
                        descendants.values().forEach(valueService::delete);
                    }
                }
            }
            if (!toPersist.isEmpty()) {
                valueService.createAll(toPersist);
            }
            newOrUpdated.addAll(calculated);
            if(!newOrUpdated.isEmpty()){
                Set<NodeEntity> createdValues = newOrUpdated.stream().map(v->v.node).collect(Collectors.toSet());
                for(NodeEntity node : createdValues){
                    if(node.isDetection()){
                        // Build enriched Change records from the detection values
                        // already in memory — no additional DB lookups needed
                        List<Change> changes = newOrUpdated.stream()
                                .filter(v -> v.node.equals(node))
                                .map(v -> new Change(
                                        v.getId(),
                                        node.getId(),
                                        node.name,
                                        node.type(),
                                        v.data,
                                        v.data != null ? v.data.getField("fingerprint") : null
                                ))
                                .toList();
                        long folderId = sourceValues.stream()
                                .filter(v -> v.folder != null)
                                .map(v -> v.folder.id)
                                .findFirst()
                                .orElse(-1L);
                        // Derive rootValueId from sourceValueIds — for upload work,
                        // the first ID is the root value (upload ID)
                        long rootValueId = w.getSourceValueIds().isEmpty() ? -1L : w.getSourceValueIds().getFirst();
                        changeDetectedEvent.fire(new ChangeDetectedEvent(folderId,
                                changes, w.isDispatch(), rootValueId));
                    }
                    // Cascade work inherits source value IDs and dispatch flag, so
                    // tracker association is derived automatically via findTrackers()
                    if(w.isCascade()) {
                        List<Long> sourceValueIds = sourceValues.stream().map(ValueEntity::getId).toList();
                        List<Work> cascadeWork = nodeService.getDependentNodes(node).stream()
                                .map(n -> {
                                    Work cascaded = new Work(n, n.sources, sourceValueIds);
                                    cascaded.setDispatch(w.isDispatch());
                                    return cascaded;
                                })
                                .toList();

                        create(cascadeWork);
                    }
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
            if(w.getActiveNodes() != null && !w.getActiveNodes().isEmpty()){
                decrementDeferred = true;
                tm.getTransaction().registerSynchronization(new Synchronization() {
                    @Override public void beforeCompletion() {}
                    @Override public void afterCompletion(int status) {
                        workQueue.decrement(w);
                        decrementTrackers(w);
                        w.releaseReferences();
                    }
                });
            }
        }catch( Exception e){
            Log.debugf(e, "WorkRunner caught: %s\n work=%s", e.getMessage(), w);
            w.incrementRetryCount();
            if(db.isSQLite() && w.getRetryCount() < RETRY_LIMIT){
                backoffSleep(w.getRetryCount());
                workQueue.add(w);
                // Skip decrement in finally — work is re-queued and will be
                // decremented when the retry completes
                decrementDeferred = true;
            } else {
                // Fail trackers so CompletableFutures complete exceptionally
                failTrackers(w, e);
            }
        } finally {
            if(!decrementDeferred && w.getActiveNodes() != null && !w.getActiveNodes().isEmpty()){
                workQueue.decrement(w);
                decrementTrackers(w);
                w.releaseReferences();
            }
        }
    }

}
