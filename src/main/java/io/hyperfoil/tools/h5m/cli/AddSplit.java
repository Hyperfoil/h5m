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

import java.util.List;

@CommandDefinition(name="split", description = "Add a split node that divides array values into individual elements for downstream processing", generateHelp = true)
public class AddSplit implements Command<H5mCommandInvocation>, FolderAware {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class) String folderName;
    @Arguments(description = "name and operation") List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;


    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        String name = (args != null && args.size() >= 1) ? args.get(0) : null;
        String operation = (args != null && args.size() >= 2) ? args.get(1) : null;
        if(name == null){
            invocation.println("missing node name");
            return CommandResult.FAILURE;
        }
        if(folderName == null){
            invocation.println("folder name is required (use --to)");
            return CommandResult.FAILURE;
        }
        NodeGroup foundGroup = nodeGroupService.find(folderName);
        if(foundGroup == null){
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }
        if(operation == null){
            invocation.println("missing operation");
            return CommandResult.FAILURE;
        }

        nodeService.create(name, foundGroup.id(), NodeType.SPLIT, operation);
        return CommandResult.SUCCESS;
    }

    @Override
    public String getFolderName() { return folderName; }
}
