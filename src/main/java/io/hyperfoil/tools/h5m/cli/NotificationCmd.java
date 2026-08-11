package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;


@CommandDefinition(
    name = "notification",
    description = "Notification configuration management for change detection events",
    groupCommands = {
        AddNotification.class,
        ListNotification.class,
        RemoveNotification.class,
    },
    generateHelp = true
)
public class NotificationCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'notification <subcommand>'. Try 'notification --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
