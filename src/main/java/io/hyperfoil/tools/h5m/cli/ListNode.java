package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.util.graph.Graph;
import org.aesh.util.graph.GraphNode;
import org.aesh.util.graph.GraphStyle;
import org.aesh.util.tree.Tree;
import org.aesh.util.tree.TreeNode;

import io.hyperfoil.tools.jjq.JqProgram;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CommandDefinition(name = "list", aliases = {"nodes"}, description = "List computation nodes in a folder with their types, operations, and source relationships", generateHelp = true)
public class ListNode implements Command<H5mCommandInvocation> {

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    public enum Render { Table, Graph, Tree }

    @Option(name = "from", acceptNameWithoutDashes = true, description = "group name")
    String groupName;

    @Option(name = "as", acceptNameWithoutDashes = true, description = "render format (Table, Graph, or Tree)", defaultValue = { "Table" })
    Render render;

    @Option(name = "filter", acceptNameWithoutDashes = true, shortName = 'f', description = "filter nodes by name (substring match)")
    String filter;

    @Option(name = "depth", acceptNameWithoutDashes = true, shortName = 'd', description = "max tree depth to display (Tree mode only)", defaultValue = { "-1" })
    int depth;

    @Option(name = "jq", acceptNameWithoutDashes = true, description = "jq expression to filter nodes, e.g. select(.type == \"JQ\")")
    String jqFilter;

