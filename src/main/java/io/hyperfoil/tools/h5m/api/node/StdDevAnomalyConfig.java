package io.hyperfoil.tools.h5m.api.node;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuration for the StdDev Anomaly detection algorithm")
public record StdDevAnomalyConfig(
        int windowSize,
        double deviations,
        Direction direction,
        int minDataPoints,
        String fingerprintFilter
) implements NodeConfiguration {
    public enum Direction {
        UPPER,
        LOWER,
        BOTH
    }
}
