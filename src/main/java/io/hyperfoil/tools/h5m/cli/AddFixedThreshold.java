package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.FixedThresholdConfig;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@CommandLine.Command(name = "fixedthreshold", separator = " ", description = "add a fixed threshold node", mixinStandardHelpOptions = true)
public class AddFixedThreshold implements Callable<Integer> {

    @CommandLine.Option(names = {"to"}, description = "target group / test") String groupName;

    @CommandLine.Option(names = {"range"}, arity = "1", description = "node that produces the value to inspect")
    String rangeName;

    @CommandLine.Option(names = {"by"}, description = "grouping node", arity = "0..1")
    public String groupBy;

    @CommandLine.Option(names = {"fingerprint"}, description = "node names to use as fingerprint")
    List<String> fingerprints;

    @CommandLine.Option(names = {"--fingerprint-filter", "-ff"}, arity = "0..1", description = "jq filter expression for fingerprints")
    String fingerprintFilter;

    @CommandLine.Option(names = {"min"}, arity = "0..1", description = "minimum threshold value")
    Double min;

    @CommandLine.Option(names = {"max"}, arity = "0..1", description = "maximum threshold value")
    Double max;

    @CommandLine.Option(names = {"min-inclusive"}, arity = "0..1", description = "whether min boundary value is within range", defaultValue = "true")
    boolean minInclusive;

    @CommandLine.Option(names = {"max-inclusive"}, arity = "0..1", description = "whether max boundary value is within range", defaultValue = "true")
    boolean maxInclusive;

    @CommandLine.Parameters(index = "0", arity = "1", description = "node name") String name;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    NodeServiceInterface nodeService;

    @Override
    public Integer call() throws Exception {
        if (name == null || name.isEmpty()) {
            System.err.println("missing node name");
            return 1;
        }
        if (groupName == null || groupName.isEmpty()) {
            System.err.println("missing group name");
            return 1;
        }
        NodeGroup foundGroup = nodeGroupService.byName(groupName);
        if (foundGroup == null) {
            System.err.println("node group with name " + groupName + " does not exist");
            return 1;
        }

        List<Node> foundNodes = nodeService.findNodeByFqdn(name, foundGroup.id());
        if (!foundNodes.isEmpty()) {
            System.err.println(groupName + " already has " + name + " node(s)\n  " + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
        }

        if (rangeName == null || rangeName.isEmpty()) {
            System.err.println("Missing range");
            return 1;
        }
        foundNodes = nodeService.findNodeByFqdn(rangeName, foundGroup.id());
        if (foundNodes.isEmpty()) {
            System.err.println("could not find matching range node by name " + rangeName);
            return 1;
        } else if (foundNodes.size() > 1) {
            System.err.println("found more than one matching range node by name " + rangeName + "\n  " + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
            return 1;
        }
        Node rangeNode = foundNodes.getFirst();

        Node groupByNode = null;
        if (groupBy != null && !groupBy.isEmpty()) {
            foundNodes = nodeService.findNodeByFqdn(groupBy, foundGroup.id());
            if (foundNodes.isEmpty()) {
                System.err.println("could not find matching group by node with name" + groupBy);
                return 1;
            } else if (foundNodes.size() > 1) {
                System.err.println("found more than one matching group by node for name " + groupBy + "\n  " + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return 1;
            }
            groupByNode = foundNodes.getFirst();
        }
        if (groupByNode == null) {
            groupByNode = foundGroup.root();
        }

        List<Long> fingerprintNodes = new ArrayList<>();
        if (fingerprints != null && !fingerprints.isEmpty()) {
            List<String> fingerprintNames = fingerprints.stream().flatMap(fp -> Arrays.stream(fp.split(","))).map(String::trim).filter(v -> !v.isBlank()).toList();
            for (String fingerprintName : fingerprintNames) {
                foundNodes = nodeService.findNodeByFqdn(fingerprintName, foundGroup.id());
                if (foundNodes.isEmpty()) {
                    System.err.println("could not find matching fingerprint node by name " + fingerprintName);
                    return 1;
                } else if (foundNodes.size() > 1) {
                    System.err.println("found more than one matching fingerprint node by name " + fingerprintName + "\n  " + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                    return 1;
                }
                fingerprintNodes.add(foundNodes.getFirst().id());
            }
        }

        Long fingerprintId = nodeService.create("_fp-" + name, foundGroup.id(), NodeType.FINGERPRINT, fingerprintNodes, null);
        nodeService.create(name, foundGroup.id(), NodeType.FIXED_THRESHOLD, List.of(fingerprintId, groupByNode.id(), rangeNode.id()), new FixedThresholdConfig(min, max, minInclusive, maxInclusive, fingerprintFilter));

        return 0;
    }
}
