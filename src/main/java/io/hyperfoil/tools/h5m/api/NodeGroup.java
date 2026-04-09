package io.hyperfoil.tools.h5m.api;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A group of nodes forming a transformation pipeline")
public record NodeGroup(
        @Schema(description = "Unique group ID") Long id,
        @Schema(description = "Group name") String name,
        @Schema(description = "Root input node") Node root,
        @Schema(description = "Top-level transformation nodes") List<Node> sources) {
}
