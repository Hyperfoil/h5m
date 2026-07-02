package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.entity.ProcessingTrackerEntity;
import io.hyperfoil.tools.h5m.svc.ProcessingService;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name="processing", separator = " ", description = "list incomplete processing events", mixinStandardHelpOptions = true )
public class ListProcessing implements Callable<Integer> {

    @Inject
    ProcessingService processingService;

    @Override
    public Integer call() throws Exception {
        List<ProcessingTrackerEntity> incomplete = processingService.getIncompleteProcessing();
        System.out.println(
                ListCmd.table(80,incomplete,List.of("folderId","referenceId","created"),
                        List.of(
                                e->e.folderId,
                                e->e.referenceId,
                                e->e.createdAt
                        ))
        );
        return 0;
    }
}
