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

@CommandDefinition(name="jsonata", description = "Add a JSONata transformation node that applies a JSONata expression to input data", generateHelp = true)
public class AddJsonata implements Command<H5mCommandInvocation> {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test") String groupName;
    @Arguments(description = "name and jsonata expression") List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        try {
            if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
            String name = (args != null && args.size() >= 1) ? args.get(0) : null;
            String jsonata = (args != null && args.size() >= 2) ? args.get(1) : null;
            if(name == null || name.isBlank()){
                invocation.println("missing jsonata node name");
                return CommandResult.FAILURE;
            }
            if(name.matches("\\d+")){
                invocation.println("nodes names cannot be numbers");
                return CommandResult.FAILURE;
            }
            if("-".equals(jsonata)){
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append(System.lineSeparator());
                    }
                }
                if(sb.length()>0){
                    jsonata = sb.toString().trim();
                }else{
                    invocation.println("unable to read function from input");
                    return CommandResult.FAILURE;
                }
            }
            if(jsonata == null || jsonata.isEmpty()){
                invocation.println("missing jsonata operation");
                return CommandResult.FAILURE;
            }
            if(groupName == null){
                invocation.println("missing group name");
                return CommandResult.FAILURE;
            }
            NodeGroup foundGroup =  nodeGroupService.byName(groupName);
            if(foundGroup == null){
                invocation.println("group not found");
                return CommandResult.FAILURE;
            }

            nodeService.create(name, foundGroup.id(), NodeType.JSONATA, jsonata);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
