package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.node.FixedThresholdConfig;

@CommandDefinition(name = "fixedthreshold", aliases = {"ft"}, description = "Add a fixed threshold change detection node that flags values exceeding a configured bound", generateHelp = true)
public class AddFixedThreshold extends AddDetectionNode {

    @Option(name = "min", acceptNameWithoutDashes = true, description = "minimum threshold value")
    Double min;

    @Option(name = "max", acceptNameWithoutDashes = true, description = "maximum threshold value")
    Double max;

    @Option(name = "min-inclusive", acceptNameWithoutDashes = true, description = "whether min boundary value is within range", defaultValue = {"true"})
    boolean minInclusive;

    @Option(name = "max-inclusive", acceptNameWithoutDashes = true, description = "whether max boundary value is within range", defaultValue = {"true"})
    boolean maxInclusive;

    @Override
    protected CommandResult createDetectionNode(H5mCommandInvocation invocation, NodeGroup group,
                                                 Node fingerprintNode, Node rangeNode, Node domainNode, Node groupByNode) {
        nodeService.createConfigured(name, group.id(), NodeType.FIXED_THRESHOLD,
                List.of(fingerprintNode.id(), groupByNode.id(), rangeNode.id()),
                new FixedThresholdConfig(min, max, minInclusive, maxInclusive, fingerprintFilter));
        return CommandResult.SUCCESS;
    }
}
