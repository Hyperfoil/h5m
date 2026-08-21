package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "user",
    description = "User management",
    groupCommands = {
        AdminCreateUser.class,
        AdminListUsers.class,
    },
    generateHelp = true
)
public class AdminUserCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) {
        invocation.println("Use 'admin user <subcommand>'. Try 'admin user --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
