package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@CommandDefinition(name="js", description = "Add a JavaScript transformation node that applies a JS function to input data", generateHelp = true)
public class AddJs implements Command<H5mCommandInvocation> {

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test") String groupName;

    @Arguments(description = "name and javascript function") List<String> args;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        try {
            if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
            String name = (args != null && args.size() >= 1) ? args.get(0) : null;
            String function = (args != null && args.size() >= 2) ? args.get(1) : null;
            if("-".equals(function)){
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append(System.lineSeparator());
                    }
                }
                if(sb.length()>0){
                    function = sb.toString().trim();
                }else{
                    invocation.println("unable to read function from input");
                    return CommandResult.FAILURE;
                }
            }
            if(function == null || "-".equals(function)){
                invocation.println("unable to read function from input "+function);
                return CommandResult.FAILURE;
            }
            if(groupName == null){
                invocation.println("missing group name");
                return CommandResult.FAILURE;
            }

            NodeGroup foundGroup = nodeGroupService.byName(groupName);
            if(foundGroup == null){
                invocation.println("unable to find group: "+groupName);
                return CommandResult.FAILURE;
            }

            nodeService.create(name, foundGroup.id(), NodeType.JS, function);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
