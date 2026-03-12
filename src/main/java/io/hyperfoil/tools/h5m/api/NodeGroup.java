package io.hyperfoil.tools.h5m.api;

import java.util.List;

public record NodeGroup(Long id, String name, Node root, List<Node> sources) {
}
