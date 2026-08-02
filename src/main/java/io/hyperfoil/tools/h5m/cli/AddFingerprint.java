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
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import java.util.ArrayList;
import java.util.List;

@CommandDefinition(name = "fingerprint", description = "Add a fingerprint node that groups values by a unique identity", generateHelp = true)
public class AddFingerprint implements Command<H5mCommandInvocation> {

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target group / test")
    String groupName;

    @Argument(description = "source expression (e.g., \"{mem,cpu}:.\" or node names comma-separated)")
    String sourceExpr;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (groupName == null && invocation.hasFolderContext()) groupName = invocation.getFolderName();
        if (sourceExpr == null || sourceExpr.isEmpty()) {
            invocation.println("missing source expression for fingerprint");
            return CommandResult.FAILURE;
        }
        if (groupName == null) {
            invocation.println("missing group name (use --to)");
            return CommandResult.FAILURE;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if (foundGroup == null) {
            invocation.println("could not find group " + groupName);
            return CommandResult.FAILURE;
        }

        // Parse source node names from expression like "{mem,cpu}:." or "({mem,cpu})=>..."
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
                    invocation.println("could not find node: " + nodeName);
                    return CommandResult.FAILURE;
                }
                sourceIds.add(found.getFirst().id());
            }
        } else {
            // Treat as comma-separated node names
            for (String nodeName : sourceExpr.split(",")) {
                nodeName = nodeName.trim();
                if (nodeName.isEmpty()) continue;
                List<Node> found = nodeService.findNodeByFqdn(nodeName, foundGroup.id());
                if (found.isEmpty()) {
                    invocation.println("could not find node: " + nodeName);
                    return CommandResult.FAILURE;
                }
                sourceIds.add(found.getFirst().id());
            }
        }

        if (sourceIds.isEmpty()) {
            invocation.println("no source nodes found for fingerprint");
            return CommandResult.FAILURE;
        }

        nodeService.createConfigured("_fp-fingerprint", foundGroup.id(), NodeType.FINGERPRINT, sourceIds, null);
        return CommandResult.SUCCESS;
    }
}