    @Option(name = "root", acceptNameWithoutDashes = true, shortName = 'r', description = "show only the subtree rooted at this node (substring match)")
    String rootNode;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        return doExecute(invocation);
    }

    CommandResult doExecute(H5mCommandInvocation invocation) {
        if (groupName == null && invocation.hasFolderContext())
            groupName = invocation.getFolderName();
        if (groupName == null) {
            invocation.println("group name is required (use --from)");
            return CommandResult.FAILURE;
        }
        NodeGroup nodeGroup = nodeGroupService.byName(groupName);
        if (nodeGroup == null) {
            invocation.println("NodeEntity group " + groupName + " not found");
            return CommandResult.FAILURE;
        }
        if (render == null)
            render = Render.Table;

        List<Node> sources = nodeGroup.sources();

        if (rootNode != null && !rootNode.isEmpty()) {
            List<Node> matches = sources.stream()
                    .filter(n -> n.name().contains(rootNode) || n.fqdn().contains(rootNode))
                    .collect(Collectors.toList());
            if (matches.isEmpty()) {
                invocation.println("No node matching '" + rootNode + "' found");
                return CommandResult.FAILURE;
            } else if (matches.size() > 1) {
                invocation.println("Multiple nodes match '" + rootNode + "':");
                matches.forEach(n -> invocation.println("  " + n.fqdn()));
                return CommandResult.FAILURE;
            }
            Node match = matches.getFirst();
            if (render.equals(Render.Tree)) {
                TreeNode treeRoot = TreeNode.of(match.name() + " [" + match.type().display() + "]");
                Map<Node, TreeNode> nodes = new HashMap<>();
                nodes.put(match, treeRoot);
                for (Node source : match.sources()) {
                    walkTree(source, nodes);
                }
                invocation.println(Tree.<TreeNode>builder()
                        .label(TreeNode::label)
                        .children(TreeNode::children)
                        .maxDepth(depth)
                        .build()
                        .render(treeRoot));
            } else if (render.equals(Render.Graph)) {
                GraphNode graphRoot = GraphNode.of(match.name());
                Map<Node, GraphNode> nodes = new HashMap<>();
                nodes.put(match, graphRoot);
                for (Node source : match.sources()) {
                    walkGraph(source, nodes);
                }
                invocation.println(Graph.render(graphRoot, GraphStyle.ROUNDED));
            } else {
                invocation.println(
                        ListCmd.table(80, match.sources(), List.of("name", "type", "fqdn", "operation"),
                                List.of(Node::name,
                                        n -> n.type().display(),
                                        Node::fqdn,
                                        Node::operation)));
            }
            return CommandResult.SUCCESS;
        }

        if (filter != null && !filter.isEmpty()) {
            sources = sources.stream()
                    .filter(n -> n.name().contains(filter) || n.fqdn().contains(filter))
                    .collect(Collectors.toList());
        }
        if (jqFilter != null && !jqFilter.isEmpty()) {
            try {
                JqProgram program = JqProgram.compile(jqFilter);
                sources = sources.stream()
                        .filter(n -> {
                            JqObject.Builder builder = JqObject.builder();
                            builder.put("name", n.name());
                            builder.put("fqdn", n.fqdn());
                            builder.put("type", n.type().display());
                            builder.put("operation", n.operation() != null ? n.operation() : "");
                            JqValue[] sourceNames = n.sources() != null
                                    ? n.sources().stream().map(s -> (JqValue) JqString.of(s.name())).toArray(JqValue[]::new)
                                    : new JqValue[0];
                            builder.put("sources", JqArray.of(sourceNames));
                            JqObject json = builder.build();
                            List<JqValue> result = program.applyAll(json);
                            return !result.isEmpty() && !result.getFirst().isNull()
                                    && !(result.getFirst().isBoolean() && !result.getFirst().asBoolean(false));
                        })
                        .collect(Collectors.toList());
            } catch (Exception e) {
                invocation.println("Invalid jq expression: " + e.getMessage());
                return CommandResult.FAILURE;
            }
        }

        boolean filtered = (filter != null && !filter.isEmpty()) || (jqFilter != null && !jqFilter.isEmpty());

        if (render.equals(Render.Graph)) {
            GraphNode rootNode = GraphNode.of("root");
            Map<Node, GraphNode> nodes = new HashMap<>();
            nodes.put(nodeGroup.root(), rootNode);
            for (Node source : sources) {
                walkGraph(source, nodes);
            }
            invocation.println(Graph.render(rootNode, GraphStyle.ROUNDED));
        } else if (render.equals(Render.Tree)) {
            if (filtered) {
                // Build a set of filtered node names for lookup
                java.util.Set<String> filteredNames = sources.stream()
                        .map(Node::name)
                        .collect(Collectors.toSet());
                // Collect all source nodes referenced by filtered nodes that aren't in the filtered set
                // These are the "context" parents that should appear at the top
                Map<String, TreeNode> sourceRoots = new HashMap<>();
                TreeNode treeRoot = TreeNode.of(groupName);
                for (Node node : sources) {
                    boolean addedUnderParent = false;
                    if (node.sources() != null) {
                        for (Node parent : node.sources()) {
                            if (!filteredNames.contains(parent.name())) {
                                TreeNode parentTree = sourceRoots.computeIfAbsent(parent.name(),
                                        name -> {
                                            TreeNode pn = TreeNode.of(name + " [" + parent.type().display() + "]");
                                            treeRoot.child(pn);
                                            return pn;
                                        });
                                parentTree.child(node.name() + " [" + node.type().display() + "]");
                                addedUnderParent = true;
                            }
                        }
                    }
                    if (!addedUnderParent) {
                        treeRoot.child(node.name() + " [" + node.type().display() + "]");
                    }
                }
                invocation.println(Tree.<TreeNode>builder()
                        .label(TreeNode::label)
                        .children(TreeNode::children)
                        .maxDepth(depth)
                        .build()
                        .render(treeRoot));
            } else {
                TreeNode rootNode = TreeNode.of(groupName);
                Map<Node, TreeNode> nodes = new HashMap<>();
                nodes.put(nodeGroup.root(), rootNode);
                for (Node source : sources) {
                    walkTree(source, nodes);
                }
                invocation.println(Tree.<TreeNode>builder()
                        .label(TreeNode::label)
                        .children(TreeNode::children)
                        .maxDepth(depth)
                        .build()
                        .render(rootNode));
            }
        } else {
            invocation.println(
                    ListCmd.table(80, sources, List.of("name", "type", "fqdn", "operation"),
                            List.of(Node::name,
                                    n -> n.type().display(),
                                    Node::fqdn,
                                    Node::operation)));
        }

        return CommandResult.SUCCESS;
    }

    public GraphNode walkGraph(Node node, Map<Node, GraphNode> nodes) {
        if (nodes.containsKey(node)) {
            return nodes.get(node);
        } else {
            GraphNode rtrn = GraphNode.of(node.name());
            nodes.put(node, rtrn);
            for (Node source : node.sources()) {
                GraphNode fromSource = walkGraph(source, nodes);
                fromSource.child(rtrn);
            }
            return rtrn;
        }
    }

    public TreeNode walkTree(Node node, Map<Node, TreeNode> nodes) {
        if (nodes.containsKey(node)) {
            return nodes.get(node);
        } else {
            TreeNode rtrn = TreeNode.of(node.name() + " [" + node.type().display() + "]");
            nodes.put(node, rtrn);
            for (Node source : node.sources()) {
                TreeNode fromSource = walkTree(source, nodes);
                fromSource.child(rtrn);
            }
            return rtrn;
        }
    }
}
