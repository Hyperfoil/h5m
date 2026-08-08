package io.hyperfoil.tools.h5m.api.node;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Configuration for the Relative Difference detection algorithm")
public record RelativeDifferenceConfig(String filter, double threshold, int window, int minPrevious, String fingerprintFilter) implements NodeConfiguration {
}
