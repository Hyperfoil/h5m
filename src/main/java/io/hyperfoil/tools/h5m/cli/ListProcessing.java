package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.entity.ProcessingTrackerEntity;
import io.hyperfoil.tools.h5m.svc.ProcessingService;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

import java.util.List;

@CommandDefinition(name = "list-processing", description = "list incomplete processing events", generateHelp = true)
public class ListProcessing implements Command<H5mCommandInvocation> {

    @Inject
    ProcessingService processingService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        List<ProcessingTrackerEntity> incomplete = processingService.getIncompleteProcessing();
        invocation.println(
                ListCmd.table(80, incomplete, List.of("folderId", "referenceId", "created"),
                        List.of(
                                e -> e.folderId,
                                e -> e.referenceId,
                                e -> e.createdAt
                        ))
        );
        return CommandResult.SUCCESS;
    }
}
