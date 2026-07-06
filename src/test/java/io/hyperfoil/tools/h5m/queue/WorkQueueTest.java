package io.hyperfoil.tools.h5m.queue;

import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.FreshDb;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.entity.work.Work;
import io.hyperfoil.tools.h5m.entity.node.JqNode;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;
import io.hyperfoil.tools.h5m.entity.node.RootNode;
import io.hyperfoil.tools.h5m.svc.WorkService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class WorkQueueTest extends FreshDb {

    @Inject
    TransactionManager tm;

    @Inject
    WorkService workService;


    @Test
    public void reject_relativedifference_as_duplicate() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        tm.begin();
        NodeEntity rootNode = new JqNode("root",".root");
        rootNode.persist();
        NodeEntity relativeDifference = new RelativeDifference();
        relativeDifference.persist();

        ValueEntity rootValue1 = new ValueEntity(null,rootNode,JqValues.parse("\"text1\""));
        ValueEntity rootValue2 = new ValueEntity(null,rootNode,JqValues.parse("\"text2\""));

        rootValue1.persist();
        rootValue2.persist();

        Work work1 = new Work(relativeDifference,List.of(rootNode),List.of(rootValue1.id));
        Work work2 = new Work(relativeDifference,List.of(rootNode),List.of(rootValue2.id));

        assertEquals(work1.hashCode(),work2.hashCode(),"both worth should have the same hashcode despite different values");

        WorkQueue q = new WorkQueue();

        q.addWorks(List.of(work1));
        assertEquals(1,q.size(),"first work should be added");
        q.addWorks(List.of(work2));
        assertTrue(q.isPending(work2),"work 2 should be pending since it matches work1");
        assertEquals(1,q.size(),"seconds work should not be added because it should have the same hash");
        tm.commit();
    }

    @Test
    public void reject_duplicates() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();
        tm.begin();
        RootNode root = new RootNode();
        root.persist();
        NodeEntity aNode = new JqNode("a",".a",root);
        aNode.persist();
        ValueEntity rootValue = new ValueEntity(null,aNode,JqValues.parse("\"found\""));
        rootValue.persist();


        Work first = new Work(aNode,aNode.sources,List.of(rootValue.id));
        Work second = new Work(aNode,aNode.sources,List.of(rootValue.id));

        assertEquals(first.hashCode(),second.hashCode(),"work with different id but same scope should have the same hash");
        q.addWorks(List.of(first, second));
        assertEquals(1,q.size(),"second should not be added to the queue");

        tm.commit();
    }

    @Test
    public void poll_null_until_source_completes() throws InterruptedException, SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.sources= List.of(aNode);
        bNode.persist();

        Work aWork = new Work(aNode,null,null);
        Work bWork = new Work(bNode,null,null);
        tm.commit();

        q.addWorks(List.of(bWork));
        q.addWorks(List.of(aWork));
        Runnable firstRunnable = q.take();

        assertNotNull(firstRunnable);

        assertFalse(q.isPending(aWork),"a should be removed from the q");
        assertTrue(q.isPending(bWork),"b should remain in the queue");

        Runnable polled = q.poll();

        assertNull(polled,"b should remain in q until a completes");

        q.decrement(aWork);//fake call to Runnable.run

        polled = q.poll();
        assertNotNull(polled,"b work should return now that a is complete");
    }

    @Test
    public void addAll_adds_new_items() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.persist();
        Work aWork = new Work(aNode, null, null);
        Work bWork = new Work(bNode, null, null);
        tm.commit();

        boolean added = q.addAll(List.of(aWork, bWork));

        assertTrue(added, "addAll should return true when new items are added");
        assertEquals(2, q.size());
        assertTrue(q.isPending(aWork));
        assertTrue(q.isPending(bWork));
    }

    @Test
    public void addAll_rejects_all_duplicates() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        Work aWork = new Work(aNode, null, null);
        tm.commit();

        q.addWorks(List.of(aWork));
        assertEquals(1, q.size());

        boolean added = q.addAll(List.of(aWork));

        assertFalse(added, "addAll should return false when items added are duplicates");
        assertEquals(1, q.size(), "queue size should not change");
    }

    @Test
    public void addAll_check_and_adds_only_new() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();
        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.persist();
        Work aWork = new Work(aNode, null, null);
        Work bWork = new Work(bNode, null, null);
        tm.commit();

        q.addWorks(List.of(aWork));
        int sizeBefore = q.size();
        assertEquals(1,sizeBefore,"aWork has one work pending aNode");

        boolean added = q.addAll(List.of(aWork, bWork));

        assertTrue(added, "addAll should return true when at least one item is new");
        assertTrue(q.isPending(aWork), "aWork should still be pending — it was skipped, not removed");
        assertTrue(q.isPending(bWork), "bWork should be pending — it was newly added");
        assertEquals(sizeBefore + 1, q.size(), "exactly one item (bWork) should have been added");
    }

    @Test
    public void addAll_wakes_sleeping_thread() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException, InterruptedException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        Work aWork = new Work(aNode, null, null);
        tm.commit();

        Runnable[] result = new Runnable[1];
        Thread t2 = new Thread(() -> {
            try {
                result[0] = q.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t2.start();
        Thread.sleep(100);

        assertNull(result[0], "thread should be sleeping. Has no work yet");

        q.addAll(List.of(aWork));

        t2.join(500);
        assertNotNull(result[0], "thread should have woken up and taken the work");
    }

    // ---- Recalculation-scoped cancellation tests ----

    @Test
    public void removeByRecalcId_only_removes_matching_tracker() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.persist();
        NodeEntity cNode = new JqNode("c");
        cNode.persist();
        tm.commit();

        // Create work for two different recalculations + one upload
        Work recalc1Work = new Work(aNode, null, null);
        recalc1Work.recalcTrackerId = 100L;
        recalc1Work.folderId = 1;

        Work recalc2Work = new Work(bNode, null, null);
        recalc2Work.recalcTrackerId = 200L;
        recalc2Work.folderId = 1; // same folder, different recalc

        Work uploadWork = new Work(cNode, null, null);
        uploadWork.folderId = 1; // upload for same folder (recalcTrackerId = null)

        q.addWorks(List.of(recalc1Work, recalc2Work, uploadWork));
        assertEquals(3, q.size(), "all three should be queued");

        // Remove only recalc1's work
        List<Work> removed = q.removeByRecalcId(100L);
        assertEquals(1, removed.size(), "should remove exactly 1 item");
        assertEquals(recalc1Work, removed.get(0));
        assertEquals(2, q.size(), "recalc2 and upload should remain");

        // Verify recalc2 and upload are still there
        assertTrue(q.isPending(recalc2Work), "recalc2 should still be pending");
        assertTrue(q.isPending(uploadWork), "upload should still be pending");
    }

    @Test
    public void removeByRecalcId_does_not_remove_upload_work() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        tm.commit();

        // Upload work has recalcTrackerId = null
        Work uploadWork = new Work(aNode, null, null);
        uploadWork.folderId = 1;

        q.addWorks(List.of(uploadWork));
        assertEquals(1, q.size());

        // Trying to remove by any recalc ID should not affect upload work
        List<Work> removed = q.removeByRecalcId(100L);
        assertTrue(removed.isEmpty(), "should not remove upload work");
        assertEquals(1, q.size(), "upload should remain");
    }

    @Test
    public void poll_return_first_non_blocked() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.sources= List.of(aNode);
        bNode.persist();
        NodeEntity cNode = new JqNode("c");
        cNode.persist();

        Work aWork = new Work(aNode,null,null);
        Work bWork = new Work(bNode,null,null);
        Work cWork = new Work(cNode,null,null);
        tm.commit();

        q.addWorks(List.of(aWork, bWork, cWork));

        Runnable firstRunnable = q.poll();
        assertFalse(q.isPending(aWork),"a should be removed from the q");

        Runnable polled = q.poll();

        assertNotNull(polled,"poll should pull next runnable work");
        assertTrue(q.isPending(bWork),"b should remain in the queue");
        assertFalse(q.isPending(cWork),"c should not be in the queue");
    }

    @Test
    public void poll_return_child_node_with_different_sourceValue() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        RootNode rootNode = new RootNode();
        rootNode.persist();
        NodeEntity parentNode = new JqNode("parent",".",rootNode);
        parentNode.persist();
        NodeEntity childNode = new JqNode("child",".",parentNode);
        childNode.persist();

        ValueEntity valueOne = new ValueEntity(null,rootNode);
        valueOne.persist();
        ValueEntity valueTwo = new ValueEntity(null,rootNode);
        valueTwo.persist();


        Work parentWork = new Work(parentNode, parentNode.sources,List.of(valueOne.id));
        Work childWork = new Work(childNode,childNode.sources,List.of(valueTwo.id));
        tm.commit();

        q.addWorks(List.of(parentWork, childWork));

        Runnable firstRunnable = q.poll();
        assertEquals(parentWork,firstRunnable,"first runnable should be parentWork");
        assertFalse(q.isPending(parentWork),"parentWork should be removed from the q");

        Runnable polled = q.poll();

        assertNotNull(polled,"poll should pull childWork");
        assertEquals(childWork,polled,"poll should be childWork");
        assertFalse(q.isPending(childWork),"childWork should remain in the queue");
    }



    // ---- Upload gating tests ----

    @Test
    public void upload_gated_during_recalculation_same_folder() {
        // Register a recalculation for folder 1
        workService.registerRecalculation(1L, 100L);
        assertTrue(workService.isFolderRecalculating(1L), "folder 1 should be recalculating");

        // Complete the recalculation
        workService.completeRecalculation(1L, 100L);
        assertFalse(workService.isFolderRecalculating(1L), "folder 1 should no longer be recalculating");
    }

    @Test
    public void upload_not_gated_for_different_folder() {
        // Register a recalculation for folder 1
        workService.registerRecalculation(1L, 100L);

        // Folder 2 should NOT be gated
        assertFalse(workService.isFolderRecalculating(2L), "folder 2 should not be affected by folder 1's recalculation");

        // Cleanup
        workService.completeRecalculation(1L, 100L);
    }

    @Test
    public void multiple_recalculations_same_folder_gate_until_all_complete() {
        // Two independent recalculations for the same folder (different nodes)
        workService.registerRecalculation(1L, 100L);
        workService.registerRecalculation(1L, 200L);
        assertTrue(workService.isFolderRecalculating(1L), "folder should be recalculating");

        // Complete one — folder still gated
        workService.completeRecalculation(1L, 100L);
        assertTrue(workService.isFolderRecalculating(1L), "folder should still be recalculating (one remains)");

        // Complete the other — folder ungated
        workService.completeRecalculation(1L, 200L);
        assertFalse(workService.isFolderRecalculating(1L), "folder should no longer be recalculating");
    }

    @Test
    public void cancel_recalculation_does_not_affect_upload_work() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        WorkQueue q = new WorkQueue();

        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        NodeEntity bNode = new JqNode("b");
        bNode.persist();
        tm.commit();

        // Create upload work and recalculation work for the same folder
        Work uploadWork = new Work(aNode, null, null);
        uploadWork.folderId = 1;
        // recalcTrackerId is null (upload)

        Work recalcWork = new Work(bNode, null, null);
        recalcWork.folderId = 1;
        recalcWork.recalcTrackerId = 100L;

        q.addWorks(List.of(uploadWork, recalcWork));
        assertEquals(2, q.size());

        // Cancel the recalculation
        List<Work> removed = q.removeByRecalcId(100L);
        assertEquals(1, removed.size(), "should remove recalc work");
        assertEquals(recalcWork, removed.get(0));
        assertEquals(1, q.size(), "upload should remain");
        assertTrue(q.isPending(uploadWork), "upload work should not be affected by recalc cancellation");
    }

    @Test
    public void cascade_work_inherits_recalcTrackerId() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        tm.begin();
        NodeEntity aNode = new JqNode("a");
        aNode.persist();
        tm.commit();

        // Create parent recalculation work
        Work parentWork = new Work(aNode, null, null);
        parentWork.folderId = 1;
        parentWork.recalcTrackerId = 100L;

        // Simulate cascade: child work should inherit recalcTrackerId
        Work cascadedWork = new Work(aNode, null, null);
        cascadedWork.folderId = parentWork.folderId;
        cascadedWork.recalcTrackerId = parentWork.recalcTrackerId;

        assertEquals(100L, cascadedWork.recalcTrackerId, "cascaded work should inherit recalcTrackerId");
        assertEquals(1L, cascadedWork.folderId, "cascaded work should inherit folderId");
    }

    @Test
    public void upload_work_recalcTrackerId_is_null_by_default() {
        Work uploadWork = new Work();
        assertNull(uploadWork.recalcTrackerId, "new Work should have null recalcTrackerId (upload)");
        assertEquals(-1, uploadWork.folderId, "new Work should have default folderId");
    }

}

