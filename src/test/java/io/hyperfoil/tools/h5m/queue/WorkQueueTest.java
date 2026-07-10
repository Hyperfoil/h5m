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


}
