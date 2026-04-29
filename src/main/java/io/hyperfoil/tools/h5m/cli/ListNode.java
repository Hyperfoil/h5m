package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.aesh.util.graph.GraphStyle;
import picocli.CommandLine;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.aesh.util.graph.Graph;
import org.aesh.util.graph.GraphNode;

@CommandLine.Command(name="nodes", separator = " ", description = "list nodes", mixinStandardHelpOptions = true)
public class ListNode implements Callable<Integer> {

    @CommandLine.ParentCommand
    ListCmd parent;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    public static enum Render {Table, Graph};

    @CommandLine.Option(names = {"from"},description = "group name", arity="0..1") String groupName;

    @CommandLine.Option(names = {"as"}, description = "Valid values: ${COMPLETION-CANDIDATES}\")", defaultValue = "Table") Render render;

    @CommandLine.Option(names = {"--hide"}, description = "hide nodes matching suffix pattern (comma-separated, e.g. _primary,_alt)", arity = "0..1") String hidePattern;

    @CommandLine.Option(names = {"--type"}, description = "filter by node type (comma-separated, e.g. sql,ecma,fp,rd)", arity = "0..1") String typeFilter;

    @CommandLine.Option(names = {"--depth"}, description = "max depth from root", arity = "0..1", defaultValue = "0") int maxDepth;

    @CommandLine.Option(names = {"--show-type"}, description = "prefix node labels with [type]", defaultValue = "true") boolean showType;

    @CommandLine.Option(names = {"--max-label-width"}, description = "wrap labels at word boundaries when wider than N chars (0 = no wrap)", defaultValue = "30") int maxLabelWidth;

    @Override
    @Transactional
    public Integer call() throws Exception {
        groupName = groupName==null ? parent.name : groupName;
        if(groupName == null){
            CommandLine cmd = new CommandLine(this);
            cmd.usage(System.err);
            return 1;
        }
        NodeGroup nodeGroup = nodeGroupService.byName(groupName);
        if(nodeGroup == null){
            System.err.println("NodeEntity group "+groupName+" not found");
            return 1;
        }
        if(render.equals(Render.Graph)){
            Set<String> skipNames = new HashSet<>();
            if (hidePattern != null) {
                List<String> suffixes = Arrays.stream(hidePattern.split(",")).map(String::trim).toList();
                for (Node source : nodeGroup.sources()) {
                    for (String suffix : suffixes) {
                        if (source.name().endsWith(suffix)) {
                            skipNames.add(source.name());
                            break;
                        }
                    }
                }
            }

            Set<String> types = typeFilter != null
                ? Arrays.stream(typeFilter.split(",")).map(String::trim).collect(Collectors.toSet())
                : Collections.emptySet();

            String rootLabel = showType ? "[root] root" : "root";
            GraphNode rootNode = GraphNode.of(rootLabel);
            Map<Node,GraphNode> nodes = new HashMap<>();
            nodes.put(nodeGroup.root(), rootNode);
            for(Node source: nodeGroup.sources()){
                walk(source, nodes, skipNames, types, 1, maxDepth, showType);
            }
            System.out.println(Graph.render(rootNode, GraphStyle.ROUNDED, 0, maxLabelWidth));
        }else {
            System.out.println(
                ListCmd.table(80,nodeGroup.sources(),List.of("name","type","fqdn","operation"),
                    List.of(Node::name,
                        n->n.type().display(),
                        Node::fqdn,
                        Node::operation
                    )
                )
            );
        }

        return 0;
    }

    public GraphNode walk(Node node, Map<Node,GraphNode> nodes, Set<String> skipNames,
                          Set<String> typeFilter, int depth, int maxDepth, boolean showType){
        if(nodes.containsKey(node)){
            return nodes.get(node);
        }
        if(maxDepth > 0 && depth > maxDepth){
            return null;
        }

        boolean skip = skipNames.contains(node.name())
            || (!typeFilter.isEmpty() && !typeFilter.contains(node.type().display()));

        GraphNode rtrn = null;
        if (!skip) {
            String label = showType ? "[" + node.type().display() + "] " + node.name() : node.name();
            rtrn = GraphNode.of(label);
            nodes.put(node, rtrn);
        }

        for(Node source: node.sources()){
            GraphNode fromSource = walk(source, nodes, skipNames, typeFilter, depth + 1, maxDepth, showType);
            if(rtrn != null && fromSource != null){
                fromSource.child(rtrn);
            } else if(rtrn != null && nodes.containsKey(source)){
                nodes.get(source).child(rtrn);
            }
        }
        return rtrn;
    }

}
