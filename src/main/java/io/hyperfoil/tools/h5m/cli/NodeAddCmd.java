package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;

@CommandDefinition(
    name = "add",
    description = "Add a new computation node to a folder",
    groupCommands = {
        AddJq.class,
        AddJs.class,
        AddJsonata.class,
        AddSqlJsonpath.class,
        AddSqlJsonpathAll.class,
        AddSplit.class,
        AddFixedThreshold.class,
        AddRelativeDifference.class,
        AddStdDevAnomaly.class,
    },
    generateHelp = true
)
public class NodeAddCmd implements Command<H5mCommandInvocation> {
    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        invocation.println("Use 'node add <type>'. Types: jq, js, jsonata, sqlpath, sqlpathall, split, fixedthreshold, relativedifference, stddev");
        return CommandResult.SUCCESS;
    }
}
