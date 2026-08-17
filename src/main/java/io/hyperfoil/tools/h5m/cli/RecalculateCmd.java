package io.hyperfoil.tools.h5m.cli;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ProcessingServiceInterface;

@CommandDefinition(name = "recalculate", description = "Recalculate all computed values in a folder by reprocessing through the node graph", generateHelp = true)
public class RecalculateCmd implements Command<H5mCommandInvocation> {

    private static final long TIMEOUT_MINUTES = 10;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    ProcessingServiceInterface processingService;

    @Argument(description = "folder name", completer = FolderCompleter.class)
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required");
            return CommandResult.FAILURE;
        }
        NodeGroup group = nodeGroupService.find(folderName);
        if (group == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }
        List<Node> topLevelNodes = group.sources();
        if (topLevelNodes == null || topLevelNodes.isEmpty()) {
            invocation.println("No nodes to recalculate in " + folderName);
            return CommandResult.SUCCESS;
        }
        for (Node node : topLevelNodes) {
            processingService.recalculateNode(node.id());
        }
        for (Node node : topLevelNodes) {
            if (!processingService.awaitRecalculation(node.id(), TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                invocation.println("Recalculation timed out for node: " + node.id());
                return CommandResult.FAILURE;
            }
        }
        return CommandResult.SUCCESS;
    }
}
