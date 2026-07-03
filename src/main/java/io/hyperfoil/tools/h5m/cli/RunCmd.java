package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "run",
    description = "Run management: list uploads, show run data, and upload new data",
    groupCommands = {
        RunListCmd.class,
        RunShowCmd.class,
        RunUploadCmd.class,
    },
    generateHelp = true
)
public class RunCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'run <subcommand>'. Try 'run --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
