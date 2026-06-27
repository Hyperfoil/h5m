package io.hyperfoil.tools.h5m.api;

/**
 * Configuration for the E-Divisive (Hunter) change detection algorithm.
 *
 * <p>The algorithm detects distributional change points in a time series by
 * splitting the series at candidate points and testing whether the segments
 * before and after the split come from different distributions.
 *
 * <p><b>Minimum data requirements:</b> The split phase uses a sliding window
 * of size {@code windowLen} on each side of a candidate change point.
 * The practical minimum series length for detection is roughly {@code 2 * windowLen}
 * (enough data on both sides of a change point). However, the significance test
 * (Welch's t-test) may require more data depending on {@code maxPvalue} — smaller
 * p-value thresholds need more data points to reach statistical significance.
 * With the default {@code windowLen=50}, at least 100+ data points are recommended
 * for reliable detection.
 *
 * @param windowLen sliding window size for the split phase (must be >= 3, default 50).
 *                  Larger values improve detection accuracy but require more data.
 * @param maxPvalue significance threshold; change points with p-value above this are
 *                  discarded (default 0.001). Lower values require stronger evidence,
 *                  which may need more data points to achieve.
 * @param minMagnitude minimum relative change magnitude to report, e.g., 0.1 = 10% (default 0.0)
 * @param maxSeriesLength maximum number of most recent data points to analyze (default 500).
 *                        Bounds computation time for long-running tests.
 * @param fingerprintFilter optional jq filter to select specific fingerprint values
 */
public record EDivisiveConfig(int windowLen, double maxPvalue, double minMagnitude, int maxSeriesLength, String fingerprintFilter) {}
