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
import java.util.Scanner;

@CommandDefinition(name="jq", description = "Add a JQ transformation node that applies a jq filter expression to input data", generateHelp = true)
public class AddJq implements Command<H5mCommandInvocation> {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test") String groupName;
    @Arguments(description = "name and jq filter") List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        try {
            if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
            String name = (args != null && args.size() >= 1) ? args.get(0) : null;
            String jq = (args != null && args.size() >= 2) ? args.get(1) : null;
            Scanner sc = new Scanner(System.in);
            if(name == null){
                invocation.print("Enter name: ");
                name = sc.nextLine();
            }
            NodeGroup foundGroup;
            do{
                if(groupName == null){
                    invocation.print("Enter target group / folder name: ");
                    groupName = sc.nextLine();
                }
                foundGroup =  nodeGroupService.byName(groupName);
                if(foundGroup == null){
                    invocation.println("could not find "+groupName);
                    groupName = null;
                }
            }while(groupName == null);

            if("-".equals(jq)){
                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append(System.lineSeparator());
                    }
                }
                if(sb.length()>0){
                    jq = sb.toString().trim();
                }else{
                    invocation.println("unable to read function from input");
                    return CommandResult.FAILURE;
                }
            }
            if(jq == null){
                invocation.print("Enter jq filter: ");
                jq = sc.nextLine();
            }

            nodeService.create(name, foundGroup.id(), NodeType.JQ, jq);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
