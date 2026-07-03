package io.hyperfoil.tools.h5m.cli;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;

@CommandDefinition(name = "purge", description = "Delete all computed values from the database", generateHelp = true)
public class PurgeValuesCmd implements Command<H5mCommandInvocation> {

    @Inject
    ValueServiceInterface valueService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        valueService.purgeValues();
        invocation.println("All values purged");
        return CommandResult.SUCCESS;
    }
}
