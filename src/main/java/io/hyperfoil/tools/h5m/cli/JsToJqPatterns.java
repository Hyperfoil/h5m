package io.hyperfoil.tools.h5m.cli;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts simple JavaScript function patterns found in Horreum labels to
 * equivalent jq expressions. This allows the import pipeline to create
 * JqNode instances instead of JsNode instances, eliminating GraalVM Truffle
 * interpreter overhead for these common patterns.
 * <p>
 * Only patterns that have been verified across real Horreum test data are
 * supported. Unsupported patterns return {@code null} and fall back to JsNode.
 *
 * @see <a href="https://github.com/Hyperfoil/h5m/issues/247">Issue #247</a>
 */
public class JsToJqPatterns {

    private JsToJqPatterns() {}

    // --- Pattern 1: Simple division ---
    // time => time / 1000000
    // v => v / 1e+6;
    // (v) => v / 1e+6
    private static final Pattern SIMPLE_DIVISION = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*\\w+\\s*/\\s*([\\d.eE+]+)\\s*;?\\s*"
    );

    // --- Pattern 2: Division + rounding via parseFloat/toFixed ---
    // value => parseFloat((value / 1000000).toFixed(2))
    private static final Pattern DIVISION_TOFIXED = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*parseFloat\\(\\(\\w+\\s*/\\s*([\\d.eE+]+)\\)\\.toFixed\\((\\d+)\\)\\)\\s*;?\\s*"
    );

    // --- Pattern 3: Simple array mean ---
    // fd => fd.reduce((a,b) => a+b) / fd.length
    // (start) => start.reduce((a,b) => a+b) / start.length
    private static final Pattern ARRAY_MEAN_SIMPLE = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*\\w+\\.reduce\\(\\(\\w+,\\s*\\w+\\)\\s*=>\\s*\\w+\\s*\\+\\s*\\w+\\)\\s*/\\s*\\w+\\.length\\s*;?\\s*"
    );

    // --- Pattern 4: Array mean with null guard + toFixed ---
    // value => { if(!value) { return 0 } else { return parseFloat(((value.reduce((a,b) => a+b, 0))/value.length).toFixed(3)) || null }; }
    // Also matches variant with *1000 before toFixed
    private static final Pattern ARRAY_MEAN_GUARDED = Pattern.compile(
            "\\s*\\w+\\s*=>\\s*\\{\\s*if\\s*\\(\\s*!\\s*\\w+\\s*\\)\\s*\\{\\s*return\\s+0\\s*;?\\s*\\}\\s*else\\s*\\{\\s*return\\s+parseFloat\\(\\(\\(\\w+\\.reduce\\(\\(\\w+,\\s*\\w+\\)\\s*=>\\s*\\w+\\s*\\+\\s*\\w+,\\s*0\\)\\)\\s*/\\s*\\w+\\.length(\\s*\\*\\s*([\\d.]+))?\\)\\.toFixed\\((\\d+)\\)\\)\\s*(\\|\\|\\s*null)?\\s*;?\\s*\\}\\s*;?\\s*\\}\\s*;?\\s*"
    );

    // --- Pattern 5: Conditional filter + array mean ---
    // value => { if (value["workload"] != "autobench") { return null } if(!value["results"]) { return 0 } else { return parseFloat(...) } }
    private static final Pattern CONDITIONAL_ARRAY_MEAN = Pattern.compile(
            "\\s*\\w+\\s*=>\\s*\\{\\s*if\\s*\\(\\s*\\w+\\[\"(\\w+)\"\\]\\s*!=\\s*\"(\\w+)\"\\s*\\)\\s*\\{\\s*return\\s+null\\s*;?\\s*\\}\\s*;?\\s*if\\s*\\(\\s*!\\s*\\w+\\[\"(\\w+)\"\\]\\s*\\)\\s*\\{\\s*return\\s+0\\s*;?\\s*\\}\\s*else\\s*\\{\\s*return\\s+parseFloat\\(\\(\\(\\w+\\[\"\\3\"\\]\\.reduce\\(\\(\\w+,\\s*\\w+\\)\\s*=>\\s*\\w+\\s*\\+\\s*\\w+,\\s*0\\)\\)\\s*/\\s*\\w+\\[\"\\3\"\\]\\.length\\)\\.toFixed\\((\\d+)\\)\\).*\\}\\s*;?\\s*\\}\\s*;?\\s*"
    );

    // --- Pattern 6: Array max ---
    // fd => Math.max(...fd)
    private static final Pattern ARRAY_MAX = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*Math\\.max\\(\\.\\.\\.\\w+\\)\\s*;?\\s*"
    );

    // --- Pattern 7: Array min ---
    // fd => Math.min(...fd)
    private static final Pattern ARRAY_MIN = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*Math\\.min\\(\\.\\.\\.\\w+\\)\\s*;?\\s*"
    );

    // --- Pattern 8: Null guard (return "N/A" or value) ---
    // value => { if (value == null) { return "N/A" } else { return value } }
    private static final Pattern NULL_GUARD = Pattern.compile(
            "\\s*\\w+\\s*=>\\s*\\{\\s*if\\s*\\(\\s*\\w+\\s*==\\s*null\\s*\\)\\s*\\{\\s*return\\s+\"([^\"]+)\"\\s*;?\\s*\\}\\s*else\\s*\\{\\s*return\\s+\\w+\\s*;?\\s*\\}\\s*\\}\\s*;?\\s*"
    );

    // --- Pattern 9: parseInt / Number.parseInt ---
    // value => Number.parseInt(value)
    // value => parseInt(value)
    private static final Pattern PARSE_INT = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*(?:Number\\.)?parseInt\\(\\w+\\)\\s*;?\\s*"
    );

    // --- Pattern 10: Simple time division ---
    // time => time / 1000000  (function keyword variant)
    // Create Legume 50% Percentile ms: time => time / 1000000
    // Already covered by SIMPLE_DIVISION

    // --- Pattern 11: Array max of field with Math.round ---
    // (value) => Math.round(value.reduce((curMax, result) => Math.max(curMax, result["throughput"]), 0))
    private static final Pattern ARRAY_MAX_FIELD_ROUNDED = Pattern.compile(
            "\\s*\\(?\\s*\\w+\\s*\\)?\\s*=>\\s*Math\\.round\\(\\w+\\.reduce\\(\\(\\w+,\\s*\\w+\\)\\s*=>\\s*Math\\.max\\(\\w+,\\s*\\w+\\[\"(\\w+)\"\\]\\),\\s*0\\)\\)\\s*;?\\s*"
    );

    /**
     * Attempts to convert a JavaScript function string to an equivalent jq
     * expression. Returns the jq expression string if a known pattern matches,
     * or {@code null} if the function is not convertible.
     *
     * @param jsFunction the JavaScript function source code
     * @return the equivalent jq expression, or {@code null}
     */
    public static String tryConvert(String jsFunction) {
        if (jsFunction == null || jsFunction.isBlank()) {
            return null;
        }

        // Normalize whitespace for matching (but keep original for backrefs)
        String normalized = jsFunction.replaceAll("\\s+", " ").trim();

        Matcher m;

        // Pattern 2: Division + toFixed (check before simple division since it's more specific)
        m = DIVISION_TOFIXED.matcher(normalized);
        if (m.matches()) {
            double divisor = parseNumber(m.group(1));
            int decimals = Integer.parseInt(m.group(2));
            long multiplier = (long) Math.pow(10, decimals);
            return String.format(". / %s * %d | round / %d", formatNumber(divisor), multiplier, multiplier);
        }

        // Pattern 1: Simple division
        m = SIMPLE_DIVISION.matcher(normalized);
        if (m.matches()) {
            double divisor = parseNumber(m.group(1));
            return ". / " + formatNumber(divisor);
        }

        // Pattern 5: Conditional filter + array mean (check before guarded mean since it's more specific)
        m = CONDITIONAL_ARRAY_MEAN.matcher(normalized);
        if (m.matches()) {
            String filterField = m.group(1);
            String filterValue = m.group(2);
            String arrayField = m.group(3);
            int decimals = Integer.parseInt(m.group(4));
            long multiplier = (long) Math.pow(10, decimals);
            return String.format(
                    "if .\"%s\" != \"%s\" then null elif (.\"%s\" == null or .\"%s\" == false) then 0 else (.\"%s\" | add / length * %d | round / %d) end",
                    filterField, filterValue, arrayField, arrayField, arrayField, multiplier, multiplier);
        }

        // Pattern 4: Array mean with null guard + toFixed
        m = ARRAY_MEAN_GUARDED.matcher(normalized);
        if (m.matches()) {
            String scaleStr = m.group(2); // optional multiplier (e.g., *1000)
            int decimals = Integer.parseInt(m.group(3));
            long multiplier = (long) Math.pow(10, decimals);
            double scale = scaleStr != null ? Double.parseDouble(scaleStr) : 1.0;
            if (scale != 1.0) {
                return String.format(
                        "if . == null or . == false then 0 elif length == 0 then null else (add / length * %s * %d | round / %d) end",
                        formatNumber(scale), multiplier, multiplier);
            }
            return String.format(
                    "if . == null or . == false then 0 elif length == 0 then null else (add / length * %d | round / %d) end",
                    multiplier, multiplier);
        }

        // Pattern 3: Simple array mean
        m = ARRAY_MEAN_SIMPLE.matcher(normalized);
        if (m.matches()) {
            return "add / length";
        }

        // Pattern 6: Array max
        m = ARRAY_MAX.matcher(normalized);
        if (m.matches()) {
            return "max";
        }

        // Pattern 7: Array min
        m = ARRAY_MIN.matcher(normalized);
        if (m.matches()) {
            return "min";
        }

        // Pattern 11: Array max of field with rounding
        m = ARRAY_MAX_FIELD_ROUNDED.matcher(normalized);
        if (m.matches()) {
            String field = m.group(1);
            return String.format("[.[] | .%s] | max | round", field);
        }

        // Pattern 8: Null guard
        m = NULL_GUARD.matcher(normalized);
        if (m.matches()) {
            String fallback = m.group(1);
            return String.format("if . == null then \"%s\" else . end", fallback);
        }

        // Pattern 9: parseInt
        m = PARSE_INT.matcher(normalized);
        if (m.matches()) {
            return "tonumber | floor";
        }

        return null;
    }

    private static double parseNumber(String s) {
        // Handle scientific notation like 1e+6, 1e6, 1E+6
        return Double.parseDouble(s.replace("e+", "e").replace("E+", "E"));
    }

    private static String formatNumber(double d) {
        if (d == (long) d) {
            return Long.toString((long) d);
        }
        return Double.toString(d);
    }
}
