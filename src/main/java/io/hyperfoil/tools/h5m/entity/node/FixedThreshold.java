package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.entity.Node;
import io.hyperfoil.tools.yaup.json.Json;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("ft")
public class FixedThreshold extends Node {

    private static final String MIN = "min";
    private static final String MAX = "max";
    private static final String INCLUSIVE = "inclusive";
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
    private Json config;

    public FixedThreshold() {
        config = new Json();
    }

    public FixedThreshold(String name, String operation) {
        super(name, operation);
        config = new Json();
    }

    @PostLoad
    public void loadConfig() {
        if (this.config == null || this.config.isEmpty()) {
            if (this.operation != null && !this.operation.isBlank()) {
                config = Json.fromString(this.operation);
            } else {
                config = new Json();
            }
        }
    }

    public void setNodes(Node fingerprint, Node groupBy, Node range) {
        List<Node> sources = new ArrayList<>();
        sources.add(fingerprint);
        sources.add(groupBy);
        sources.add(range);
        this.sources = sources;
    }

    @Transient
    public Node getFingerprintNode() {
        return sources.get(0);
    }

    @Transient
    public Node getGroupByNode() {
        return sources.get(1);
    }

    @Transient
    public Node getRangeNode() {
        return sources.get(2);
    }

    @Transient
    public double getMin() {
        Object val = config.get(MIN);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return Double.NaN;
    }

    public void setMin(double min) {
        config.set(MIN, min);
        operation = config.toString();
    }

    @Transient
    public double getMax() {
        Object val = config.get(MAX);
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        return Double.NaN;
    }

    public void setMax(double max) {
        config.set(MAX, max);
        operation = config.toString();
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
    public boolean isInclusive() {
        return config.getBoolean(INCLUSIVE, true);
    }

    public void setInclusive(boolean inclusive) {
        config.set(INCLUSIVE, inclusive);
        operation = config.toString();
    }

    @Transient
    public String getFingerprintFilter() {
        return config.getString(FINGERPRINT_FILTER);
    }

    public void setFingerprintFilter(String fingerprintFilter) {
        config.set(FINGERPRINT_FILTER, fingerprintFilter);
        operation = config.toString();
    }

    @Override
    protected Node shallowCopy() {
        return new FixedThreshold(name, operation);
    }
}
