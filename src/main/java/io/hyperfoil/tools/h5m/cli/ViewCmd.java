package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "view",
    description = "View management: create, show, update, and remove data views",
    groupCommands = {
        ViewListCmd.class,
        ViewShowCmd.class,
        ViewCreateCmd.class,
        ViewRemoveCmd.class,
        ViewUpdateCmd.class,
    },
    generateHelp = true
)
public class ViewCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'view <subcommand>'. Try 'view --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
