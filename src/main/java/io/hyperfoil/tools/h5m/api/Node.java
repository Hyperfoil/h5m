package io.hyperfoil.tools.h5m.api;

import java.util.List;
import java.util.Objects;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "A transformation node in the DAG pipeline")
public record Node(
        @Schema(description = "Unique node ID") Long id,
        @Schema(description = "Node name") @NotEmpty
        // Input hint only: the backend may still send reserved 'h5m.' names.
        @Pattern(regexp = ReservedNamespace.ALLOWED_NAME_PATTERN, message = "names starting with 'h5m.' are reserved for internal use") String name,
        @Schema(description = "Fully qualified domain name") String fqdn,
        @Schema(description = "Node type") @NotNull NodeType type,
        @Schema(description = "Node group ID") @NotNull Long groupId,
        // @NotEmpty guards user-supplied operations on create (JQ/JS/JSONATA).
        // On the response side, system-created ROOT and FINGERPRINT nodes are serialized with operation="", so clients must tolerate an empty operation despite the schema's non-empty hint.
        @Schema(description = "Node operation (jq filter, JS function, etc.); empty for system ROOT/FINGERPRINT nodes") @NotEmpty String operation,
        @Schema(description = "Source dependency nodes") List<Node> sources,
        @Schema(description = "Ephemeral mode: AUTO (system decides), DISCARD (always discard data), KEEP (always keep data)") EphemeralMode ephemeral) {

    @Override
    public int hashCode() {
        if (id != null) {
            return Objects.hash(id, name, type);
        }
        return Objects.hash(name, type, operation, groupId);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Node n) {
            if (id != null && n.id != null) {
                return id.equals(n.id);
            }
            return Objects.equals(name, n.name)
                && Objects.equals(type, n.type)
                && Objects.equals(operation, n.operation)
                && Objects.equals(groupId, n.groupId);
        }
        return false;
    }
}
