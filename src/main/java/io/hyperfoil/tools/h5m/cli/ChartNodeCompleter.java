package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.quarkus.arc.Arc;
import io.quarkus.logging.Log;

/**
 * Completer for chart range/domain node names. Unlike {@link NodeNameCompleter},
 * it only suggests plottable nodes: extractor nodes (JQ/JS/JSONATA/USER_INPUT)
 * with user-facing names. Structural nodes (ROOT, SPLIT), fingerprint nodes,
 * change detection nodes and {@code _}-prefixed internals can never produce
 * a chart and are filtered out.
 */
public class ChartNodeCompleter implements OptionCompleter<CompleterInvocation> {

    @Override
    public void complete(CompleterInvocation completerInvocation) {
        String input = completerInvocation.getGivenCompleteValue();

        try {
            String folderName = null;
            if (completerInvocation.getCommand() instanceof FolderAware fa && fa.getFolderName() != null) {
                folderName = fa.getFolderName();
            }
            if (folderName == null) {
                FolderContext folderContext = Arc.container().instance(FolderContext.class).get();
                if (folderContext != null && folderContext.isSet()) {
                    folderName = folderContext.getFolderName();
                }
            }
            if (folderName == null) {
                return;
            }

            NodeGroupServiceInterface nodeGroupService = Arc.container().instance(NodeGroupServiceInterface.class).get();
            NodeGroup nodeGroup = nodeGroupService.find(folderName);
            if (nodeGroup == null || nodeGroup.sources() == null) {
                return;
            }

            completerInvocation.addAllCompleterValues(plottableNames(nodeGroup.sources(), input));
        } catch (Exception e) {
            Log.debug("Chart node completion failed", e);
        }
    }

    /**
     * Node names suitable as chart range/domain, filtered and sorted.
     * Pure function, unit tested.
     */
    static List<String> plottableNames(List<Node> nodes, String prefix) {
        String filter = prefix == null ? "" : prefix;
        return nodes.stream()
                .filter(n -> n.name() != null && !n.name().isEmpty())
                .filter(n -> !n.name().startsWith("_"))
                .filter(n -> n.type() == NodeType.JQ
                        || n.type() == NodeType.JS
                        || n.type() == NodeType.JSONATA
                        || n.type() == NodeType.USER_INPUT)
                .map(Node::name)
                .filter(name -> filter.isEmpty() || name.startsWith(filter))
                .sorted()
                .toList();
    }
}
