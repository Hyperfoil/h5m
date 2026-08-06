package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;


@CommandDefinition(
    name = "legacy",
    description = "Import data from a legacy Horreum PostgreSQL database",
    groupCommands = {
        LoadLegacyTests.class,
        LoadLegacyRuns.class,
        VerifyLegacy.class,
    },
    generateHelp = true
)
public class LegacyCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'legacy <subcommand>'. Try 'legacy --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
