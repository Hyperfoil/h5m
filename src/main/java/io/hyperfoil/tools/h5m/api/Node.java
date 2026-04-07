package io.hyperfoil.tools.h5m.api;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A transformation node in the DAG pipeline")
public record Node(
        @Schema(description = "Unique node ID") Long id,
        @Schema(description = "Node name") String name,
        @Schema(description = "Fully qualified domain name") String fqdn,
        @Schema(description = "Node type") NodeType type,
        @Schema(description = "Parent node group") NodeGroup group,
        @Schema(description = "Node operation (jq filter, JS function, etc.)") String operation,
        @Schema(description = "Source dependency nodes") List<Node> sources) {
}
