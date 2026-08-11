package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.node.EDivisiveConfig;
import io.hyperfoil.tools.h5m.entity.node.EDivisive;

@CommandDefinition(name = "edivisive", aliases = {"ed"}, description = "add an e-divisive (Hunter) change detection node", generateHelp = true)
public class AddEDivisive extends AddDetectionNode {

    @Option(name = "domain", acceptNameWithoutDashes = true, required = true,
            description = "node used to sort the range values (required for e-divisive)",
            completer = NodeNameCompleter.class)
    String domainOption;

    @Option(name = "windowLen", acceptNameWithoutDashes = true,
            description = "sliding window size for the split phase (min 3)",
            defaultValue = "" + EDivisive.DEFAULT_WINDOW_LEN)
    int windowLen;

    @Option(name = "maxPvalue", acceptNameWithoutDashes = true,
            description = "significance threshold for change points",
            defaultValue = "" + EDivisive.DEFAULT_MAX_PVALUE)
    double maxPvalue;

    @Option(name = "minMagnitude", acceptNameWithoutDashes = true,
            description = "minimum relative change magnitude to report (e.g., 0.1 = 10%)",
            defaultValue = "" + EDivisive.DEFAULT_MIN_MAGNITUDE)
    double minMagnitude;

    @Option(name = "maxSeriesLength", acceptNameWithoutDashes = true,
            description = "maximum number of recent data points to analyze",
            defaultValue = "" + EDivisive.DEFAULT_MAX_SERIES_LENGTH)
    int maxSeriesLength;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        domainName = domainOption;
        return super.execute(invocation);
    }

    @Override
    protected CommandResult createDetectionNode(H5mCommandInvocation invocation, NodeGroup group,
                                                 Node fingerprintNode, Node rangeNode, Node domainNode, Node groupByNode) {
        if (domainNode == null) {
            invocation.println("domain node is required for e-divisive");
            return CommandResult.FAILURE;
        }
        nodeService.createConfigured(name, group.id(), NodeType.EDIVISIVE,
                List.of(fingerprintNode.id(), groupByNode.id(), rangeNode.id(), domainNode.id()),
                new EDivisiveConfig(windowLen, maxPvalue, minMagnitude, maxSeriesLength, fingerprintFilter));
        return CommandResult.SUCCESS;
    }
}
