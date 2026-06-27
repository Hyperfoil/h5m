package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.yaup.json.Json;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

@Entity
@DiscriminatorValue("ed")
public class EDivisive extends NodeEntity implements DetectionNode {

    private static final String WINDOW_LEN = "windowLen";
    public static final int DEFAULT_WINDOW_LEN = 50;
    private static final String MAX_PVALUE = "maxPvalue";
    public static final double DEFAULT_MAX_PVALUE = 0.001;
    private static final String MIN_MAGNITUDE = "minMagnitude";
    public static final double DEFAULT_MIN_MAGNITUDE = 0.0;
    private static final String MAX_SERIES_LENGTH = "maxSeriesLength";
    public static final int DEFAULT_MAX_SERIES_LENGTH = 500;
    private static final String FINGERPRINT_FILTER = "fingerprintFilter";

    @Transient
    private Json config;

    public EDivisive() {
        config = new Json();
    }

    public EDivisive(String name, String operation) {
        super(name, operation);
        if (operation != null && !operation.isBlank()) {
            config = Json.fromString(operation);
        } else {
            config = new Json();
        }
    }

    @Override
    public NodeType type() {
        return NodeType.EDIVISIVE;
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

    public void setNodes(NodeEntity fingerprint, NodeEntity groupBy, NodeEntity range, NodeEntity domain) {
        List<NodeEntity> sources = new ArrayList<>();
        sources.add(fingerprint);
        sources.add(groupBy);
        sources.add(range);
        if (domain != null) {
            sources.add(domain);
        }
        this.sources = sources;
    }

    @Override
    @Transient
    public NodeEntity getFingerprintNode() {
        return sources.get(0);
    }

    @Override
    @Transient
    public NodeEntity getGroupByNode() {
        return sources.get(1);
    }

    @Transient
    public NodeEntity getRangeNode() {
        return sources.get(2);
    }

    @Transient
    public NodeEntity getDomainNode() {
        return sources.size() > 3 ? sources.get(3) : null;
    }

    @Transient
    public int getWindowLen() {
        return (int) config.getLong(WINDOW_LEN, DEFAULT_WINDOW_LEN);
    }

    public void setWindowLen(int windowLen) {
        config.set(WINDOW_LEN, windowLen);
        operation = config.toString();
    }

    @Transient
    public double getMaxPvalue() {
        Object val = config.get(MAX_PVALUE);
        if (val instanceof Number n) return n.doubleValue();
        return DEFAULT_MAX_PVALUE;
    }

    public void setMaxPvalue(double maxPvalue) {
        config.set(MAX_PVALUE, maxPvalue);
        operation = config.toString();
    }

    @Transient
    public double getMinMagnitude() {
        Object val = config.get(MIN_MAGNITUDE);
        if (val instanceof Number n) return n.doubleValue();
        return DEFAULT_MIN_MAGNITUDE;
    }

    public void setMinMagnitude(double minMagnitude) {
        config.set(MIN_MAGNITUDE, minMagnitude);
        operation = config.toString();
    }

    @Transient
    public int getMaxSeriesLength() {
        return (int) config.getLong(MAX_SERIES_LENGTH, DEFAULT_MAX_SERIES_LENGTH);
    }

    public void setMaxSeriesLength(int maxSeriesLength) {
        config.set(MAX_SERIES_LENGTH, maxSeriesLength);
        operation = config.toString();
    }

    @Override
    @Transient
    public String getFingerprintFilter() {
        return config.getString(FINGERPRINT_FILTER);
    }

    public void setFingerprintFilter(String fingerprintFilter) {
        config.set(FINGERPRINT_FILTER, fingerprintFilter);
        operation = config.toString();
    }

    @Override
    protected NodeEntity shallowCopy() {
        return new EDivisive(name, operation);
    }
}
