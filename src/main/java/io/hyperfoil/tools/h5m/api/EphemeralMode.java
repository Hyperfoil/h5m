package io.hyperfoil.tools.h5m.api;

/**
 * Controls whether value data for a node is discarded after upload
 * processing completes to save storage.
 *
 * The value rows and edges are always preserved for ancestry queries.
 */
public enum EphemeralMode {
    /** System decides based on graph structure: ephemeral if node has non-detection children (intermediate node) */
    AUTO,
    /** User explicitly wants data discarded after processing */
    DISCARD,
    /** User explicitly wants data kept */
    KEEP
}
