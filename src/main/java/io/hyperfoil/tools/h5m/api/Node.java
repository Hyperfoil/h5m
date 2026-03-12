package io.hyperfoil.tools.h5m.api;

import java.util.List;

public record Node(Long id, String name, String fqdn, NodeType type, NodeGroup group, String operation, List<Node> sources) {
}
