package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.entity.NodeEntity;

/**
 * Common interface for change detection node types (FixedThreshold, RelativeDifference, etc.)
 * that share fingerprint, groupBy, and range source nodes plus an optional fingerprint filter.
 */
public interface DetectionNode {
    NodeEntity getFingerprintNode();
    NodeEntity getGroupByNode();
    NodeEntity getRangeNode();
    String getFingerprintFilter();
}
