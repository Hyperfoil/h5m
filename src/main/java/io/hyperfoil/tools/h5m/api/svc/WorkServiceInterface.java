package io.hyperfoil.tools.h5m.api.svc;

import java.util.concurrent.TimeUnit;

public interface WorkServiceInterface {

    boolean terminate(long timeout, TimeUnit timeUnit) throws InterruptedException;

}
