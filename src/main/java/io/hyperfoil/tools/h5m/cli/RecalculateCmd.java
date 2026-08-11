package io.hyperfoil.tools.h5m.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.svc.RecalculationTracker;

@CommandDefinition(name = "recalculate", description = "Recalculate all computed values in a folder by reprocessing through the node graph", generateHelp = true)
public class RecalculateCmd implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Argument(description = "folder name", completer = FolderCompleter.class)
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
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
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Node node : topLevelNodes) {
            RecalculationTracker tracker = folderService.recalculateNode(node.id());
            futures.add(tracker.getFuture());
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(10, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            invocation.println("Recalculation failed: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
