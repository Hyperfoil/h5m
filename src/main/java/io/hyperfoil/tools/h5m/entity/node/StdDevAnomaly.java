package io.hyperfoil.tools.h5m.entity.node;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.StdDevAnomalyConfig;
import io.hyperfoil.tools.h5m.entity.NodeEntity;
import io.hyperfoil.tools.yaup.json.Json;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard-deviation-based anomaly detection node.
 * Detects values that exceed mean ± (deviations × stddev) of the
 * preceding windowSize data points. Designed as a CI gate for
 * catching large regressions relative to natural noise.
 *
 * Sources: [fingerprint=0, groupBy=1, range=2, domain=3]
 */
@Entity
@DiscriminatorValue("sd")
public class StdDevAnomaly extends NodeEntity implements DetectionNode {

    private static final String WINDOW_SIZE = "windowSize";
    public static final int DEFAULT_WINDOW_SIZE = 40;

    private static final String DEVIATIONS = "deviations";
    public static final double DEFAULT_DEVIATIONS = 4.0;

    private static final String DIRECTION = "direction";
    public static final StdDevAnomalyConfig.Direction DEFAULT_DIRECTION = StdDevAnomalyConfig.Direction.BOTH;

    private static final String MIN_DATA_POINTS = "minDataPoints";
    public static final int DEFAULT_MIN_DATA_POINTS = 10;

    private static final String FINGERPRINT_FILTER = "fingerprintFilter";

    @Transient
    private Json config;

    public StdDevAnomaly() {
        config = new Json();
    }

    public StdDevAnomaly(String name, String operation) {
        super(name, operation);
        config = new Json();
    }

    @Override
    public NodeType type() {
        return NodeType.STDDEV_ANOMALY;
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

    /**
     * Sets the source nodes in the required order.
     * @param fingerprint node producing fingerprint values for grouping
     * @param groupBy node for grouping (typically the split/dataset node)
     * @param range node producing the numeric values to monitor
     * @param domain node producing the ordering values (e.g., timestamp, build number)
     */
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

    @Override
    @Transient
    public NodeEntity getRangeNode() {
        return sources.get(2);
    }

    @Transient
    public NodeEntity getDomainNode() {
        return sources.size() > 3 ? sources.get(3) : null;
    }

    @Transient
    public int getWindowSize() {
        return (int) config.getLong(WINDOW_SIZE, DEFAULT_WINDOW_SIZE);
    }

    public void setWindowSize(int windowSize) {
        config.set(WINDOW_SIZE, windowSize);
        operation = config.toString();
    }

    @Transient
    public double getDeviations() {
        // Use Number cast instead of Json.getDouble() to handle BigDecimal values
        // parsed by Json.fromString() (see yaup#41)
        Object val = config.get(DEVIATIONS);
        if (val instanceof Number n) return n.doubleValue();
        return DEFAULT_DEVIATIONS;
    }

    public void setDeviations(double deviations) {
        config.set(DEVIATIONS, deviations);
        operation = config.toString();
    }

    @Transient
    public StdDevAnomalyConfig.Direction getDirection() {
        String dir = config.getString(DIRECTION);
        if (dir == null || dir.isBlank()) return DEFAULT_DIRECTION;
        try {
            return StdDevAnomalyConfig.Direction.valueOf(dir);
        } catch (IllegalArgumentException e) {
            return DEFAULT_DIRECTION;
        }
    }

    public void setDirection(StdDevAnomalyConfig.Direction direction) {
        config.set(DIRECTION, direction.name());
        operation = config.toString();
    }

    @Transient
    public int getMinDataPoints() {
        return (int) config.getLong(MIN_DATA_POINTS, DEFAULT_MIN_DATA_POINTS);
    }

    public void setMinDataPoints(int minDataPoints) {
        config.set(MIN_DATA_POINTS, minDataPoints);
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
        return new StdDevAnomaly(name, operation);
    }
}
