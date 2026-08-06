package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import jakarta.inject.Inject;

import io.quarkus.narayana.jta.QuarkusTransaction;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import io.hyperfoil.tools.h5m.entity.ValueEntity;

@CommandDefinition(name = "show", description = "Show the JSON data for a specific run (upload)", generateHelp = true)
public class RunShowCmd implements Command<H5mCommandInvocation> {

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    ValueServiceInterface valueService;

    @Inject
    NodeServiceInterface nodeService;

    @Argument(description = "run ID (omit to show the newest run)")
    Long runId;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name")
    String folderName;

    @Option(name = "node", acceptNameWithoutDashes = true, description = "show value for a specific node from this run",
            completer = NodeNameCompleter.class)
    String nodeName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from)");
            return CommandResult.FAILURE;
        }

        NodeGroup nodeGroup = nodeGroupService.byName(folderName);
        if (nodeGroup == null) {
            invocation.println("Node group '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        if (nodeGroup.root() == null) {
            invocation.println("No root node found for folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }

        // Use QuarkusTransaction for entity access
        final NodeGroup ng = nodeGroup;
        return QuarkusTransaction.requiringNew().call(() -> {
            ValueEntity rootValue;
            if (runId != null) {
                rootValue = ValueEntity.findById(runId);
                if (rootValue == null) {
                    invocation.println("Run with id " + runId + " not found");
                    return CommandResult.FAILURE;
                }
                if (rootValue.node.id != ng.root().id().longValue()) {
                    invocation.println("Value " + runId + " is not a root value (run) for folder '" + folderName + "'");
                    return CommandResult.FAILURE;
                }
            } else {
                // Get the newest root value
                List<Value> rootValues = valueService.getNodeValues(ng.root().id());
                if (rootValues.isEmpty()) {
                    invocation.println("No runs found for folder '" + folderName + "'");
                    return CommandResult.SUCCESS;
                }
                Value newest = rootValues.getLast();
                rootValue = ValueEntity.findById(newest.id());
                if (rootValue == null) {
                    invocation.println("Failed to load run data");
                    return CommandResult.FAILURE;
                }
            }

            if (nodeName != null && !nodeName.isEmpty()) {
                return showNodeValue(invocation, rootValue, ng);
            }

            // Default: show the root value's data
            JqValue data = rootValue.data;
            if (data == null || data.isNull()) {
                invocation.println("Run " + rootValue.id + " has no data");
                return CommandResult.SUCCESS;
            }

            invocation.println("Run " + rootValue.id + ":");
            invocation.println(JqValues.toPrettyJsonString(data));
            return CommandResult.SUCCESS;
        });
    }

    private CommandResult showNodeValue(H5mCommandInvocation invocation, ValueEntity rootValue, NodeGroup nodeGroup) {
        var foundNodes = nodeService.findNodeByFqdn(nodeName, nodeGroup.id());
        if (foundNodes.isEmpty()) {
            invocation.println("Node '" + nodeName + "' not found in folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }
        if (foundNodes.size() > 1) {
            invocation.println("'" + nodeName + "' is ambiguous, matched multiple nodes:");
            for (var n : foundNodes) {
                invocation.println("  " + n.fqdn());
            }
            return CommandResult.FAILURE;
        }

        var node = foundNodes.getFirst();
        // Find the descendant value for this node from this run
        List<Value> descendants = valueService.getNodeDescendantValues(rootValue.node.id);
        Value match = descendants.stream()
                .filter(v -> v.node() != null && v.node().id().equals(node.id()))
                .findFirst()
                .orElse(null);

        if (match == null) {
            invocation.println("No value found for node '" + nodeName + "' in run " + rootValue.id);
            return CommandResult.SUCCESS;
        }

        JqValue data = match.data();
        if (data == null || data.isNull()) {
            invocation.println("Node '" + nodeName + "' value is null in run " + rootValue.id);
            return CommandResult.SUCCESS;
        }

        invocation.println("Run " + rootValue.id + ", node '" + nodeName + "':");
        invocation.println(JqValues.toPrettyJsonString(data));
        return CommandResult.SUCCESS;
    }
}
