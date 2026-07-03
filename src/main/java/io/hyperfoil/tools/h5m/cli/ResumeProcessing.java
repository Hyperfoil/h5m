package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.svc.ProcessingService;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(name = "resume", description = "resume incomplete processing events", generateHelp = true)
public class ResumeProcessing implements Command<H5mCommandInvocation> {

    @Inject
    ProcessingService service;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        service.recoverIncompleteProcessing(null);
        return CommandResult.SUCCESS;
    }
}
