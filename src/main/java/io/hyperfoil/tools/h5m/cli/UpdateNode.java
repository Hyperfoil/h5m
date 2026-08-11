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

import io.hyperfoil.tools.jjq.JqProgram;
import io.hyperfoil.tools.jjq.jsonata.JsonataCompiler;

import java.util.List;
import java.util.stream.Collectors;

@CommandDefinition(name = "update", description = "Modify a node's name or operation expression", generateHelp = true)
public class UpdateNode implements Command<H5mCommandInvocation>, FolderAware {

    @Argument(description = "node name", required = true, completer = NodeNameCompleter.class)
    String name;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name", completer = FolderCompleter.class)
    String folderName;

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
        if (folderName == null && invocation.hasFolderContext()) {
            folderName = invocation.getFolderName();
        }
        if (folderName == null) {
            invocation.println("folder name is required (use --from or cd into a folder)");
            return CommandResult.FAILURE;
        }

        NodeGroup foundGroup = nodeGroupService.find(folderName);
        if (foundGroup == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        List<Node> foundNodes = nodeService.findNodeByFqdn(name, foundGroup.id());
        if (foundNodes.isEmpty()) {
            invocation.println("node " + name + " not found in " + folderName);
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

        nodeService.update(node.id(), newName, operation);

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

    @Override
    public String getFolderName() { return folderName; }
}
