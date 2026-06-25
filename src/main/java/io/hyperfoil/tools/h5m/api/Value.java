package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.jjq.value.JqValue;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A computed value produced by a node")
public record Value(
        @Schema(description = "Unique value ID") Long id,
        @Schema(description = "The JSON data payload") JqValue data,
        @Schema(description = "The node that produced this value") Node node,
        @Schema(description = "The folder this value belongs to") Folder folder) {
}
