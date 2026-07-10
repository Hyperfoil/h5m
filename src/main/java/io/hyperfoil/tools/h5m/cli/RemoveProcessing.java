package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.entity.ProcessingTrackerEntity;
import io.hyperfoil.tools.h5m.svc.ProcessingService;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name="processing", separator = " ", description = "remove unfinished processing from queue", mixinStandardHelpOptions = true)
public class RemoveProcessing implements Callable<Integer> {

    @Inject
    ProcessingService service;

    @Override
    public Integer call() throws Exception {
        int count = service.removeIncompleteProcessing();
        System.out.println("removed "+count);
        return 0;
    }
}
