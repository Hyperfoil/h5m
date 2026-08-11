package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;


import java.util.List;

@CommandDefinition(name="remove", description = "Remove a computation node from a folder", generateHelp = true)
public class RemoveNode implements Command<H5mCommandInvocation>, FolderAware {

    @Inject
    NodeServiceInterface nodeService;

    @Argument(description = "node name", required = true, completer = NodeNameCompleter.class) String name;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class) String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        String fqdn = folderName == null ? name : folderName + NodeEntity.FQDN_SEPARATOR + name;
        List<Node> found = nodeService.findNodeByFqdn(fqdn);
        if(found==null || found.isEmpty()) {
            invocation.println("Node '" + fqdn + "' not found");
            return CommandResult.FAILURE;
        }else if (found.size()>1){
            invocation.println("found too many matching nodes");
            found.forEach(n -> invocation.println(n.toString()));
            return CommandResult.FAILURE;
        }else{
            nodeService.delete(found.getFirst().id());
        }
        return CommandResult.SUCCESS;
    }

    @Override
    public String getFolderName() { return folderName; }
}
