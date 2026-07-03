package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.jjq.JqProgram;
import io.hyperfoil.tools.jjq.jsonata.JsonataCompiler;

import java.util.List;
import java.util.stream.Collectors;

@CommandDefinition(name = "update", description = "Modify a node's name or operation expression", generateHelp = true)
public class UpdateNode implements Command<H5mCommandInvocation> {

    @Argument(description = "node name", required = true)
    String name;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder / group name", completer = FolderCompleter.class)
    String groupName;

    @Option(name = "operation", acceptNameWithoutDashes = true, shortName = 'o', description = "new operation (jq filter, js function, etc.)")
    String operation;

    @Option(name = "name", acceptNameWithoutDashes = true, shortName = 'n', description = "rename the node")
    String newName;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (groupName == null && invocation.hasFolderContext()) {
            groupName = invocation.getFolderName();
        }
        if (groupName == null) {
            invocation.println("folder name is required (use --from or cd into a folder)");
            return CommandResult.FAILURE;
        }

        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if (foundGroup == null) {
            invocation.println("folder " + groupName + " not found");
            return CommandResult.FAILURE;
        }

        List<Node> foundNodes = nodeService.findNodeByFqdn(name, foundGroup.id());
        if (foundNodes.isEmpty()) {
            invocation.println("node " + name + " not found in " + groupName);
            return CommandResult.FAILURE;
        } else if (foundNodes.size() > 1) {
            invocation.println("ambiguous node name " + name + ", matches:\n  " +
                    foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
            return CommandResult.FAILURE;
        }

        Node node = foundNodes.getFirst();

        if (operation == null && newName == null) {
            invocation.println("nothing to update (specify --operation or --name)");
            return CommandResult.FAILURE;
        }

        // Validate the new operation before applying
        if (operation != null) {
            String validationError = validateOperation(node.type(), operation);
            if (validationError != null) {
                invocation.println("invalid operation: " + validationError);
                return CommandResult.FAILURE;
            }
        }

        // Load the entity and apply changes
        NodeEntity entity = NodeEntity.findById(node.id());
        if (entity == null) {
            invocation.println("node entity not found: " + node.id());
            return CommandResult.FAILURE;
        }

        if (newName != null) {
            entity.name = newName;
        }
        if (operation != null) {
            entity.operation = operation;
        }

        nodeService.update(entity);

        if (newName != null && operation != null) {
            invocation.println("updated node " + name + " -> name=" + newName + ", operation=" + operation);
        } else if (newName != null) {
            invocation.println("renamed node " + name + " -> " + newName);
        } else {
            invocation.println("updated operation for " + name);
        }

        return CommandResult.SUCCESS;
    }

    /**
     * Validates the operation expression based on the node type.
     * Returns null if valid, or an error message if invalid.
     */
    private String validateOperation(NodeType type, String operation) {
        try {
            switch (type) {
                case JQ -> {
                    JqProgram.compile(operation);
                }
                case JSONATA -> {
                    JsonataCompiler.compile(operation);
                }
                // JS validation would require GraalVM context — skip for now
                // SQL jsonpath validation is complex — skip for now
                default -> {
                    // No validation for other types
                }
            }
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
