package io.hyperfoil.tools.h5m.api;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A group of nodes forming a transformation pipeline")
public record NodeGroup(
        @Schema(description = "Unique group ID") Long id,
        @Schema(description = "Group name")
        // Input hint only: the backend may still send reserved 'h5m.' names.
        @Pattern(regexp = ReservedNamespace.ALLOWED_NAME_PATTERN, message = "names starting with 'h5m.' are reserved for internal use") String name,
        @Schema(description = "Root input node") Node root,
        @Schema(description = "Top-level transformation nodes") List<Node> sources) {
}
