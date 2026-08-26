package io.hyperfoil.tools.h5m.api;

import jakarta.validation.constraints.Pattern;

/**
 * Team metadata DTO. Used at the API boundary instead of the JPA entity.
 */
public record Team(long id,
        // Input hint only: the backend may still send reserved 'h5m.' names.
        @Pattern(regexp = ReservedNamespace.ALLOWED_NAME_PATTERN, message = "names starting with 'h5m.' are reserved for internal use") String name,
        int memberCount) {}
