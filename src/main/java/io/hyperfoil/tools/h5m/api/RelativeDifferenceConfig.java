package io.hyperfoil.tools.h5m.api;

public record RelativeDifferenceConfig(String filter, double threshold, int window, int minPrevious, String fingerprintFilter) {
}
