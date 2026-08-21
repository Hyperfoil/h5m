package io.hyperfoil.tools.h5m.api.node;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuration for the StdDev Anomaly detection algorithm")
public record StdDevAnomalyConfig(
        @Min(2) int windowSize,
        @DecimalMin(value = "0", inclusive = false) double deviations,
        Direction direction,
        @Positive int minDataPoints,
        String fingerprintFilter
) implements NodeConfiguration {
    public enum Direction {
        UPPER,
        LOWER,
        BOTH
    }
}
