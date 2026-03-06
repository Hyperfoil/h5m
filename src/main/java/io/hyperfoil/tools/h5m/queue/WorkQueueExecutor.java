package io.hyperfoil.tools.h5m.queue;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkQueueExecutor extends ThreadPoolExecutor {

    private static final AtomicInteger atomicInteger = new AtomicInteger(0);

    public WorkQueueExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, WorkQueue workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, r -> new Thread(r, "h5m-work-queue-runner-" + atomicInteger.getAndIncrement()));
    }

    public WorkQueue getWorkQueue() {
        return (WorkQueue) getQueue();
    }
}
