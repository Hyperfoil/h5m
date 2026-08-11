package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.node.RelativeDifferenceConfig;
import io.hyperfoil.tools.h5m.entity.node.RelativeDifference;

@CommandDefinition(name="relativedifference", aliases = {"rd"}, description = "Add a relative difference change detection node that detects percentage changes between consecutive values", generateHelp = true)
public class AddRelativeDifference extends AddDetectionNode {

    @Option(name = "domain", acceptNameWithoutDashes = true, description = "node used to sort the range values",
            completer = NodeNameCompleter.class)
    String domainOption;

    @Option(name = "threshold", acceptNameWithoutDashes = true,
            description = "Maximum difference between the aggregated value of last <window> datapoints and the mean of preceding values.",
            defaultValue = {"" + RelativeDifference.DEFAULT_THRESHOLD})
    double threshold;

    @Option(name = "window", acceptNameWithoutDashes = true,
            description = "Number of most recent datapoints used for aggregating the value for comparison.",
            defaultValue = {"" + RelativeDifference.DEFAULT_WINDOW})
    int window;

    @Option(name = "minPrevious", acceptNameWithoutDashes = true,
            description = "Number of datapoints preceding the aggregation window.",
            defaultValue = {"" + RelativeDifference.DEFAULT_MIN_PREVIOUS})
    int minPrevious;

    @Option(name = "filter", acceptNameWithoutDashes = true,
            description = "Function used to aggregate datapoints from the floating window.",
            defaultValue = {RelativeDifference.DEFAULT_FILTER})
    String filter;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        domainName = domainOption;
        return super.execute(invocation);
    }

    @Override
    protected CommandResult createDetectionNode(H5mCommandInvocation invocation, NodeGroup group,
                                                 Node fingerprintNode, Node rangeNode, Node domainNode, Node groupByNode) {
        List<Long> sources = domainNode == null
                ? List.of(fingerprintNode.id(), groupByNode.id(), rangeNode.id())
                : List.of(fingerprintNode.id(), groupByNode.id(), rangeNode.id(), domainNode.id());
        nodeService.createConfigured(name, group.id(), NodeType.RELATIVE_DIFFERENCE, sources,
                new RelativeDifferenceConfig(filter, threshold, window, minPrevious, fingerprintFilter));
        return CommandResult.SUCCESS;
    }
}
