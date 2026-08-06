package io.hyperfoil.tools.h5m.cli;

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.ViewComponent;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;

@CommandDefinition(name = "update", description = "Update a view: add/remove columns or reorder them", generateHelp = true)
public class ViewUpdateCmd implements Command<H5mCommandInvocation> {

    @Inject
    ViewServiceInterface viewService;

    @Inject
    NodeServiceInterface nodeService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Argument(description = "view name", required = true)
    String viewName;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name")
    String folderName;

    @Option(name = "add", acceptNameWithoutDashes = true, description = "node name or ID to add as a column",
            completer = NodeNameCompleter.class)
    String addNode;

    @Option(name = "remove", acceptNameWithoutDashes = true, description = "node name or header to remove from columns",
            completer = NodeNameCompleter.class)
    String removeNode;

    @Option(name = "reorder", acceptNameWithoutDashes = true, description = "node name to reorder",
            completer = NodeNameCompleter.class)
    String reorderNode;

    @Option(name = "position", acceptNameWithoutDashes = true, description = "absolute position for reorder (0-based)")
    Integer position;

    @Option(name = "header", acceptNameWithoutDashes = true, description = "custom header name when adding (defaults to node name, truncated to 20 chars)")
    String headerName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from)");
            return CommandResult.FAILURE;
        }

        // Look up the view
        List<View> views;
        try {
            views = viewService.getViews(folderName);
        } catch (Exception e) {
            invocation.println("Failed to list views: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        View view = views.stream()
                .filter(v -> v.name().equalsIgnoreCase(viewName))
                .findFirst()
                .orElse(null);
        if (view == null) {
            invocation.println("View '" + viewName + "' not found");
            return CommandResult.FAILURE;
        }

        List<ViewComponent> components = new ArrayList<>(view.components() != null ? view.components() : List.of());

        if (addNode != null) {
            return handleAdd(invocation, view, components);
        } else if (removeNode != null) {
            return handleRemove(invocation, view, components);
        } else if (reorderNode != null) {
            return handleReorder(invocation, view, components);
        } else {
            invocation.println("Specify --add, --remove, or --reorder");
            return CommandResult.FAILURE;
        }
    }

    private CommandResult handleAdd(H5mCommandInvocation invocation, View view, List<ViewComponent> components) {
        NodeGroup group = nodeGroupService.byName(folderName);
        if (group == null) {
            invocation.println("Node group for folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        // Try to find the node by name
        List<Node> found = nodeService.findNodeByFqdn(addNode, group.id());
        if (found.isEmpty()) {
            invocation.println("Node '" + addNode + "' not found in folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }
        if (found.size() > 1) {
            invocation.println("'" + addNode + "' is ambiguous, matched multiple nodes:");
            for (Node n : found) {
                invocation.println("  " + n.fqdn());
            }
            return CommandResult.FAILURE;
        }

        Node node = found.getFirst();
        String header = headerName != null ? headerName : node.name();
        if (header.length() > 20) {
            header = header.substring(0, 20);
        }

        int order = components.size();
        ViewComponent newComp = new ViewComponent(null, node.id(), node.name(), node.type().display(), header, order);
        components.add(newComp);

        try {
            View updated = new View(view.id(), view.name(), view.folderId(), components);
            viewService.updateView(view.id(), updated);
            invocation.println("Added column '" + header + "' (node: " + node.name() + ") to view '" + view.name() + "'");
        } catch (Exception e) {
            invocation.println("Failed to update view: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }

    private CommandResult handleRemove(H5mCommandInvocation invocation, View view, List<ViewComponent> components) {
        ViewComponent toRemove = components.stream()
                .filter(c -> c.nodeName().equalsIgnoreCase(removeNode) || c.headerName().equalsIgnoreCase(removeNode))
                .findFirst()
                .orElse(null);
        if (toRemove == null) {
            invocation.println("Column '" + removeNode + "' not found in view '" + view.name() + "'");
            return CommandResult.FAILURE;
        }

        components.remove(toRemove);
        // Re-index the remaining components
        List<ViewComponent> reindexed = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            ViewComponent c = components.get(i);
            reindexed.add(new ViewComponent(c.id(), c.nodeId(), c.nodeName(), c.nodeType(), c.headerName(), i));
        }

        try {
            View updated = new View(view.id(), view.name(), view.folderId(), reindexed);
            viewService.updateView(view.id(), updated);
            invocation.println("Removed column '" + removeNode + "' from view '" + view.name() + "'");
        } catch (Exception e) {
            invocation.println("Failed to update view: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }

    private CommandResult handleReorder(H5mCommandInvocation invocation, View view, List<ViewComponent> components) {
        if (position == null) {
            invocation.println("--position is required when using --reorder");
            return CommandResult.FAILURE;
        }

        ViewComponent toMove = components.stream()
                .filter(c -> c.nodeName().equalsIgnoreCase(reorderNode) || c.headerName().equalsIgnoreCase(reorderNode))
                .findFirst()
                .orElse(null);
        if (toMove == null) {
            invocation.println("Column '" + reorderNode + "' not found in view '" + view.name() + "'");
            return CommandResult.FAILURE;
        }

        components.remove(toMove);
        int pos = Math.max(0, Math.min(position, components.size()));
        components.add(pos, toMove);

        // Re-index
        List<ViewComponent> reindexed = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            ViewComponent c = components.get(i);
            reindexed.add(new ViewComponent(c.id(), c.nodeId(), c.nodeName(), c.nodeType(), c.headerName(), i));
        }

        try {
            View updated = new View(view.id(), view.name(), view.folderId(), reindexed);
            viewService.updateView(view.id(), updated);
            invocation.println("Moved column '" + reorderNode + "' to position " + pos + " in view '" + view.name() + "'");
        } catch (Exception e) {
            invocation.println("Failed to update view: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
