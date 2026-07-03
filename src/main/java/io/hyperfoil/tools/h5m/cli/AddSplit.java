package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

import java.util.List;

@CommandDefinition(name="split", description = "Add a split node that divides array values into individual elements for downstream processing", generateHelp = true)
public class AddSplit implements Command<H5mCommandInvocation> {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test") String groupName;
    @Arguments(description = "name and operation") List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;


    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
        String name = (args != null && args.size() >= 1) ? args.get(0) : null;
        String operation = (args != null && args.size() >= 2) ? args.get(1) : null;
        if(name == null){
            invocation.println("missing node name");
            return CommandResult.FAILURE;
        }
        if(groupName == null){
            invocation.println("missing group name");
            return CommandResult.FAILURE;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if(foundGroup == null){
            invocation.println("could not find target group/test "+groupName);
            return CommandResult.FAILURE;
        }
        if(operation == null){
            invocation.println("missing operation");
            return CommandResult.FAILURE;
        }



        return CommandResult.SUCCESS;
    }
}
