package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "member",
    description = "Team member management",
    groupCommands = {
        AdminAddMember.class,
    },
    generateHelp = true
)
public class AdminMemberCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) {
        invocation.println("Use 'admin member <subcommand>'. Try 'admin member --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
