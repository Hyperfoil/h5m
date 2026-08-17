package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.EphemeralMode;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.Processing;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;

import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ProcessingEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.work.Work;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles pipeline processing lifecycle: recalculation, selective node
 * recalculation, crash recovery, and in-memory tracker management.
 *
 * <p>Manages mutable {@link ActivityTracker} state and produces immutable
 * {@link Processing} snapshots on demand. Trackers are indexed by root
 * value ID for work-item accounting and by node ID for recalculation
 * status queries.</p>
 *
 * <p>Ingestion starts in {@link ValueService#createRootValue} which delegates
 * tracker creation and lifecycle to this service. Recalculation is exposed
 * directly via {@link io.hyperfoil.tools.h5m.rest.NodeResource}.</p>
 */
@ApplicationScoped
public class ProcessingService implements ProcessingServiceInterface {

    private static final String FOLDER_FETCH = "SELECT f FROM folder f JOIN FETCH f.group g LEFT JOIN FETCH g.sources LEFT JOIN FETCH g.root";

    private static final long RETENTION_MS = 10 * 60 * 1000;

    /**
     * Per-root-value trackers for work-item accounting. Each root value ID maps
     * to a tracker whose pendingCount is incremented/decremented as work items
     * are created and completed. For recalculations, sub-trackers are created
     * per root value and their completion drives the main tracker's progress.
     */
    private final ConcurrentHashMap<Long, ActivityTracker> byRootValueId = new ConcurrentHashMap<>();

    /**
     * Per-node trackers for recalculation status queries.
     * Keyed by the target node ID being recalculated.
     */
    private final ConcurrentHashMap<Long, ActivityTracker> byNodeId = new ConcurrentHashMap<>();

    @Inject
    EntityManager em;
    @Inject
    ValueService valueService;
    @Inject
    WorkService workService;
    @Inject
    NodeService nodeService;

    // --- Tracker lifecycle ---

    /**
     * Creates a tracker for ingestion of a root value through the pipeline.
     * The tracker is indexed by root value ID for both work-item accounting
     * and status queries.
     *
     * @return the activity tracker (callers can get the future from it)
     */
    ActivityTracker createForIngestion(long nodeId, long rootValueId, String folderName) {
        ActivityTracker tracker = byRootValueId.computeIfAbsent(rootValueId, _ -> new ActivityTracker(nodeId, List.of(rootValueId), folderName, 1));
        tracker.afterCleanup = tracker.future.whenComplete((_, t) -> {
            byRootValueId.remove(rootValueId);
            workService.runInNewTransaction(() -> completeIngestion(rootValueId, t));
        });
        return tracker;
    }

    private void completeIngestion(long rootValueId, Throwable error) {
        ProcessingEntity entity = ProcessingEntity.find("valueId = ?1 and completed = false", rootValueId).firstResult();
        if (entity != null) {
            if (error != null) {
                Log.errorf(error, "Ingestion failed for root value %d", rootValueId);
            } else {
                entity.completed = true;
            }
        }
        int nullified = valueService.nullifyEphemeralData(rootValueId);
        if (nullified > 0) {
            Log.debugf("Nullified data for %d ephemeral values (root value %d)", nullified, rootValueId);
            em.getEntityManagerFactory().getCache().evict(ValueEntity.class);
        }
    }

    /**
     * Creates a tracker for a node recalculation (multiple root values).
     * Internally creates per-root-value sub-trackers for work-item accounting,
     * and a main tracker (indexed by node ID) that aggregates progress.
     *
     * @return the main tracker (callers can get the future and status from it)
     */
    ActivityTracker createForRecalculation(long nodeId, Set<Long> rootValueIds, String folderName) {
        List<CompletableFuture<Void>> subFutures = new ArrayList<>(rootValueIds.size());
        for (long rootValueId : rootValueIds) {
            ActivityTracker sub = byRootValueId.computeIfAbsent(rootValueId, _ -> new ActivityTracker(nodeId, List.of(rootValueId), folderName, 1));
            sub.future.whenComplete((_, _) -> byRootValueId.remove(rootValueId));
            subFutures.add(sub.future);
        }

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(subFutures.toArray(CompletableFuture[]::new));
        ActivityTracker main = new ActivityTracker(nodeId, List.copyOf(rootValueIds), folderName, rootValueIds.size(), combinedFuture);

        for (CompletableFuture<Void> subFuture : subFutures) {
            subFuture.whenComplete((_, _) -> main.incrementCompleted());
        }

        byNodeId.put(nodeId, main);
        return main;
    }

    // --- Work-item accounting (called by WorkService) ---

    List<ActivityTracker> findTrackers(Work work) {
        if (work.getSourceValueIds() == null || byRootValueId.isEmpty()) {
            return List.of();
        }
        List<ActivityTracker> found = new ArrayList<>();
        for (Long valueId : work.getSourceValueIds()) {
            if (valueId != null) {
                ActivityTracker tracker = byRootValueId.get(valueId);
                if (tracker != null && !found.contains(tracker)) {
                    found.add(tracker);
                }
            }
        }
        return found;
    }

    void incrementTrackers(Work work) {
        for (ActivityTracker tracker : findTrackers(work)) {
            tracker.increment();
        }
    }

    void decrementTrackers(Work work) {
        for (ActivityTracker tracker : findTrackers(work)) {
            tracker.decrement();
        }
    }

    void failTrackers(Work work, Throwable t) {
        for (ActivityTracker tracker : findTrackers(work)) {
            tracker.fail(t);
        }
    }

    // --- Status queries ---

    @Override
    @Transactional
    public Processing getIngestionStatus(long rootValueId) {
        ActivityTracker tracker = byRootValueId.get(rootValueId);
        if (tracker != null) {
            return tracker.toStatus();
        }
        ValueEntity rootValue = ValueEntity.findById(rootValueId);
        if (rootValue != null && rootValue.node != null && rootValue.node.type() == NodeType.ROOT) {
            return new Processing(rootValue.node.id, List.of(rootValueId), null, 1, 1, Processing.State.COMPLETED, null, 0);
        }
        return null;
    }

    @Override
    public Processing getRecalculationStatus(long nodeId) {
        ActivityTracker tracker = getByNodeId(nodeId);
        return tracker != null ? tracker.toStatus() : null;
    }

    @Override
    public boolean awaitIngestion(long rootValueId, long timeout, TimeUnit unit) {
        ActivityTracker tracker = getByRootValueId(rootValueId);
        if (tracker == null) {
            return true;
        }
        try {
            tracker.getFuture().get(timeout, unit);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public boolean awaitRecalculation(long nodeId, long timeout, TimeUnit unit) {
        ActivityTracker tracker = getByNodeId(nodeId);
        if (tracker == null) {
            return true;
        }
        try {
            tracker.getFuture().get(timeout, unit);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    ActivityTracker getByNodeId(long nodeId) {
        ActivityTracker tracker = byNodeId.get(nodeId);
        if (tracker != null && tracker.state != Processing.State.RUNNING) {
            if (tracker.completedAt > 0 && System.currentTimeMillis() - tracker.completedAt > RETENTION_MS) {
                byNodeId.remove(nodeId);
                return null;
            }
        }
        return tracker;
    }

    ActivityTracker getByRootValueId(long rootValueId) {
        return byRootValueId.get(rootValueId);
    }

    // --- Recovery ---

    /**
     * On startup, re-trigger processing for any ingestions or recalculations that
     * were interrupted (e.g., by a crash). Uses all source nodes (not just
     * top-level) so that mid-cascade crashes are recovered correctly — the
     * deduplication logic in execute() skips already-computed values while
     * ensuring missing children are still calculated.
     * <p>
     * Recovery is split into two phases to avoid SQLITE_BUSY_SNAPSHOT errors.
     * <ul>
     *   <li>Phase 1 (inside the transaction): reads incomplete trackers, updates
     *       their state (marking old ones completed, persisting new recovery
     *       trackers), and collects the actual recovery work
     *       (recalculate/createTracked calls) as deferred actions.</li>
     *   <li>Phase 2 (after the transaction commits): runs the deferred actions.
     *       These open their own transactions (via requiringNew), which would
     *       deadlock with SQLite's single-writer constraint if they ran inside
     *       the Phase 1 transaction.</li>
     * </ul>
     */
    public void recoverIncompleteProcessing(@Observes @Priority(2) StartupEvent ev) {
        //ev == null when forced to recover
        if(!ConfigUtils.getProfiles().contains("cli") || ev == null) {
            List<Runnable> deferred = new ArrayList<>();
            QuarkusTransaction.requiringNew().run(() -> {
                List<ProcessingEntity> incomplete = ProcessingEntity.find("completed", false).list();
                if (!incomplete.isEmpty()) {
                    Log.infof("Found %d incomplete processing operations to recover", incomplete.size());
                    for (ProcessingEntity tracking : incomplete) {
                        if (tracking.isIngestion()) {
                            recoverIngestion(tracking, deferred);
                        } else if (tracking.isRecalculation()) {
                            recoverRecalculateNode(tracking, deferred);
                        } else {
                            Log.warnf("Unknown processing record %d (no valueId or nodeId), skipping", tracking.id);
                        }
                    }
                }
            });
            deferred.forEach(Runnable::run);
        }
    }

    @Transactional
    public List<ProcessingEntity> getIncompleteProcessing() {
        return ProcessingEntity.find("completed", false).list();
    }

    @Transactional
    public int removeIncompleteProcessing(){
        List<ProcessingEntity> incomplete = getIncompleteProcessing();
        incomplete.forEach(ProcessingEntity::delete);
        return incomplete.size();
    }

    private void recoverIngestion(ProcessingEntity tracking, List<Runnable> deferred) {
        ValueEntity rootValue = ValueEntity.findById(tracking.valueId);
        if (rootValue == null) {
            Log.warnf("Root value %d not found for incomplete ingestion, removing tracking record", tracking.valueId);
            tracking.delete();
            return;
        }
        FolderEntity folder = findFolderById(tracking.folderId);
        if (folder == null) {
            Log.warnf("Folder %d not found for incomplete ingestion, removing tracking record", tracking.folderId);
            tracking.delete();
            return;
        }
        Log.infof("Re-triggering ingestion for root value %d in folder %d", tracking.valueId, tracking.folderId);
        // Use all source nodes (not just top-level) to handle mid-cascade crashes
        List<Work> works = List.copyOf(folder.group.sources).stream()
                .map(node -> {
                    // Pre-compute ancestor cache while session is open — the deferred
                    // runnable runs after this transaction closes, and dependsOn()
                    // would fail trying to lazily traverse sources on detached entities.
                    node.dependsOn(node);
                    Work w = new Work(node, new ArrayList<>(node.sources), List.of(rootValue.id));
                    w.setCascade(false);
                    return w;
                })
                .toList();
        if (!works.isEmpty()) {
            deferred.add(() -> {
                createForIngestion(folder.group.root.id, rootValue.id, folder.name);
                workService.create(works);
            });
        } else {
            tracking.completed = true;
        }
    }

    private void recoverRecalculateNode(ProcessingEntity tracking, List<Runnable> deferred) {
        FolderEntity folder = findFolderById(tracking.folderId);
        if (folder == null) {
            Log.warnf("Folder %d not found for incomplete node recalculation, removing tracking record", tracking.folderId);
            tracking.delete();
            return;
        }
        NodeEntity node = NodeEntity.findById(tracking.nodeId);
        if (node == null) {
            Log.warnf("Node %d not found for incomplete recalculation, removing tracking record", tracking.nodeId);
            tracking.delete();
            return;
        }
        // Recovery queues ALL source nodes (not just top-level or the specific node).
        //
        // Why not use recalculate() (top-level + cascade):
        // A mid-process crash may have left the pipeline in a partially computed
        // state — e.g., node A was recomputed but cascade to B, C didn't happen.
        // With top-level + cascade, A's dedup sees the value is already correct
        // and skips it without cascading. B and C remain stale.
        //
        // Why not use recalculateNode():
        // Same dedup issue — if the tracked node's value already matches, cascade
        // doesn't fire for its dependents.
        //
        // Solution: queue Work for EVERY node in the graph (same as ingestion recovery).
        // Each node gets its own Work item. The dedup logic skips nodes whose values
        // are already correct but processes any that need updating. This ensures the
        // entire pipeline reaches a consistent state regardless of where the crash
        // interrupted processing.
        Log.infof("Re-triggering processing for all nodes in folder %d (node %d was in progress)", tracking.folderId, tracking.nodeId);

        // Create a new tracker for this recovery work — if recovery itself crashes,
        // the new tracker ensures it's re-triggered on next startup.
        ProcessingEntity recoveryTracker = new ProcessingEntity(tracking.folderId, tracking.nodeId, null);
        recoveryTracker.persist();
        tracking.completed = true;

        List<ValueEntity> rootValues = valueService.getValues(folder.group.root);
        rootValues.forEach(ValueEntity::getPath);
        List<Work> works = new ArrayList<>();
        Set<Long> rootValueIds = new HashSet<>();
        for (ValueEntity rootValue : rootValues) {
            rootValueIds.add(rootValue.id);
            for (NodeEntity sourceNode : List.copyOf(folder.group.sources)) {
                // Pre-compute ancestor cache while session is open — the deferred
                // runnable runs after this transaction closes, and dependsOn()
                // would fail trying to lazily traverse sources on detached entities.
                sourceNode.dependsOn(sourceNode);
                Work w = new Work(sourceNode, new ArrayList<>(sourceNode.sources), List.of(rootValue.id));
                w.setDispatch(false);
                w.setCascade(false);
                works.add(w);
            }
        }
        if (!works.isEmpty()) {
            long recoveryTrackerId = recoveryTracker.id;
            // Defer work creation until after the recovery transaction commits — createTracked opens its own transaction via afterCompletion
            deferred.add(() -> {
                ActivityTracker tracker = createForRecalculation(node.id, rootValueIds, folder.name);
                workService.create(works);
                tracker.afterCleanup = tracker.getFuture().whenComplete((_, _) -> workService.runInNewTransaction(() -> {
                    ProcessingEntity entity = ProcessingEntity.findById(recoveryTrackerId);
                    if (entity != null) {
                        entity.completed = true;
                    }
                    for (ValueEntity rootValue : rootValues) {
                        valueService.nullifyEphemeralData(rootValue.id);
                    }
                    // Evict cached ValueEntity instances since data was nullified via native SQL
                    em.getEntityManagerFactory().getCache().evict(ValueEntity.class);
                }));
            });
        } else {
            recoveryTracker.completed = true;
        }
    }

    // --- Folder lookup helpers ---

    private FolderEntity findFolderById(long folderId) {
        return em.createQuery(FOLDER_FETCH + " WHERE f.id = :id", FolderEntity.class)
                .setParameter("id", folderId).getResultStream().findFirst().orElse(null);
    }

    private FolderEntity findFolderByName(String name) {
        return em.createQuery(FOLDER_FETCH + " WHERE f.name = :name", FolderEntity.class)
                .setParameter("name", name).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Folder not found: " + name));
    }

    private FolderEntity findFolderByGroupId(long groupId) {
        return em.createQuery(FOLDER_FETCH + " WHERE f.group.id = :groupId", FolderEntity.class)
                .setParameter("groupId", groupId).getResultStream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No folder found for group " + groupId));
    }

    // --- Recalculation ---

    /**
     * Selectively recalculates values for a specific node and its dependents.
     * Walks up the source chain to find ephemeral ancestor.
     * Queues Work from those ancestors along with Work for target node
     *
     * Use cases:
     * * A user adds a new Node to the NodeGroup
     * * A user changes a Node's operation or sources
     *
     * @param nodeId the node to recalculate
     * @return recalculation status with progress tracking
     * @throws IllegalStateException if ingestion is in progress for this folder
     * @throws IllegalArgumentException if the node is not found or has no group
     */
    public Processing recalculateNode(long nodeId){
        return workService.callInNewTransaction(() -> {
            NodeEntity targetNode = NodeEntity.findById(nodeId);
            if(targetNode == null){
                throw new IllegalArgumentException("Node not found: " + nodeId);
            }
            if (targetNode.group == null) {
                throw new IllegalArgumentException("Node " + nodeId + " has no group");
            }
            FolderEntity folder = findFolderByGroupId(targetNode.group.id);
            List<ValueEntity> rootValues = valueService.getValues(targetNode.group.root);
            if (rootValues.isEmpty()) {
                return new Processing(nodeId, List.of(), folder.name, 0, 0, Processing.State.COMPLETED, null, 0);
            }
            rootValues.forEach(ValueEntity::getPath);

            Set<Long> rootValueIds = new HashSet<>(rootValues.size());
            Set<NodeEntity> ephemeralSources = nodeService.getEphemeralSources(targetNode);
            List<Work> todo = new  ArrayList<>();

            for(ValueEntity rootValue : rootValues){
                rootValueIds.add(rootValue.id);
                if(!ephemeralSources.isEmpty()){
                    for(NodeEntity ephemeralSource : ephemeralSources){
                        Work ephemeralWork = new Work(ephemeralSource,new ArrayList<>(ephemeralSource.sources),List.of(rootValue.id));
                        ephemeralWork.setCascade(false);
                        ephemeralWork.setDispatch(false);
                        todo.add(ephemeralWork);
                    }
                }
                Work nodeWork = new Work(targetNode, new ArrayList<>(targetNode.sources), List.of(rootValue.id));
                nodeWork.setDispatch(false);
                todo.add(nodeWork);
            }

            if(todo.isEmpty()){
                return new Processing(nodeId, List.of(), folder.name, 0, 0, Processing.State.COMPLETED, null, 0);
            }

            // Track for crash recovery
            ProcessingEntity tracking = new ProcessingEntity(folder.id, nodeId, null);
            tracking.persist();

            ActivityTracker tracker = createForRecalculation(nodeId, rootValueIds, folder.name);
            workService.create(todo);

            // Mark completed and null out ephemeral data after recalculation finishes.
            // The cleanup future is stored in afterCleanup so that awaitRecalculation
            // can wait for both the processing AND the cleanup to finish.
            tracker.afterCleanup = tracker.future.whenComplete((_, t) -> {
                if (t != null) {
                    Log.errorf(t, "Recalculation failed for folder '%s' (nodeId=%d)", folder.name, nodeId);
                }
                // Mark tracker completed even on failure to prevent infinite retry on restart.
                // On success, also nullify ephemeral data and evict the 2LC.
                workService.runInNewTransaction(() -> {
                    ProcessingEntity entity = ProcessingEntity.findById(tracking.id);
                    if (entity != null) {
                        entity.completed = true;
                    }
                    if (t == null) {
                        for (ValueEntity rootValue : rootValues) {
                            int nullified = valueService.nullifyEphemeralData(rootValue.id);
                            if (nullified > 0) {
                                Log.debugf("Nullified data for %d ephemeral values (root %d)", nullified, rootValue.id);
                            }
                        }
                        em.getEntityManagerFactory().getCache().evict(ValueEntity.class);
                    }
                });
            });
            return tracker.toStatus();
        });
    }

    // --- Ephemeral chain walking ---
    /**
     * Walks up the source chain from the target node to find the nodes where
     * recomputation should start. Ephemeral nodes (DISCARD, or AUTO with
     * non-detection children) will have their data nullified, so the pipeline
     * must be restarted from an ancestor whose data is available.
     *
     * <p>The algorithm:</p>
     * <ul>
     *   <li>If the target node's sources all have available data (KEEP, root,
     *       or detection sources), return just the target node.</li>
     *   <li>If any source is ephemeral (data nullified), recursively walk up
     *       to that source's sources.</li>
     *   <li>Stop at root nodes (always have data) or KEEP nodes.</li>
     *   <li>Return the set of nodes that should be queued as Work items.
     *       The cascade mechanism handles recomputing everything between the
     *       start nodes and the target.</li>
     * </ul>
     *
     * @param allGroupNodes all nodes in the folder's group (already loaded via
     *                      JOIN FETCH), used to check graph structure in-memory
     */
    Set<NodeEntity> findRecomputationStartNodes(NodeEntity targetNode, NodeEntity rootNode,
                                                  List<NodeEntity> allGroupNodes) {
        Set<NodeEntity> startNodes = new LinkedHashSet<>();
        findStartNodes(targetNode, rootNode, allGroupNodes, startNodes, new HashSet<>());
        return startNodes;
    }

    private void findStartNodes(NodeEntity node, NodeEntity rootNode,
                                 List<NodeEntity> allGroupNodes,
                                 Set<NodeEntity> startNodes, Set<Long> visited) {
        if (!visited.add(node.getId())) {
            return; // avoid cycles
        }

        boolean allSourcesHaveData = true;
        if (node.sources != null) {
            for (NodeEntity source : node.sources) {
                if (source.getId().equals(rootNode.getId())) {
                    // Root always has data — this source is fine
                    continue;
                }
                if (isEphemeral(source, allGroupNodes)) {
                    // This source's data is nullified — walk up further
                    allSourcesHaveData = false;
                    findStartNodes(source, rootNode, allGroupNodes, startNodes, visited);
                }
                // KEEP or detection sources have data — no need to walk up
            }
        }

        if (allSourcesHaveData) {
            // All sources have data available — this node can be the start point
            startNodes.add(node);
        }
        // If not all sources have data, the recursive calls above will add
        // the correct ancestors to startNodes. This node will be reached
        // via cascade from those ancestors.
    }

    /**
     * Checks if a node's value data has been ephemeral-nullified.
     * Matches the nullifyEphemeralData() SQL logic:
     * <ul>
     *   <li>KEEP — never ephemeral</li>
     *   <li>DISCARD — always ephemeral</li>
      *   <li>AUTO — ephemeral only if the node has non-analysis children
     *       and is not itself a direct source of an analysis node</li>
     * </ul>
     * Uses the already-loaded group nodes to check graph structure in-memory.
     * Must match the logic in {@link ValueService#nullifyEphemeralData(long)}.
     */
    private boolean isEphemeral(NodeEntity node, List<NodeEntity> allGroupNodes) {
        if (node.ephemeral == EphemeralMode.KEEP) return false;
        if (node.ephemeral == EphemeralMode.DISCARD) return true;
        // AUTO: check graph structure to determine if data was nullified
        boolean hasNonAnalysisChild = false;
        boolean isAnalysisSource = false;
        for (NodeEntity other : allGroupNodes) {
            if (other.sources != null && other.sources.stream()
                    .anyMatch(s -> s.getId().equals(node.getId()))) {
                if (other.type().isAnalysis()) {
                    isAnalysisSource = true;
                } else {
                    hasNonAnalysisChild = true;
                }
            }
        }
        return hasNonAnalysisChild && !isAnalysisSource;
    }

    public void deleteForFolder(long folderId) {
        em.createNativeQuery("DELETE FROM processing WHERE folder_id = :fid")
                .setParameter("fid", folderId).executeUpdate();
    }

    // --- Activity tracker (mutable internal state) ---

    static class ActivityTracker {
        private final long nodeId;
        private final List<Long> valueIds;
        private final String folderName;
        private final int total;
        private final AtomicInteger pendingCount = new AtomicInteger(0);
        private final AtomicInteger completedCount = new AtomicInteger(0);
        private final CompletableFuture<Void> future;
        private final long startedAt;
        private volatile Processing.State state = Processing.State.RUNNING;
        private volatile String error;
        private volatile long completedAt;
        volatile CompletableFuture<Void> afterCleanup;

        ActivityTracker(long nodeId, List<Long> valueIds, String folderName, int total) {
            this(nodeId, valueIds, folderName, total, new CompletableFuture<>());
        }

        ActivityTracker(long nodeId, List<Long> valueIds, String folderName, int total, CompletableFuture<Void> future) {
            this.nodeId = nodeId;
            this.valueIds = valueIds;
            this.folderName = folderName;
            this.total = total;
            this.startedAt = System.currentTimeMillis();
            this.future = future;

            future.whenComplete((_, t) -> {
                completedAt = System.currentTimeMillis();
                if (t != null) {
                    state = Processing.State.FAILED;
                    error = t.getMessage();
                } else {
                    state = Processing.State.COMPLETED;
                }
            });
        }

        public CompletableFuture<Void> getFuture() {
            return future;
        }

        public void increment() {
            pendingCount.incrementAndGet();
        }

        public void decrement() {
            int remaining = pendingCount.decrementAndGet();
            Log.debugf("Processing[node=%d]: decrement -> %d remaining", nodeId, remaining);
            if (remaining == 0) {
                future.complete(null);
            } else if (remaining < 0) {
                Log.warnf("Processing[node=%d]: over-decremented to %d", nodeId, remaining);
            }
        }

        public void fail(Throwable t) {
            Log.errorf(t, "Processing[node=%d]: work failed", nodeId);
            pendingCount.set(Integer.MIN_VALUE);
            future.completeExceptionally(t);
        }

        public void incrementCompleted() {
            completedCount.incrementAndGet();
        }

        public Processing toStatus() {
            return new Processing(nodeId, valueIds, folderName, total, completedCount.get(), state, error, System.currentTimeMillis() - startedAt);
        }

        @Override
        public String toString() {
            return "ActivityTracker[node=" + nodeId + ", pending=" + pendingCount.get() + ", completed=" + completedCount.get() + "/" + total + "]";
        }
    }
}
