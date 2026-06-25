package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("ft")
public class FixedThreshold extends NodeEntity implements DetectionNode {

    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String MIN_INCLUSIVE = "minInclusive";
    private static final String MAX_INCLUSIVE = "maxInclusive";
    private static final String FINGERPRINT_FILTER = "fingerprintFilter";

    public enum ViolationType {
        ABOVE("above"),
        BELOW("below");

        private final String label;

        ViolationType(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    @Transient
    private JqObject config;

    public FixedThreshold() {
        config = JqObject.EMPTY;
    }

    public FixedThreshold(String name, String operation) {
        super(name, operation);
        config = JqObject.EMPTY;
    }

    @PostLoad
    public void loadConfig() {
        if (config == null || config.size() == 0) {
            if (this.operation != null && !this.operation.isBlank()) {
                JqValue parsed = JqValues.parse(this.operation);
                config = parsed instanceof JqObject obj ? obj : JqObject.EMPTY;
            } else {
                config = JqObject.EMPTY;
            }
        }
    }

    public void setNodes(NodeEntity fingerprint, NodeEntity groupBy, NodeEntity range) {
        List<NodeEntity> sources = new ArrayList<>();
        sources.add(fingerprint);
        sources.add(groupBy);
        sources.add(range);
        this.sources = sources;
    }

    @Transient
    public NodeEntity getFingerprintNode() {
        return sources.get(0);
    }

    @Transient
    public NodeEntity getGroupByNode() {
        return sources.get(1);
    }

    @Transient
    public NodeEntity getRangeNode() {
        return sources.get(2);
    }

    @Transient
    public double getMin() {
        return config.get(MIN).asDouble(Double.NaN);
    }

    public void setMin(double min) {
        config = config.with(MIN, io.hyperfoil.tools.jjq.value.JqNumber.of(min));
        operation = config.toJsonString();
    }

    @Transient
    public double getMax() {
        return config.get(MAX).asDouble(Double.NaN);
    }

    public void setMax(double max) {
        config = config.with(MAX, io.hyperfoil.tools.jjq.value.JqNumber.of(max));
        operation = config.toJsonString();
    }

    @Transient
    public boolean isMinEnabled() {
        return config.has(MIN);
    }

    @Transient
    public boolean isMaxEnabled() {
        return config.has(MAX);
    }

    @Transient
    public boolean isMinInclusive() {
        return config.get(MIN_INCLUSIVE).asBoolean(true);
    }

    public void setMinInclusive(boolean minInclusive) {
        config = config.with(MIN_INCLUSIVE, io.hyperfoil.tools.jjq.value.JqBoolean.of(minInclusive));
        operation = config.toJsonString();
    }

    @Transient
    public boolean isMaxInclusive() {
        return config.get(MAX_INCLUSIVE).asBoolean(true);
    }

    public void setMaxInclusive(boolean maxInclusive) {
        config = config.with(MAX_INCLUSIVE, io.hyperfoil.tools.jjq.value.JqBoolean.of(maxInclusive));
        operation = config.toJsonString();
    }

    @Transient
    public String getFingerprintFilter() {
        return config.has(FINGERPRINT_FILTER) ? config.get(FINGERPRINT_FILTER).asString(null) : null;
    }

    public void setFingerprintFilter(String fingerprintFilter) {
        config = config.with(FINGERPRINT_FILTER, io.hyperfoil.tools.jjq.value.JqString.of(fingerprintFilter));
        operation = config.toJsonString();
    }

    @Override
    public NodeType type() {
        return NodeType.FIXED_THRESHOLD;
    }

    @Override
    protected NodeEntity shallowCopy() {
        return new FixedThreshold(name, operation);
    }
}
