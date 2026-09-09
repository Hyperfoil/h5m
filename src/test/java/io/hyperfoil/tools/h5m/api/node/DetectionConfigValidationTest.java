package io.hyperfoil.tools.h5m.api.node;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class DetectionConfigValidationTest {

    @Inject
    Validator validator;

    // ---- EDivisiveConfig @Min(3) on windowLen ----

    @Test
    public void eDivisive_windowLen_at_minimum_boundary() {
        EDivisiveConfig config = new EDivisiveConfig(3, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "windowLen=3 should pass @Min(3)");
    }

    @Test
    public void eDivisive_windowLen_below_minimum() {
        EDivisiveConfig config = new EDivisiveConfig(2, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowLen=2 should fail @Min(3)");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("windowLen")));
    }

    @Test
    public void eDivisive_windowLen_zero() {
        EDivisiveConfig config = new EDivisiveConfig(0, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowLen=0 should fail @Min(3)");
    }

    @Test
    public void eDivisive_windowLen_negative() {
        EDivisiveConfig config = new EDivisiveConfig(-5, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowLen=-5 should fail @Min(3)");
    }

    // ---- EDivisiveConfig @DecimalMin("0") @DecimalMax("1") on maxPvalue ----

    @Test
    public void eDivisive_maxPvalue_at_min_boundary() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.0, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "maxPvalue=0.0 should pass @DecimalMin(\"0\")");
    }

    @Test
    public void eDivisive_maxPvalue_at_max_boundary() {
        EDivisiveConfig config = new EDivisiveConfig(50, 1.0, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "maxPvalue=1.0 should pass @DecimalMax(\"1\")");
    }

    @Test
    public void eDivisive_maxPvalue_within_range() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.05, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "maxPvalue=0.05 should pass both constraints");
    }

    @Test
    public void eDivisive_maxPvalue_below_minimum() {
        EDivisiveConfig config = new EDivisiveConfig(50, -0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "maxPvalue=-0.001 should fail @DecimalMin(\"0\")");
    }

    @Test
    public void eDivisive_maxPvalue_exceeds_maximum() {
        EDivisiveConfig config = new EDivisiveConfig(50, 1.1, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "maxPvalue=1.1 should fail @DecimalMax(\"1\")");
    }

    @Test
    public void eDivisive_maxPvalue_far_exceeds_maximum() {
        EDivisiveConfig config = new EDivisiveConfig(50, 2.0, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "maxPvalue=2.0 should fail @DecimalMax(\"1\")");
    }

    // ---- EDivisiveConfig @DecimalMin("0") on minMagnitude ----

    @Test
    public void eDivisive_minMagnitude_at_boundary() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minMagnitude=0.0 should pass @DecimalMin(\"0\")");
    }

    @Test
    public void eDivisive_minMagnitude_positive_value() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.5, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minMagnitude=0.5 should pass @DecimalMin(\"0\")");
    }

    @Test
    public void eDivisive_minMagnitude_negative() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, -0.1, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "minMagnitude=-0.1 should fail @DecimalMin(\"0\")");
    }

    // ---- EDivisiveConfig @Positive on maxSeriesLength ----

    @Test
    public void eDivisive_maxSeriesLength_valid() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.0, 500, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "maxSeriesLength=500 should pass @Positive");
    }

    @Test
    public void eDivisive_maxSeriesLength_one() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.0, 1, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "maxSeriesLength=1 should pass @Positive");
    }

    @Test
    public void eDivisive_maxSeriesLength_zero() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.0, 0, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "maxSeriesLength=0 should fail @Positive");
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("maxSeriesLength")));
    }

    @Test
    public void eDivisive_maxSeriesLength_negative() {
        EDivisiveConfig config = new EDivisiveConfig(50, 0.001, 0.0, -100, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "maxSeriesLength=-100 should fail @Positive");
    }

    // ---- RelativeDifferenceConfig @DecimalMin("0") on threshold ----

    @Test
    public void relativeDiff_threshold_at_boundary() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.0, 10, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "threshold=0.0 should pass @DecimalMin(\"0\")");
    }

    @Test
    public void relativeDiff_threshold_positive() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.5, 10, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "threshold=0.5 should pass @DecimalMin(\"0\")");
    }

    @Test
    public void relativeDiff_threshold_negative() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, -0.1, 10, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "threshold=-0.1 should fail @DecimalMin(\"0\")");
    }

    // ---- RelativeDifferenceConfig @Positive on window ----

    @Test
    public void relativeDiff_window_valid() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.1, 10, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "window=10 should pass @Positive");
    }

    @Test
    public void relativeDiff_window_one() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MEAN,RelativeDifferenceConfig.Filter.MEAN, 0.1, 1, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "window=1 should pass @Positive");
    }

    @Test
    public void relativeDiff_window_zero() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.1, 0, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "window=0 should fail @Positive");
    }

    @Test
    public void relativeDiff_window_negative() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MIN,RelativeDifferenceConfig.Filter.MAX, 0.1, -5, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "window=-5 should fail @Positive");
    }

    // ---- RelativeDifferenceConfig @Positive on minPrevious ----

    @Test
    public void relativeDiff_minPrevious_valid() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.1, 10, 5, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minPrevious=5 should pass @Positive");
    }

    @Test
    public void relativeDiff_minPrevious_one() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MEAN,RelativeDifferenceConfig.Filter.MAX, 0.1, 10, 1, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minPrevious=1 should pass @Positive");
    }

    @Test
    public void relativeDiff_minPrevious_zero() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,RelativeDifferenceConfig.Filter.MAX, 0.1, 10, 0, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "minPrevious=0 should fail @Positive");
    }

    @Test
    public void relativeDiff_minPrevious_negative() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MIN,RelativeDifferenceConfig.Filter.MAX, 0.1, 10, -1, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "minPrevious=-1 should fail @Positive");
    }

    // ---- StdDevAnomalyConfig @Min(2) on windowSize ----

    @Test
    public void stdDev_windowSize_at_minimum_boundary() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(2, 1.0, StdDevAnomalyConfig.Direction.BOTH, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "windowSize=2 should pass @Min(2)");
    }

    @Test
    public void stdDev_windowSize_below_minimum() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(1, 1.0, StdDevAnomalyConfig.Direction.BOTH, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowSize=1 should fail @Min(2)");
    }

    @Test
    public void stdDev_windowSize_zero() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(0, 1.0, StdDevAnomalyConfig.Direction.UPPER, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowSize=0 should fail @Min(2)");
    }

    @Test
    public void stdDev_windowSize_negative() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(-5, 1.0, StdDevAnomalyConfig.Direction.LOWER, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "windowSize=-5 should fail @Min(2)");
    }

    // ---- StdDevAnomalyConfig @DecimalMin("0", inclusive = false) on deviations ----

    @Test
    public void stdDev_deviations_above_zero() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(10, 0.1, StdDevAnomalyConfig.Direction.BOTH, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "deviations=0.1 should pass @DecimalMin(\"0\", inclusive=false)");
    }

    @Test
    public void stdDev_deviations_positive() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, 3.0, StdDevAnomalyConfig.Direction.BOTH, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "deviations=3.0 should pass constraint");
    }

    @Test
    public void stdDev_deviations_exactly_zero() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, 0.0, StdDevAnomalyConfig.Direction.UPPER, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "deviations=0.0 should fail @DecimalMin(\"0\", inclusive=false)");
    }

    @Test
    public void stdDev_deviations_negative() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, -1.0, StdDevAnomalyConfig.Direction.LOWER, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "deviations=-1.0 should fail @DecimalMin");
    }

    // ---- StdDevAnomalyConfig @Positive on minDataPoints ----

    @Test
    public void stdDev_minDataPoints_valid() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, 2.0, StdDevAnomalyConfig.Direction.BOTH, 100, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minDataPoints=100 should pass @Positive");
    }

    @Test
    public void stdDev_minDataPoints_one() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(2, 1.0, StdDevAnomalyConfig.Direction.UPPER, 1, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "minDataPoints=1 should pass @Positive");
    }

    @Test
    public void stdDev_minDataPoints_zero() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, 2.0, StdDevAnomalyConfig.Direction.LOWER, 0, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "minDataPoints=0 should fail @Positive");
    }

    @Test
    public void stdDev_minDataPoints_negative() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(5, 2.0, StdDevAnomalyConfig.Direction.BOTH, -50, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "minDataPoints=-50 should fail @Positive");
    }

    // ---- Multiple violations in single config ----

    @Test
    public void eDivisive_multiple_violations_all_fields() {
        EDivisiveConfig config = new EDivisiveConfig(
            2,      // Fails @Min(3)
            1.5,    // Fails @DecimalMax("1")
            -0.1,   // Fails @DecimalMin("0")
            -1,     // Fails @Positive
            null
        );
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertEquals(4, violations.size(), "should have 4 violations for 4 invalid fields");
    }

    @Test
    public void relativeDiff_multiple_violations() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MAX,
            RelativeDifferenceConfig.Filter.MAX,
            -0.1,  // Fails @DecimalMin("0")
            0,     // Fails @Positive
            -5,    // Fails @Positive
            null
        );
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertEquals(3, violations.size(), "should have 3 violations for threshold, window, and minPrevious");
    }

    // ---- Boundary and edge cases ----

    @Test
    public void eDivisive_all_minimum_valid_values() {
        EDivisiveConfig config = new EDivisiveConfig(3, 0.0, 0.0, 1, null);
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "all minimum valid values should pass");
    }

    @Test
    public void relativeDiff_all_minimum_valid_values() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MIN, RelativeDifferenceConfig.Filter.MAX,0.0, 1, 1, null);
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "all minimum valid values should pass");
    }

    @Test
    public void stdDev_all_minimum_valid_values() {
        StdDevAnomalyConfig config = new StdDevAnomalyConfig(2, 0.001, StdDevAnomalyConfig.Direction.BOTH, 1, null);
        Set<ConstraintViolation<StdDevAnomalyConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "all minimum valid values should pass");
    }

    @Test
    public void eDivisive_very_small_valid_decimals() {
        EDivisiveConfig config = new EDivisiveConfig(
            50,
            0.00001,  // Very small valid p-value
            0.00001,  // Very small valid magnitude
            1000,
            null
        );
        Set<ConstraintViolation<EDivisiveConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "very small fractional values within valid ranges should pass");
    }

    @Test
    public void relativeDiff_very_large_valid_values() {
        RelativeDifferenceConfig config = new RelativeDifferenceConfig(
            RelativeDifferenceConfig.Filter.MEAN,
            RelativeDifferenceConfig.Filter.MAX,
            999999.99,  // Very large valid threshold
            10000,      // Very large valid window
            5000,       // Very large valid minPrevious
            null
        );
        Set<ConstraintViolation<RelativeDifferenceConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "very large values within valid ranges should pass");
    }

    // ---- FixedThresholdConfig cross-field validation ----

    @Test
    public void fixedThreshold_min_less_than_max() {
        FixedThresholdConfig config = new FixedThresholdConfig(5.0, 10.0, true, true, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "min < max should pass");
    }

    @Test
    public void fixedThreshold_min_equals_max() {
        FixedThresholdConfig config = new FixedThresholdConfig(5.0, 5.0, true, true, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "min == max should pass");
    }

    @Test
    public void fixedThreshold_min_greater_than_max() {
        FixedThresholdConfig config = new FixedThresholdConfig(10.0, 5.0, true, true, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertFalse(violations.isEmpty(), "min > max should fail");
    }

    @Test
    public void fixedThreshold_only_min_set() {
        FixedThresholdConfig config = new FixedThresholdConfig(10.0, null, true, null, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "only min set should pass (one-sided threshold)");
    }

    @Test
    public void fixedThreshold_only_max_set() {
        FixedThresholdConfig config = new FixedThresholdConfig(null, 10.0, null, true, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "only max set should pass (one-sided threshold)");
    }

    @Test
    public void fixedThreshold_neither_set() {
        FixedThresholdConfig config = new FixedThresholdConfig(null, null, null, null, null);
        Set<ConstraintViolation<FixedThresholdConfig>> violations = validator.validate(config);
        assertTrue(violations.isEmpty(), "neither min nor max set should pass");
    }
}
