package io.hyperfoil.tools.h5m.api;

import jakarta.validation.constraints.NotEmpty;

public record Folder(Long id, @NotEmpty String name) {
}
