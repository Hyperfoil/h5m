package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
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

import java.util.ArrayList;
import java.util.List;

@CommandDefinition(name = "fingerprint", aliases = {"fp"}, description = "Add a fingerprint node that groups values by a unique identity", generateHelp = true)
public class AddFingerprint implements Command<H5mCommandInvocation>, FolderAware {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    String folderName;

    @Arguments(description = "name and source expression (e.g., myFp {mem,cpu}:.)")
    List<String> args;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();

        String name = (args != null && args.size() >= 1) ? args.get(0) : null;
        String sourceExpr = (args != null && args.size() >= 2) ? args.get(1) : null;

        if (name == null || sourceExpr == null) {
            invocation.println("Usage: node add fingerprint [--to folder] <name> <{source1,source2}:.>");
            return CommandResult.FAILURE;
        }
        if (folderName == null) {
            invocation.println("folder name is required (use --to)");
            return CommandResult.FAILURE;
        }
        NodeGroup foundGroup = nodeGroupService.find(folderName);
        if (foundGroup == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        // Parse source node names from {name1,name2}:. expression
        List<Long> sourceIds = new ArrayList<>();
        int start = sourceExpr.indexOf('{');
        int end = sourceExpr.indexOf('}');
        if (start >= 0 && end > start) {
            String nodeNames = sourceExpr.substring(start + 1, end);
            for (String nodeName : nodeNames.split(",")) {
                nodeName = nodeName.trim();
                if (nodeName.isEmpty()) continue;
                List<Node> found = nodeService.findNodeByFqdn(nodeName, foundGroup.id());
                if (found.isEmpty()) {
                    invocation.println("Node '" + nodeName + "' not found");
                    return CommandResult.FAILURE;
                }
                if (found.size() > 1) {
                    invocation.println("'" + nodeName + "' is ambiguous, matched multiple nodes");
                    return CommandResult.FAILURE;
                }
                sourceIds.add(found.getFirst().id());
            }
        } else {
            // Treat as comma-separated node names without braces
            for (String nodeName : sourceExpr.split(",")) {
                nodeName = nodeName.trim();
                if (nodeName.isEmpty()) continue;
                List<Node> found = nodeService.findNodeByFqdn(nodeName, foundGroup.id());
                if (found.isEmpty()) {
                    invocation.println("Node '" + nodeName + "' not found");
                    return CommandResult.FAILURE;
                }
                if (found.size() > 1) {
                    invocation.println("'" + nodeName + "' is ambiguous, matched multiple nodes");
                    return CommandResult.FAILURE;
                }
                sourceIds.add(found.getFirst().id());
            }
        }

        if (sourceIds.isEmpty()) {
            invocation.println("no source nodes found for fingerprint");
            return CommandResult.FAILURE;
        }

        nodeService.createConfigured(name, foundGroup.id(), NodeType.FINGERPRINT, sourceIds, null);
        return CommandResult.SUCCESS;
    }

    @Override
    public String getFolderName() { return folderName; }
}
