package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.node.StdDevAnomalyConfig;
import io.hyperfoil.tools.h5m.entity.node.StdDevAnomaly;

@CommandDefinition(name = "stddev", aliases = {"sd"}, description = "Add a standard deviation anomaly detection node that identifies statistical outliers in time series data", generateHelp = true)
public class AddStdDevAnomaly extends AddDetectionNode {

    @Option(name = "domain", acceptNameWithoutDashes = true, description = "node used to sort the range values",
            completer = NodeNameCompleter.class)
    String domainOption;

    @Option(name = "windowSize", acceptNameWithoutDashes = true,
            description = "number of preceding data points for baseline",
            defaultValue = {"" + StdDevAnomaly.DEFAULT_WINDOW_SIZE})
    int windowSize;

    @Option(name = "deviations", acceptNameWithoutDashes = true,
            description = "number of standard deviations for alert threshold",
            defaultValue = {"" + StdDevAnomaly.DEFAULT_DEVIATIONS})
    double deviations;

    @Option(name = "direction", acceptNameWithoutDashes = true,
            description = "UPPER, LOWER, or BOTH",
            defaultValue = {"BOTH"})
    StdDevAnomalyConfig.Direction direction;

    @Option(name = "minDataPoints", acceptNameWithoutDashes = true,
            description = "minimum data points before alerting",
            defaultValue = {"" + StdDevAnomaly.DEFAULT_MIN_DATA_POINTS})
    int minDataPoints;

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
        nodeService.createConfigured(name, group.id(), NodeType.STDDEV_ANOMALY, sources,
                new StdDevAnomalyConfig(windowSize, deviations, direction, minDataPoints, fingerprintFilter));
        return CommandResult.SUCCESS;
    }
}
