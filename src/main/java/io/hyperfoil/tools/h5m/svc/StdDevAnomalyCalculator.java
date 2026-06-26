package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.StdDevAnomalyConfig;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

/**
 * Standard-deviation-based anomaly detection algorithm.
 * Pure computation — no DB access, no CDI, no JPA dependencies.
 *
 * Checks if the last value in a series exceeds mean ± (deviations × stddev)
 * of the preceding baseline window.
 */
public class StdDevAnomalyCalculator {

    private StdDevAnomalyCalculator() {} // not instantiable

    /**
     * Result of a stddev anomaly check.
     * @param anomaly true if an anomaly was detected
     * @param currentValue the value being checked
     * @param mean mean of the baseline window
     * @param stddev standard deviation of the baseline window
     * @param deviations how many stddevs the current value is from the mean
     * @param threshold the threshold that was exceeded (upper or lower)
     * @param direction "above" or "below", null if no anomaly
     */
    public record Result(
            boolean anomaly,
            double currentValue,
            double mean,
            double stddev,
            double deviations,
            double threshold,
            String direction
    ) {
        public static Result noAnomaly(double currentValue, double mean, double stddev) {
            return new Result(false, currentValue, mean, stddev, (currentValue - mean) / stddev, 0, null);
        }
    }

    /**
     * Evaluates whether the last value in the series is an anomaly
     * relative to the preceding baseline window.
     *
     * @param values the numeric values in chronological order (oldest first).
     *               The last value is the current data point, everything before is the baseline.
     * @param windowSize number of preceding values to use as baseline
     * @param numDeviations number of standard deviations for the threshold
     * @param direction which direction to check (UPPER, LOWER, BOTH)
     * @param minDataPoints minimum total values required before checking
     * @return detection result, or null if insufficient data
     */
    public static Result evaluate(
            java.util.List<Double> values,
            int windowSize,
            double numDeviations,
            StdDevAnomalyConfig.Direction direction,
            int minDataPoints) {

        if (values == null || values.size() < minDataPoints) {
            return null; // insufficient data
        }

        double currentValue = values.getLast();
        int baselineEnd = values.size() - 1;
        int baselineStart = Math.max(0, baselineEnd - windowSize);

        SummaryStatistics stats = new SummaryStatistics();
        for (int i = baselineStart; i < baselineEnd; i++) {
            stats.addValue(values.get(i));
        }

        if (stats.getN() < 2) {
            return null; // need at least 2 data points for meaningful stddev
        }

        double mean = stats.getMean();
        double stddev = stats.getStandardDeviation();

        // Guard against zero stddev (all identical values)
        if (stddev == 0.0) {
            stddev = Math.max(Math.abs(mean) * 0.001, 1e-10);
        }

        double upperThreshold = mean + numDeviations * stddev;
        double lowerThreshold = mean - numDeviations * stddev;
        double actualDeviations = (currentValue - mean) / stddev;

        if ((direction == StdDevAnomalyConfig.Direction.UPPER || direction == StdDevAnomalyConfig.Direction.BOTH)
                && currentValue > upperThreshold) {
            return new Result(true, currentValue, mean, stddev, actualDeviations, upperThreshold, "above");
        }

        if ((direction == StdDevAnomalyConfig.Direction.LOWER || direction == StdDevAnomalyConfig.Direction.BOTH)
                && currentValue < lowerThreshold) {
            return new Result(true, currentValue, mean, stddev, actualDeviations, lowerThreshold, "below");
        }

        return Result.noAnomaly(currentValue, mean, stddev);
    }
}
