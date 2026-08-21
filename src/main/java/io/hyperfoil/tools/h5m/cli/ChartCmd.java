package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NodeServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import io.hyperfoil.tools.h5m.svc.ValueService;
import jakarta.inject.Inject;

import org.aesh.charts.common.ChartStyle;
import org.aesh.charts.common.DataSeries;
import org.aesh.charts.common.HorizontalLine;
import org.aesh.charts.common.Marker;
import org.aesh.charts.linechart.LineChart;
import io.quarkus.logging.Log;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.readline.prompt.Prompt;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.tty.Size;

import java.util.*;
import java.util.function.Function;

/**
 * Interactive line chart of node values over domain values with change
 * detection markers. Supports multi-fingerprint overlay (up to 3).
 */
@CommandDefinition(name = "chart", description = "Interactive line chart of node values with change detection markers", generateHelp = true)
public class ChartCmd implements Command<H5mCommandInvocation>, FolderAware {

    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String[] SERIES_COLORS = { GREEN, BLUE, CYAN };
    private static final int MAX_FINGERPRINTS = 3;

    // Set during execute(), used by redraw()
    String chartTitle;
    String controlsLine;
    int chartHeight;
    int viewportSize;
    int chartWidth;
    double yMin;
    double yMax;
    boolean showChanges = true;
    boolean markersEmpty;
    ChartStyle style;
    List<JqValue> allValues;
    Node rangeNode;
    Node domainNode;
    Node fingerprintNode;
    NodeGroup nodeGroup;
    List<String> distinctFingerprints;
    List<String> selectedFingerprints;
    Map<String, String> shortLabels;

    @Inject
    NodeServiceInterface nodeService;

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    ValueServiceInterface valueService;

    @Inject
    ValueService valueServiceImpl;

    @Argument(description = "range node name (Y axis values, must hold numbers)", required = true,
            completer = ChartNodeCompleter.class)
    String rangeNodeName;

    @Option(name = "domain", acceptNameWithoutDashes = true, description = "domain node name (X axis ordering, defaults to upload order)",
            completer = ChartNodeCompleter.class)
    String domainNodeName;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    public String folderName;

