package io.hyperfoil.tools.h5m.api.node;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuration for the Relative Difference detection algorithm")
public record RelativeDifferenceConfig(
        Filter filter,
        @DecimalMin("0") double threshold,
        @Positive int window,
        @Positive int minPrevious,
        String fingerprintFilter) implements NodeConfiguration {
    public enum Filter { MEAN, MIN, MAX }
}
