package io.hyperfoil.tools.h5m.api;

public record FixedThresholdConfig(Double min, Double max, Boolean minInclusive, Boolean maxInclusive, String fingerprintFilter) {
}