    @Option(name = "style", acceptNameWithoutDashes = true,
            description = "chart style: braille (default), unicode, ascii",
            defaultValue = "braille")
    String styleName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        // Resolve folder
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from or cd into a folder)");
            return CommandResult.FAILURE;
        }
        NodeGroup nodeGroup = nodeGroupService.find(folderName);
        if (nodeGroup == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }
        Folder folder = folderService.find(folderName);
        long folderId = folder.id();

        // Resolve range node
        Node rangeNode = resolveNode(invocation, rangeNodeName, nodeGroup.id());
        if (rangeNode == null) return CommandResult.FAILURE;

        // Resolve domain node (optional -- without it, X follows upload order)
        Node domainNode = null;
        if (domainNodeName != null && !domainNodeName.isEmpty()) {
            domainNode = resolveNode(invocation, domainNodeName, nodeGroup.id());
            if (domainNode == null) return CommandResult.FAILURE;
        }

        // Find fingerprint nodes by walking the node tree
        // Detection nodes have sources: [fingerprint, groupBy, range, domain?]
        Node fingerprintNode = findFingerprintNode(nodeGroup.sources());

        // Get chart data using lightweight query (pairs range + domain by shared root)
        // The folder_id filter is critical: 0.3ms vs 6.4s without it.
        // Without a domain node, rows follow upload (root ID) order instead.
        List<JqValue> allValues;
        Long fingerprintNodeId = fingerprintNode != null ? fingerprintNode.id() : null;
        if (domainNode != null) {
            allValues = valueServiceImpl.getAlignedValues(
                    rangeNode.id(), domainNode.id(), folderId, fingerprintNodeId);
        } else {
            allValues = valueServiceImpl.getAlignedValues(
                    rangeNode.id(), folderId, fingerprintNodeId);
        }

        if (allValues.isEmpty()) {
            invocation.println("No data found for '" + rangeNodeName + "'");
            if (domainNode != null) {
                invocation.println("'" + rangeNodeName + "' and '" + domainNodeName
                        + "' share no uploads -- both nodes must extract from the same datasets.");
            }
            return CommandResult.FAILURE;
        }

        // Extract distinct fingerprints
        List<String> distinctFingerprints = new ArrayList<>();
        if (fingerprintNode != null) {
            Set<String> fpSet = new LinkedHashSet<>();
            for (JqValue row : allValues) {
                JqValue fp = row.getField(fingerprintNode.name());
                if (fp != null && !fp.isNull()) {
                    fpSet.add(fp.toJsonString());
                }
            }
            distinctFingerprints.addAll(fpSet);
        }

        // Promote to fields for rebuilds from the interactive loop
        this.allValues = allValues;
        this.rangeNode = rangeNode;
        this.domainNode = domainNode;
        this.fingerprintNode = fingerprintNode;
        this.nodeGroup = nodeGroup;
        this.distinctFingerprints = distinctFingerprints;
        this.shortLabels = shortLabels(distinctFingerprints);
        // Default to the first fingerprint; switch with 'f' while viewing.
        // Note: each rebuild autoscales the Y axis (library behavior).
        this.selectedFingerprints = distinctFingerprints.isEmpty()
                ? List.of()
                : List.of(distinctFingerprints.getFirst());

        // Build chart
        style = switch (styleName.toLowerCase()) {
            case "ascii" -> ChartStyle.ASCII;
            case "unicode" -> ChartStyle.UNICODE;
            default -> ChartStyle.BRAILLE;
        };

        Size termSize = invocation.getShell().size();
        chartWidth = termSize != null ? Math.max(40, termSize.getWidth() - 2) : 80;
        chartHeight = termSize != null ? Math.max(10, termSize.getHeight() - 6) : 20;

        LineChart chart = buildChart();
        if (chart == null) {
            invocation.println("No numeric data found for '" + rangeNodeName + "'");
            invocation.println("The Y axis needs numbers -- '" + rangeNodeName
                    + "' holds no numeric values in this folder.");
            return CommandResult.FAILURE;
        }
        if (markersEmpty) {
            invocation.println("No change detections target '" + rangeNodeName + "'");
        }

        // Render title and chart
        String title = rangeNodeName + " (" + folderName + ")";
        int padding = Math.max(0, (chartWidth - title.length()) / 2);
        chartTitle = " ".repeat(padding) + CYAN + title + CYAN;
        controlsLine = CYAN + "← → scroll  |  Home/End jump  |  f fingerprint  |  c changes  |  q quit" + CYAN;

        // Use alternate screen buffer for clean interactive display
        var shell = invocation.getShell();
        shell.enableAlternateBuffer();
        try {
            drawChart(shell, chart);
            interactiveLoop(invocation, shell, chart);
        } finally {
            shell.enableMainBuffer();
        }

        return CommandResult.SUCCESS;
    }

    /**
     * Build the chart for the current selection. Returns null when the
     * selection yields no numeric data. Package-visible for testing.
     */
    LineChart buildChart() {
        Function<JqValue, String> labeler = domainNode != null
                ? row -> shortDomainLabel(row.getField(domainNode.name()))
                : row -> {
                    JqValue uploaded = row.getField(ValueService.UPLOADED_FIELD);
                    return uploaded != null && !uploaded.isNull() ? uploaded.asText() : "";
                };
        List<SeriesData> seriesList = new ArrayList<>();
        if (selectedFingerprints.isEmpty()) {
            SeriesData series = buildSeries(rangeNodeName, allValues,
                    rangeNode.name(), labeler, null);
            if (series.series().size() > 0) {
                series.series().color(GREEN);
                seriesList.add(series);
            }
        } else {
            int colorIdx = 0;
            for (String fp : selectedFingerprints) {
                List<JqValue> filtered = filterByFingerprint(allValues,
                        fingerprintNode.name(), fp);
                SeriesData series = buildSeries(shortLabels.getOrDefault(fp, fp), filtered,
                        rangeNode.name(), labeler, fp);
                if (series.series().size() > 0) {
                    series.series().color(SERIES_COLORS[colorIdx % SERIES_COLORS.length]);
                    seriesList.add(series);
                    colorIdx++;
                }
            }
        }

        if (seriesList.isEmpty()) {
            return null;
        }

        // X ticks show row identities (domain values or upload timestamps)
        // from the longest series; positions beyond it render blank.
        List<String> xLabels = seriesList.stream()
                .map(SeriesData::xLabels)
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());
        Function<Double, String> xTickFormatter = x -> {
            int idx = (int) Math.round(x);
            return idx >= 0 && idx < xLabels.size() ? xLabels.get(idx) : "";
        };

        // Fix the Y range over all data so switching fingerprints never
        // rescales underneath the comparison.
        yMin = Double.POSITIVE_INFINITY;
        yMax = Double.NEGATIVE_INFINITY;
        for (JqValue row : allValues) {
            JqValue rangeVal = row.getField(rangeNode.name());
            Double y = rangeVal != null ? rangeVal.tryDouble() : null;
            if (y != null && !y.isNaN()) {
                yMin = Math.min(yMin, y);
                yMax = Math.max(yMax, y);
            }
        }
        if (yMin > yMax) {
            yMin = 0;
            yMax = 1;
        }
        double yPadding = Math.max((yMax - yMin) * 0.05, 0.001);

        // Compute viewport size based on data density vs available plot width.
        // Only enable scrolling when there are more data points than can be
        // comfortably displayed (~2 characters per point in braille mode).
        int maxPoints = seriesList.stream().mapToInt(s -> s.series().size()).max().orElse(0);
        int plotWidth = chartWidth - 10; // approximate Y-axis label + tick width
        int maxComfortable = Math.max(10, plotWidth / 2);
        if (maxPoints > maxComfortable) {
            viewportSize = maxComfortable;
        } else {
            viewportSize = 0; // all data fits — no scrolling needed
        }

        MarkerWork work = showChanges
                ? collectMatches(nodeGroup, seriesList, fingerprintNode)
                : new MarkerWork(List.of(), Set.of(), Map.of());
        markersEmpty = work.matches().isEmpty();
        LineChart.Builder chartBuilder = LineChart.builder()
                .width(chartWidth)
                .height(chartHeight)
                .style(style)
                .xLabel(domainNodeName != null ? "ordered by " + domainNodeName : "upload order")
                .yLabel(rangeNodeName)
                .xTickFormatter(xTickFormatter)
                .yTickFormatter(ChartCmd::formatYTick)
                .yMin(yMin - yPadding)
                .yMax(yMax + yPadding)
                .showLegend(seriesList.size() > 1 || !work.matches().isEmpty());
        if (viewportSize > 0) {
            chartBuilder.viewportSize(viewportSize);
        }
        LineChart chart = chartBuilder.build();

        for (SeriesData series : seriesList) {
            chart.addSeries(series.series());
        }

        // Add detection markers
        placeMarkers(chart, work, seriesList);
        return chart;
    }

    private Node resolveNode(H5mCommandInvocation invocation, String name, long groupId) {
        List<Node> found = nodeService.findNodeByFqdn(name, groupId);
        if (found.isEmpty()) {
            invocation.println("Node '" + name + "' not found");
            return null;
        }
        if (found.size() > 1) {
            invocation.println("'" + name + "' is ambiguous, matched multiple nodes");
            return null;
        }
        return found.getFirst();
    }

    /**
     * Walk the node tree to find a fingerprint node. Detection nodes
     * have a fingerprint node as their first source.
     */
    private Node findFingerprintNode(List<Node> nodes) {
        if (nodes == null) return null;
        for (Node n : nodes) {
            if (n.type() == NodeType.FINGERPRINT) return n;
            if (n.type() != null && n.type().isDetection() && n.sources() != null && !n.sources().isEmpty()) {
                Node firstSource = n.sources().getFirst();
                if (firstSource.type() == NodeType.FINGERPRINT) return firstSource;
            }
            // Recurse into sources
            Node found = findFingerprintNode(n.sources());
            if (found != null) return found;
        }
        return null;
    }

    /**
     * A plotted series plus the X tick label and root ID for each of its
     * points, aligned by series position; {@code fingerprintOrNull} is the
     * selected fingerprint (null = merged). Labels come from the row via
     * {@code labeler} (domain value or upload timestamp depending on mode).
     */
    private record SeriesData(DataSeries series, List<String> xLabels,
                              List<Long> rootIds, String fingerprintOrNull) {
    }

    private SeriesData buildSeries(String name, List<JqValue> values,
                                   String rangeKey,
                                   Function<JqValue, String> labeler,
                                   String fingerprintOrNull) {
        DataSeries series = new DataSeries(name);
        List<String> xLabels = new ArrayList<>();
        List<Long> rootIds = new ArrayList<>();
        for (JqValue row : values) {
            JqValue rangeVal = row.getField(rangeKey);
            Double y = rangeVal != null ? rangeVal.tryDouble() : null;
            if (y != null) {
                // Use sequential index as X coordinate. Domain values may be
                // timestamps or other non-numeric types -- tick labels carry
                // the identity, the X-axis label tells what the ordering is.
                series.add(xLabels.size(), y);
                xLabels.add(labeler.apply(row));
                rootIds.add(rootIdOf(row));
            }
        }
        return new SeriesData(series, xLabels, rootIds, fingerprintOrNull);
    }

    private static Long rootIdOf(JqValue row) {
        JqValue root = row.getField(ValueService.ROOT_FIELD);
        if (root == null || root.isNull()) {
            return null;
        }
        Double id = root.tryDouble();
        return id != null ? id.longValue() : null;
    }

    /**
     * Format a Y tick value compactly: integers without decimals, others
     * with up to 2 decimals and trailing zeros stripped. Pure function.
     */
    static String formatYTick(double value) {
        if (value == (long) value && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * Short display form of a domain value for X ticks (max ~12 chars).
     * Pure function.
     */
    static String shortDomainLabel(JqValue domainValue) {
        if (domainValue == null || domainValue.isNull()) {
            return "";
        }
        String text = domainValue.asText();
        if (text == null) {
            text = domainValue.toJsonString();
        }
        text = text.strip();
        if (text.length() > 12) {
            text = text.substring(0, 11) + "…";
        }
        return text;
    }

    private List<JqValue> filterByFingerprint(List<JqValue> values, String fpKey, String fpValue) {
        return values.stream()
                .filter(row -> {
                    JqValue fp = row.getField(fpKey);
                    if (fp == null || fp.isNull()) return false;
                    String fpStr = fp.toJsonString();
                    return fpStr.equals(fpValue) || fpStr.equals("\"" + fpValue + "\"")
                            || fpStr.contains(fpValue);
                })
                .toList();
    }

    /**
     * Place detection markers at data-point positions. Markers are matched to
     * series by fingerprint and positioned by root ID: each detection resolves
     * through its sources to the upload root, which maps to the X index of the
     * row from that root. This works in both domain and upload-order modes
     * (detection domainvalues are strings or arbitrary numerics that rarely
     * coincide with series positions, so placing by domainvalue misaligns).
     * Co-located markers render side by side via the library's own label
     * decluttering (aesh-charts #622), each pinned to its series line.
     */
    private record PlacedMarker(Node node, Value detValue, SeriesData series) {
    }

    private record MarkerWork(List<PlacedMarker> matches, Set<Long> sourceIds,
                              Map<Value, List<Long>> sourcesByDetection) {
    }

    private MarkerWork collectMatches(NodeGroup nodeGroup,
                                      List<SeriesData> seriesList,
                                      Node fingerprintNode) {
        // Walk the node tree to find detection nodes whose range source
        // matches the charted metric. Detection nodes have sources ordered
        // [fingerprint, groupBy, range, domain?]; sources[2] is the range.
        // Only same-metric detections are relevant — cross-metric detections
        // (e.g. avThroughput RD on a maxRss chart) produce misleading markers.
        List<Node> detectionNodes = new ArrayList<>();
        collectDetectionNodes(nodeGroup.sources(), detectionNodes, new HashSet<>());
        // The tree-embedded Node record may not carry full sources (the API
        // serialization can truncate deep trees). Re-fetch each detection node
        // via the transactional service to get the complete source list.
        detectionNodes.removeIf(det -> {
            if (det.id() == null) return true;
            List<Node> fetched = nodeService.findNodeByFqdn(det.name(), nodeGroup.id());
            Node full = fetched.stream().filter(n -> n.id().equals(det.id())).findFirst().orElse(null);
            if (full == null || full.sources() == null || full.sources().size() < 3) return true;
            return !rangeNodeName.equals(full.sources().get(2).name());
        });

        // Collect matching detections, gathering source IDs for one batched
        // root lookup in placeMarkers below
        List<PlacedMarker> matches = new ArrayList<>();
        Set<Long> sourceIds = new LinkedHashSet<>();
        Map<Value, List<Long>> sourcesByDetection = new IdentityHashMap<>();
        for (SeriesData series : seriesList) {
            for (Node det : detectionNodes) {
                for (Value detValue : valueService.getNodeValues(det.id())) {
                    if (detValue.data() == null) continue;
                    if (!matchesFingerprint(detValue, series.fingerprintOrNull(), fingerprintNode)) continue;
                    matches.add(new PlacedMarker(det, detValue, series));
                    List<Long> ids = sourceIds(detValue);
                    sourcesByDetection.put(detValue, ids);
                    sourceIds.addAll(ids);
                }
            }
        }
        return new MarkerWork(matches, sourceIds, sourcesByDetection);
    }

    private void placeMarkers(LineChart chart, MarkerWork work, List<SeriesData> seriesList) {
        if (work.matches().isEmpty()) {
            return;
        }
        Map<Long, Long> rootBySource = valueServiceImpl.rootIdsForValues(work.sourceIds());
        Map<SeriesData, Map<Long, Integer>> indexBySeries = new IdentityHashMap<>();
        for (PlacedMarker match : work.matches()) {
            indexBySeries.computeIfAbsent(match.series(), s -> rootIndexByRoot(s.rootIds()));
        }

        for (PlacedMarker match : work.matches()) {
            Map<Long, Integer> indexByRoot = indexBySeries.get(match.series());
            Integer x = null;
            for (Long sourceId : work.sourcesByDetection().get(match.detValue())) {
                Long rootId = rootBySource.get(sourceId);
                if (rootId != null && indexByRoot.containsKey(rootId)) {
                    x = indexByRoot.get(rootId);
                    break;
                }
            }
            if (x == null) continue;
            DataSeries series = match.series().series();
            double lineY = series.yAt(x);
            Marker marker = markerForDetection(match.node().type(), match.detValue().data(), x, lineY);
            if (marker != null) {
                chart.addMarker(marker);
            }
        }
    }

    private boolean matchesFingerprint(Value detValue, String fingerprintOrNull, Node fingerprintNode) {
        if (fingerprintOrNull == null || fingerprintNode == null) {
            return true;
        }
        JqValue detFp = detValue.data().getField("fingerprint");
        if (detFp == null || detFp.isNull()) {
            return true;
        }
        String detFpStr = detFp.toJsonString();
        return detFpStr.equals(fingerprintOrNull)
                || detFpStr.contains(fingerprintOrNull.replace("\"", ""));
    }

    private List<Long> sourceIds(Value detValue) {
        try {
            io.hyperfoil.tools.h5m.entity.ValueEntity entity =
                    valueServiceImpl.byId(detValue.id());
            if (entity == null || entity.sources == null) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (io.hyperfoil.tools.h5m.entity.ValueEntity source : entity.sources) {
                if (source != null && source.id != null) {
                    ids.add(source.id);
                }
            }
            return ids;
        } catch (Exception e) {
            Log.debug("Failed to resolve detection sources", e);
            return List.of();
        }
    }

    private Map<Long, Integer> rootIndexByRoot(List<Long> rootIds) {
        Map<Long, Integer> indexByRoot = new HashMap<>();
        for (int i = 0; i < rootIds.size(); i++) {
            Long rootId = rootIds.get(i);
            if (rootId != null) {
                indexByRoot.putIfAbsent(rootId, i);
            }
        }
        return indexByRoot;
    }

    private void collectDetectionNodes(List<Node> nodes, List<Node> result, Set<Long> seen) {
        if (nodes == null) return;
        for (Node n : nodes) {
            if (n.id() != null && !seen.add(n.id())) continue;
            if (n.type() != null && n.type().isDetection()) {
                result.add(n);
            }
            collectDetectionNodes(n.sources(), result, seen);
        }
    }

    /**
     * Build one marker for a detection at series position {@code x}, pinned to
     * {@code lineY} (the series value there). Pinning keeps markers on the
     * data line in both modes: detection payload values live in other
     * metrics' units (cross-metric detections) or window positions that don't
     * coincide with any plotted point. Co-located labels are decluttered by
     * the library itself (aesh-charts #622); the legend carries per-type
     * entries (aesh-charts #623).
     */
    private Marker markerForDetection(NodeType type, JqValue data, int x, double lineY) {
        String legendName;
        char symbol;
        String color;
        String label = markerLabel(type, data);
        if (label == null) {
            return null;
        }

        switch (type) {
            case FIXED_THRESHOLD -> {
                String direction = data.has("direction") ? data.getField("direction").asString("") : "";
                symbol = "BELOW".equals(direction) ? '▼' : '▲';
                color = RED;
                legendName = "FT";
            }
            case RELATIVE_DIFFERENCE -> {
                symbol = '▲';
                color = RED;
                legendName = "RD";
            }
            case STDDEV_ANOMALY -> {
                symbol = '●';
                color = YELLOW;
                legendName = "SD";
            }
            case EDIVISIVE -> {
                symbol = '◆';
                color = BLUE;
                legendName = "ED";
            }
            default -> { return null; }
        }

        return Marker.at(x, lineY).label(label).legendName(legendName).color(color).symbol(symbol);
    }

    /**
     * The label identifying one detection. Pure function.
     */
    static String markerLabel(NodeType type, JqValue data) {
        return switch (type) {
            case FIXED_THRESHOLD -> "FT";
            case RELATIVE_DIFFERENCE -> {
                double ratio = data.has("ratio") ? data.getField("ratio").asDouble(0) : 0;
                yield String.format("%.0f%%", ratio);
            }
            case STDDEV_ANOMALY -> "SD";
            case EDIVISIVE -> {
                double magnitude = data.has("magnitude") ? data.getField("magnitude").asDouble(0) : 0;
                yield String.format("ED:%.1f", magnitude);
            }
            default -> null;
        };
    }

    /**
     * Compute short display labels for fingerprint values by keeping only the
     * JSON keys whose values differ across the set (e.g. {@code 16=jvm,
     * 17=quarkus}). Falls back to truncated full JSON when nothing differs.
     * Pure function, unit tested.
     */
    static Map<String, String> shortLabels(List<String> fingerprints) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (fingerprints.isEmpty()) {
            return labels;
        }
        List<Map<String, String>> parsed = new ArrayList<>();
        Set<String> keys = new LinkedHashSet<>();
        for (String fp : fingerprints) {
            Map<String, String> fields = new LinkedHashMap<>();
            try {
                JqValue value = JqValues.parse(fp);
                for (String key : value.asMap().keySet()) {
                    JqValue field = value.getField(key);
                    String text = field == null || field.isNull() ? "" : field.asText();
                    fields.put(key, text);
                    keys.add(key);
                }
            } catch (Exception e) {
                // Non-object values get no short label below
            }
            parsed.add(fields);
        }
        Set<String> varying = new LinkedHashSet<>();
        for (String key : keys) {
            Set<String> seen = new LinkedHashSet<>();
            for (Map<String, String> fields : parsed) {
                seen.add(fields.getOrDefault(key, ""));
            }
            if (seen.size() > 1) {
                varying.add(key);
            }
        }
        // First pass: values only, deduplicated within each label
        // ({"16":"jvm","17":"jvm"} renders "jvm", not "jvm, jvm").
        Map<String, String> primary = new LinkedHashMap<>();
        for (int i = 0; i < fingerprints.size(); i++) {
            String fp = fingerprints.get(i);
            Map<String, String> fields = parsed.get(i);
            String label;
            if (varying.isEmpty() || fields.isEmpty()) {
                label = fp.length() > 60 ? fp.substring(0, 57) + "..." : fp;
            } else {
                Set<String> parts = new LinkedHashSet<>();
                for (String key : varying) {
                    String part = fields.getOrDefault(key, "");
                    if (!part.isEmpty()) {
                        parts.add(part);
                    }
                }
                label = parts.isEmpty() ? fp : String.join(", ", parts);
            }
            primary.put(fp, label);
        }
        // Second pass: labels claimed by more than one fingerprint fall back
        // to key=value form so every menu line stays unambiguous.
        Map<String, Long> claims = new LinkedHashMap<>();
        for (String label : primary.values()) {
            claims.merge(label, 1L, Long::sum);
        }
        for (int i = 0; i < fingerprints.size(); i++) {
            String fp = fingerprints.get(i);
            String label = primary.get(fp);
            if (claims.getOrDefault(label, 0L) > 1 && !varying.isEmpty() && !parsed.get(i).isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (String key : varying) {
                    String part = parsed.get(i).getOrDefault(key, "");
                    if (!part.isEmpty()) {
                        parts.add(key + "=" + part);
                    }
                }
                if (!parts.isEmpty()) {
                    label = String.join(", ", parts);
                }
            }
            labels.put(fp, label);
        }
        return labels;
    }

    /**
     * Parse a fingerprint menu selection (comma-separated 1-based indices,
     * with contains-match fallback) into fingerprint values.
     * Pure function, unit tested.
     */
    static List<String> parseFingerprintSelection(String input, List<String> available) {
        List<String> selected = new ArrayList<>();
        if (input == null) {
            return selected;
        }
        for (String part : input.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1;
                if (idx >= 0 && idx < available.size()
                        && !selected.contains(available.get(idx))) {
                    selected.add(available.get(idx));
                }
            } catch (NumberFormatException e) {
                // try matching by value
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                for (String fp : available) {
                    if (fp.contains(trimmed) && !selected.contains(fp)) {
                        selected.add(fp);
                        break;
                    }
                }
            }
            if (selected.size() >= MAX_FINGERPRINTS) {
                break;
            }
        }
        return selected;
    }

    private void switchFingerprints(H5mCommandInvocation invocation,
                                    org.aesh.command.shell.Shell shell) throws InterruptedException {
        if (distinctFingerprints.size() <= 1) {
            shell.write("\u001B[" + (chartHeight + 4) + ";1H");
            shell.write("\u001B[K");
            shell.write("Only one fingerprint available");
            return;
        }
        shell.enableMainBuffer();
        try {
            invocation.println("Available fingerprints:");
            for (int i = 0; i < distinctFingerprints.size(); i++) {
                invocation.println("  " + (i + 1) + ". "
                        + shortLabels.getOrDefault(distinctFingerprints.get(i),
                                distinctFingerprints.get(i)));
            }
            String input = invocation.getShell().readLine(
                    new Prompt("Select fingerprints (comma-separated, max " + MAX_FINGERPRINTS
                            + ") [1-" + distinctFingerprints.size() + "], empty keeps current: "));
            List<String> selected = parseFingerprintSelection(input, distinctFingerprints);
            if (selected.isEmpty()) {
                return;
            }
            selectedFingerprints = selected;
        } finally {
            shell.enableAlternateBuffer();
        }
    }

    private void drawChart(org.aesh.command.shell.Shell shell, LineChart chart) {
        shell.clear();
        shell.write("\u001B[H"); // cursor to top-left
        shell.writeln(chartTitle);
        shell.writeln(chart.render());
        shell.write(controlsLine);
    }

    private void interactiveLoop(H5mCommandInvocation invocation,
                                  org.aesh.command.shell.Shell shell,
                                  LineChart chart) throws InterruptedException {
        // Scroll by a fraction of the viewport size (in data points).
        // Use at least 2 to ensure each press produces a visible change.
        boolean running = true;
        while (running) {
            int scrollAmount = viewportSize > 0 ? Math.max(2, viewportSize / 3) : 0;
            KeyAction operation = invocation.input();
            if (operation == null) continue;

            if (Key.q.equalTo(operation) || Key.Q.equalTo(operation)) {
                running = false;
            } else if (Key.ESC.equalTo(operation)) {
                // ignore standalone ESC
            } else if (Key.f.equalTo(operation) || Key.F.equalTo(operation)) {
                switchFingerprints(invocation, shell);
                LineChart rebuilt = buildChart();
                if (rebuilt != null) {
                    chart = rebuilt;
                }
                drawChart(shell, chart);
            } else if (Key.c.equalTo(operation) || Key.C.equalTo(operation)) {
                showChanges = !showChanges;
                LineChart rebuilt = buildChart();
                if (rebuilt != null) {
                    chart = rebuilt;
                }
                drawChart(shell, chart);
            } else if (Key.LEFT.equalTo(operation) || Key.LEFT_2.equalTo(operation)
                    || Key.h.equalTo(operation)) {
                chart.scrollLeft(scrollAmount);
                drawChart(shell, chart);
            } else if (Key.RIGHT.equalTo(operation) || Key.RIGHT_2.equalTo(operation)
                    || Key.l.equalTo(operation)) {
                chart.scrollRight(scrollAmount);
                drawChart(shell, chart);
            } else if (Key.HOME.equalTo(operation) || Key.HOME_2.equalTo(operation)
                    || Key.HOME_3.equalTo(operation)) {
                chart.scrollToStart();
                drawChart(shell, chart);
            } else if (Key.END.equalTo(operation) || Key.END_2.equalTo(operation)
                    || Key.END_3.equalTo(operation)) {
                chart.scrollToEnd();
                drawChart(shell, chart);
            } else {
                // Debug: show unmatched keys at the bottom
                StringBuilder sb = new StringBuilder("Key: name=").append(operation.name())
                        .append(" len=").append(operation.length()).append(" codes=[");
                for (int i = 0; i < operation.length(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(operation.getCodePointAt(i));
                }
                sb.append("]");
                shell.write("\u001B[" + (chartHeight + 4) + ";1H"); // move to bottom
                shell.write("\u001B[K"); // clear line
                shell.write(sb.toString());
            }
        }
    }

    @Override
    public String getFolderName() { return folderName; }
}
