package io.hyperfoil.tools.h5m.api;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "The type of transformation a node performs")
public enum NodeType {
    EDIVISIVE("ed"),
    FINGERPRINT("fp"),
    FIXED_THRESHOLD("ft"),
    JQ("jq"),
    JS("js"),
    JSONATA("nata"),
    RELATIVE_DIFFERENCE("rd"),
    ROOT("root"),
    STDDEV_ANOMALY("sd"),
    SPLIT("split"),
    USER_INPUT("user");

    private final String display;

    NodeType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    /**
     * Returns true if this node type is a change detection node.
     * Detection nodes (ft, rd, sd, ed) are never ephemeral-nullified
     * and their source nodes are protected from nullification.
     */
    public boolean isDetection() {
        return this == FIXED_THRESHOLD || this == RELATIVE_DIFFERENCE
            || this == STDDEV_ANOMALY || this == EDIVISIVE;
    }

    /**
     * Converts this detection node type to the corresponding {@link Change.ChangeType}.
     * Only valid for detection node types ({@link #isDetection()} returns true).
     *
     * @throws IllegalStateException if this is not a detection type
     */
    public Change.ChangeType toChangeType() {
        return switch (this) {
            case FIXED_THRESHOLD -> Change.ChangeType.FIXED_THRESHOLD;
            case RELATIVE_DIFFERENCE -> Change.ChangeType.RELATIVE_DIFFERENCE;
            case STDDEV_ANOMALY -> Change.ChangeType.STDDEV_ANOMALY;
            case EDIVISIVE -> Change.ChangeType.EDIVISIVE;
            default -> throw new IllegalStateException("Not a detection type: " + this);
        };
    }

}
