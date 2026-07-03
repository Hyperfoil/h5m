package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.svc.ProcessingService;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(name = "processing", description = "remove unfinished processing from queue", generateHelp = true)
public class RemoveProcessing implements Command<H5mCommandInvocation> {

    @Inject
    ProcessingService service;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        int count = service.removeIncompleteProcessing();
        invocation.println("removed " + count);
        return CommandResult.SUCCESS;
    }
}
