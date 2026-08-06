package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.quarkus.narayana.jta.QuarkusTransaction;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

@CommandDefinition(name = "remove", description = "Remove a notification configuration from a folder", generateHelp = true)
public class RemoveNotification implements Command<H5mCommandInvocation> {

    @Argument(description = "notification config ID", required = true)
    long configId;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        boolean deleted = QuarkusTransaction.requiringNew().call(() -> NotificationConfig.deleteById(configId));
        if (deleted) {
            invocation.println("Removed notification config " + configId);
            return CommandResult.SUCCESS;
        } else {
            invocation.println("Notification config not found: " + configId);
            return CommandResult.FAILURE;
        }
    }
}
