package io.hyperfoil.tools.h5m.api;

public record StdDevAnomalyConfig(
        int windowSize,
        double deviations,
        Direction direction,
        int minDataPoints,
        String fingerprintFilter
) {
    public enum Direction {
        UPPER,
        LOWER,
        BOTH
    }
}
