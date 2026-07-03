package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;


@CommandDefinition(
    name = "node",
    description = "Node management: add computation nodes (jq, js, jsonata, sqlpath, etc.), list, remove, and update",
    groupCommands = {
        NodeAddCmd.class,
        ListNode.class,
        RemoveNode.class,
        UpdateNode.class,
    },
    generateHelp = true
)
public class NodeCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'node <subcommand>'. Try 'node --help' for available subcommands.");
        return CommandResult.SUCCESS;
    }
}
