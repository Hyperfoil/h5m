package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.h5m.api.EphemeralMode;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeType;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChartNodeCompleterTest {

    private static Node node(String name, NodeType type) {
        return new Node(1L, name, name, type, 1L, ".x", List.of(), EphemeralMode.KEEP);
    }

    @Test
    public void keepsOnlyPlottableExtractors() {
        List<Node> nodes = List.of(
                node("avThroughput", NodeType.JQ),
                node("calc", NodeType.JS),
                node("format", NodeType.JSONATA),
                node("input", NodeType.USER_INPUT),
                node("_avThroughput", NodeType.JQ),
                node("fp", NodeType.FINGERPRINT),
                node("root", NodeType.ROOT),
                node("dataset", NodeType.SPLIT),
                node("rd.avThroughput.1", NodeType.RELATIVE_DIFFERENCE),
                node("ft.avThroughput.1", NodeType.FIXED_THRESHOLD),
                node("sd.avThroughput.1", NodeType.STDDEV_ANOMALY),
                node("ed.avThroughput.1", NodeType.EDIVISIVE));
        assertEquals(
                List.of("avThroughput", "calc", "format", "input"),
                ChartNodeCompleter.plottableNames(nodes, ""));
    }

    @Test
    public void filtersByPrefixAndSorts() {
        List<Node> nodes = List.of(
                node("version", NodeType.JQ),
                node("avThroughput", NodeType.JQ),
                node("avBuildTime", NodeType.JQ));
        assertEquals(
                List.of("avBuildTime", "avThroughput"),
                ChartNodeCompleter.plottableNames(nodes, "av"));
        assertEquals(List.of(), ChartNodeCompleter.plottableNames(nodes, "zzz"));
        assertEquals(
                List.of("avBuildTime", "avThroughput", "version"),
                ChartNodeCompleter.plottableNames(nodes, null));
    }
}
