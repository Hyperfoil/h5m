package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;

import java.util.List;

/**
 * Shared formatting for detection values displayed in CLI output.
 * Used by UploadCmd (synchronous completion summary) and ChangesCmd.
 */
final class ChangeFormatter {

    private ChangeFormatter() {}

    static String formatSummary(List<Value> detectionValues) {
        if (detectionValues.isEmpty()) {
            return "No changes detected.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(detectionValues.size()).append(detectionValues.size() == 1 ? " change" : " changes").append(" detected:");
        for (Value v : detectionValues) {
            sb.append("\n  ").append(formatChange(v));
        }
        return sb.toString();
    }

    static String formatChange(Value value) {
        String nodeName = value.node() != null ? value.node().name() : "unknown";
        NodeType nodeType = value.node() != null ? value.node().type() : null;
        String typeName = nodeType != null ? nodeType.name() : "unknown";
        String details = formatDetails(value.data(), nodeType);
        String fingerprint = formatFingerprint(value.data());
        if (fingerprint != null) {
            return String.format("%s (%s): %s, fingerprint=%s", nodeName, typeName, details, fingerprint);
        }
        return String.format("%s (%s): %s", nodeName, typeName, details);
    }

    private static String formatDetails(JqValue data, NodeType nodeType) {
        if (data == null || !(data instanceof JqObject obj)) {
            return "no data";
        }
        if (nodeType == null) {
            return truncate(data.toJsonString(), 80);
        }
        return switch (nodeType) {
            case FIXED_THRESHOLD -> {
                String value = obj.has("value") ? obj.get("value").toJsonString() : "?";
                String bound = obj.has("bound") ? obj.get("bound").toJsonString() : "?";
                String direction = obj.has("direction") ? obj.get("direction").asText() : "?";
                yield String.format("value=%s %s bound %s", value, direction, bound);
            }
            case RELATIVE_DIFFERENCE -> {
                String ratio = obj.has("ratio") ? String.format("%.1f%%", obj.get("ratio").asDouble(0.0)) : "?";
                yield String.format("ratio=%s", ratio);
            }
            case STDDEV_ANOMALY -> {
                String direction = obj.has("direction") ? obj.get("direction").asText() : "?";
                String deviations = obj.has("deviations") ? obj.get("deviations").toJsonString() : "?";
                yield String.format("%s (%s deviations)", direction, deviations);
            }
            case EDIVISIVE -> {
                String magnitude = obj.has("magnitude") ? String.format("%.2f", obj.get("magnitude").asDouble(0.0)) : "?";
                String pvalue = obj.has("pvalue") ? String.format("%.4f", obj.get("pvalue").asDouble(0.0)) : "?";
                yield String.format("magnitude=%s, p-value=%s", magnitude, pvalue);
            }
            default -> truncate(data.toJsonString(), 80);
        };
    }

    private static String formatFingerprint(JqValue data) {
        if (data == null || !data.has("fingerprint")) {
            return null;
        }
        JqValue fp = data.getField("fingerprint");
        if (fp == null || fp.isNull()) {
            return null;
        }
        return fp.toJsonString();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
