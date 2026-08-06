package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;


@CommandDefinition(
        name = "admin",
        description = "Administration commands for managing users, teams, and API keys",
        groupCommands = {
                AdminCreateTeam.class,
                AdminCreateUser.class,
                AdminListTeams.class,
                AdminListUsers.class,
                AdminAddMember.class,
                AdminCreateApiKey.class,
                AdminListApiKeys.class,
                AdminRevokeApiKey.class,
        },
        generateHelp = true
)
public class AdminCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) {
        return CommandResult.SUCCESS;
    }
}
