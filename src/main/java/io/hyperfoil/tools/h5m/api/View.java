package io.hyperfoil.tools.h5m.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "A configured view that defines which node values to display as columns")
public record View(
        @Schema(description = "Unique view ID") Long id,
        @Schema(description = "View name") @NotEmpty
        // Input hint only: the backend may still send reserved 'h5m.' names.
        @Pattern(regexp = ReservedNamespace.ALLOWED_NAME_PATTERN, message = "names starting with 'h5m.' are reserved for internal use") String name,
        @Schema(description = "Folder ID this view belongs to") Long folderId,
        @Schema(description = "Ordered list of column definitions") List<ViewComponent> components) {
}
