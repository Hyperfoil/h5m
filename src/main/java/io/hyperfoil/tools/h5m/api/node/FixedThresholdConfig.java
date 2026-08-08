package io.hyperfoil.tools.h5m.api.node;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuration for the Fixed Threshold detection algorithm")
public record FixedThresholdConfig(Double min, Double max, Boolean minInclusive, Boolean maxInclusive, String fingerprintFilter) implements NodeConfiguration {
}
