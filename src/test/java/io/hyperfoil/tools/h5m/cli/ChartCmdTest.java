package io.hyperfoil.tools.h5m.cli;

import java.util.List;
import java.util.Map;

import org.aesh.charts.common.ChartStyle;
import org.aesh.charts.linechart.LineChart;
import org.junit.jupiter.api.Test;

import io.hyperfoil.tools.jjq.value.JqValues;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChartCmdTest {

    @Test
    public void shortLabelsKeepsOnlyVaryingKeys() {
        List<String> fps = List.of(
                "{\"16\":\"jvm\",\"17\":\"quarkus\"}",
                "{\"16\":\"jvm\",\"17\":\"spring\"}",
                "{\"16\":\"native\",\"17\":\"quarkus\"}");
        Map<String, String> labels = ChartCmd.shortLabels(fps);
        assertEquals("jvm, quarkus", labels.get(fps.get(0)));
        assertEquals("jvm, spring", labels.get(fps.get(1)));
        assertEquals("native, quarkus", labels.get(fps.get(2)));
    }

    @Test
    public void shortLabelsDropsConstantKeys() {
        List<String> fps = List.of(
                "{\"16\":\"jvm\",\"17\":\"quarkus\",\"run\":\"same\"}",
                "{\"16\":\"native\",\"17\":\"quarkus\",\"run\":\"same\"}");
        Map<String, String> labels = ChartCmd.shortLabels(fps);
        assertEquals("jvm", labels.get(fps.get(0)));
        assertEquals("native", labels.get(fps.get(1)));
    }

    @Test
    public void shortLabelsDedupesRepeatedValues() {
        List<String> fps = List.of(
                "{\"16\":\"jvm\",\"17\":\"jvm\"}",
                "{\"16\":\"native\",\"17\":\"quarkus\"}");
        Map<String, String> labels = ChartCmd.shortLabels(fps);
        assertEquals("jvm", labels.get(fps.get(0)));
        assertEquals("native, quarkus", labels.get(fps.get(1)));
    }

    @Test
    public void shortLabelsFallsBackToKeysOnCollision() {
        List<String> fps = List.of(
                "{\"16\":\"jvm\"}",
                "{\"17\":\"jvm\"}");
        Map<String, String> labels = ChartCmd.shortLabels(fps);
        assertEquals("16=jvm", labels.get(fps.get(0)));
        assertEquals("17=jvm", labels.get(fps.get(1)));
    }

    @Test
    public void shortLabelsFallsBackWhenNothingVaries() {
        List<String> fps = List.of("{\"16\":\"jvm\"}", "{\"16\":\"jvm\"}");
        Map<String, String> labels = ChartCmd.shortLabels(fps);
        assertEquals("{\"16\":\"jvm\"}", labels.get(fps.get(0)));
    }

    @Test
    public void shortLabelsTruncatesLongFallback() {
        String fp = "{\"averylongkey\":\"averylongvalue-that-keeps-going-and-going-and-going\"}";
        Map<String, String> labels = ChartCmd.shortLabels(List.of(fp, fp));
        assertEquals(60, labels.get(fp).length());
        assertEquals(fp.substring(0, 57) + "...", labels.get(fp));
    }

    @Test
    public void parseSelectionByIndex() {
        List<String> available = List.of("a", "b", "c");
        assertEquals(List.of("a", "c"), ChartCmd.parseFingerprintSelection("1,3", available));
    }

    @Test
    public void parseSelectionIgnoresInvalid() {
        List<String> available = List.of("a", "b", "c");
        assertEquals(List.of("b"), ChartCmd.parseFingerprintSelection("0,2,9", available));
        assertEquals(List.of(), ChartCmd.parseFingerprintSelection("", available));
        assertEquals(List.of(), ChartCmd.parseFingerprintSelection(null, available));
    }

    @Test
    public void parseSelectionCapsAtThree() {
        List<String> available = List.of("a", "b", "c", "d");
        assertEquals(List.of("a", "b", "c"), ChartCmd.parseFingerprintSelection("1,2,3,4", available));
    }

    @Test
    public void parseSelectionMatchesByValue() {
        List<String> available = List.of("{\"16\":\"jvm\"}", "{\"16\":\"native\"}");
        assertEquals(List.of("{\"16\":\"native\"}"),
                ChartCmd.parseFingerprintSelection("native", available));
    }

    @Test
    public void parseSelectionDedupes() {
        List<String> available = List.of("a", "b");
        assertEquals(List.of("a"), ChartCmd.parseFingerprintSelection("1,1,a", available));
    }

    @Test
    public void formatYTickTrimsNoise() {
        assertEquals("15", ChartCmd.formatYTick(15.0));
        assertEquals("0", ChartCmd.formatYTick(0.0));
        assertEquals("-1", ChartCmd.formatYTick(-1.0));
        assertEquals("0.5", ChartCmd.formatYTick(0.5));
        assertEquals("3.25", ChartCmd.formatYTick(3.25));
        assertEquals("3.33", ChartCmd.formatYTick(3.333));
    }

    @Test
    public void shortDomainLabelKeepsShortValues() {
        assertEquals("3.4.1", ChartCmd.shortDomainLabel(JqValues.parse("\"3.4.1\"")));
        assertEquals("300", ChartCmd.shortDomainLabel(JqValues.parse("300")));
        assertEquals("", ChartCmd.shortDomainLabel(null));
    }

    @Test
    public void shortDomainLabelTruncatesLongValues() {
        String label = ChartCmd.shortDomainLabel(JqValues.parse("\"2024-04-30T15:13:05\""));
        assertEquals(12, label.length());
    }
}
