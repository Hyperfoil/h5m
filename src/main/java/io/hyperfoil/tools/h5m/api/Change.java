package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.jjq.value.JqValue;

/**
 * A change detection result attributed to a specific upload.
 *
 * @param valueId    the detection value entity ID
 * @param nodeId     the detection node that produced it
 * @param nodeName   detection node name (e.g., "cpu-regression")
 * @param changeType the type of change detection algorithm
 * @param data       the detection value data (threshold info, magnitude, etc.)
 */
public record Change(
        long valueId,
        long nodeId,
        String nodeName,
        ChangeType changeType,
        JqValue data
) {
    public enum ChangeType {
        FIXED_THRESHOLD,
        RELATIVE_DIFFERENCE,
        STDDEV_ANOMALY,
        EDIVISIVE
    }
}
