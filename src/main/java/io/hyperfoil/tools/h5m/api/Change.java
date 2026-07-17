package io.hyperfoil.tools.h5m.api;

import io.hyperfoil.tools.jjq.value.JqValue;

/**
 * A change detection result. Used by both the upload status endpoint (pull)
 * and webhook notifications (push) — same record, same fields, same data
 * regardless of the consumer.
 *
 * @param valueId     the detection value entity ID
 * @param nodeId      the detection node that produced it
 * @param nodeName    detection node name (e.g., "cpu-regression")
 * @param nodeType    the detection node type (FIXED_THRESHOLD, RELATIVE_DIFFERENCE, etc.)
 * @param data        the full detection value data (ratio, bound, direction, etc.)
 * @param fingerprint fingerprint data extracted from the detection value, or null
 */
public record Change(
        long valueId,
        long nodeId,
        String nodeName,
        NodeType nodeType,
        JqValue data,
        JqValue fingerprint
) {
}
