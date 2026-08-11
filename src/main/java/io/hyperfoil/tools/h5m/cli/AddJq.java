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


import org.aesh.readline.prompt.Prompt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@CommandDefinition(name="jq", description = "Add a JQ transformation node that applies a jq filter expression to input data", generateHelp = true)
public class AddJq implements Command<H5mCommandInvocation>, FolderAware {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class) String folderName;
    @Arguments(description = "name and jq filter") List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        try {
            if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
            String name = (args != null && args.size() >= 1) ? args.get(0) : null;
            String jq = (args != null && args.size() >= 2) ? args.get(1) : null;
            if(name == null){
                name = invocation.getShell().readLine(new Prompt("Enter name: "));
            }
            NodeGroup foundGroup;
            do{
                if(folderName == null){
                    folderName = invocation.getShell().readLine(new Prompt("Enter target group / folder name: "));
                }
                foundGroup =  nodeGroupService.find(folderName);
                if(foundGroup == null){
                    invocation.println("Folder '" + folderName + "' not found");
                    folderName = null;
                }
            }while(folderName == null);

            if("-".equals(jq)){
                StringBuilder sb = new StringBuilder();
                // Read from stdin for piped input — intentional System.in usage
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
                jq = invocation.getShell().readLine(new Prompt("Enter jq filter: "));
            }

            nodeService.create(name, foundGroup.id(), NodeType.JQ, jq);

            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Error: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    @Override
    public String getFolderName() { return folderName; }
}
