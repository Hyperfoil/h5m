package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "team",
    description = "Team management",
    groupCommands = {
        AdminCreateTeam.class,
        AdminListTeams.class,
    },
    generateHelp = true
)
public class AdminTeamCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) {
        invocation.println("Use 'admin team <subcommand>'. Try 'admin team --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
