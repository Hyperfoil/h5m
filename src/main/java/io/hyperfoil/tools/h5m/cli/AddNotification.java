package io.hyperfoil.tools.h5m.cli;

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

@CommandLine.Command(name = "notification", separator = " ", description = "add a notification node", mixinStandardHelpOptions = true)
public class AddNotification implements Callable<Integer> {

    @CommandLine.Option(names = {"to"}, description = "target group / test") String groupName;

    @CommandLine.Option(names = {"monitor"}, arity = "1..*", description = "detection node names to monitor")
    List<String> monitorNames;

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

        if (monitorNames == null || monitorNames.isEmpty()) {
            System.err.println("missing monitor node names");
            return 1;
        }

        List<Long> monitoredNodeIds = new ArrayList<>();
        List<String> allNames = monitorNames.stream().flatMap(n -> Arrays.stream(n.split(","))).map(String::trim).filter(v -> !v.isBlank()).toList();
        for (String monitorName : allNames) {
            foundNodes = nodeService.findNodeByFqdn(monitorName, foundGroup.id());
            if (foundNodes.isEmpty()) {
                System.err.println("could not find matching monitor node by name " + monitorName);
                return 1;
            } else if (foundNodes.size() > 1) {
                System.err.println("found more than one matching monitor node by name " + monitorName + "\n  " + foundNodes.stream().map(Node::fqdn).collect(Collectors.joining("\n  ")));
                return 1;
            }
            monitoredNodeIds.add(foundNodes.getFirst().id());
        }

        nodeService.create(name, foundGroup.id(), NodeType.NOTIFICATION, monitoredNodeIds, null);

        return 0;
    }
}
