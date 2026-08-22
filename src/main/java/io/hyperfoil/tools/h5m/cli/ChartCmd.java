package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.jjq.value.JqValue;
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
    private String chartTitle;
    private String controlsLine;
    private int chartHeight;
    private int viewportSize;

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

    @Argument(description = "range node name (Y axis values)", required = true)
    String rangeNodeName;

    @Option(name = "domain", acceptNameWithoutDashes = true, description = "domain node name (X axis ordering)",
            completer = NodeNameCompleter.class, required = true)
    String domainNodeName;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    public String folderName;

    @Option(name = "fingerprint", acceptNameWithoutDashes = true,
            description = "fingerprint values (comma-separated for multi-select, max 3)")
    String fingerprintArg;

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

        // Resolve domain node
        Node domainNode = resolveNode(invocation, domainNodeName, nodeGroup.id());
        if (domainNode == null) return CommandResult.FAILURE;

        // Find fingerprint nodes by walking the node tree
        // Detection nodes have sources: [fingerprint, groupBy, range, domain?]
        Node fingerprintNode = findFingerprintNode(nodeGroup.sources());

        // Get chart data using lightweight query (pairs range + domain by shared root)
        // The folder_id filter is critical: 0.3ms vs 6.4s without it.
        List<JqValue> allValues = valueServiceImpl.getChartData(
                rangeNode.id(), domainNode.id(), folderId,
                fingerprintNode != null ? fingerprintNode.id() : null);

        if (allValues.isEmpty()) {
            invocation.println("No data found for '" + rangeNodeName + "'");
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

        // Select fingerprints
        List<String> selectedFingerprints = selectFingerprints(invocation, distinctFingerprints);
        if (selectedFingerprints == null) return CommandResult.SUCCESS;

        // Build chart
        ChartStyle style = switch (styleName.toLowerCase()) {
            case "ascii" -> ChartStyle.ASCII;
            case "unicode" -> ChartStyle.UNICODE;
            default -> ChartStyle.BRAILLE;
        };

        Size termSize = invocation.getShell().size();
        int chartWidth = termSize != null ? Math.max(40, termSize.getWidth() - 2) : 80;
        chartHeight = termSize != null ? Math.max(10, termSize.getHeight() - 6) : 20;

        // Build data series first to determine data ranges
        List<DataSeries> seriesList = new ArrayList<>();
        if (selectedFingerprints.isEmpty()) {
            DataSeries series = buildSeries(rangeNodeName, allValues,
                    rangeNode.name(), domainNode.name());
            if (series.size() > 0) {
                series.color(GREEN);
                seriesList.add(series);
            }
        } else {
            int colorIdx = 0;
            for (String fp : selectedFingerprints) {
                List<JqValue> filtered = filterByFingerprint(allValues,
                        fingerprintNode.name(), fp);
                DataSeries series = buildSeries(fp, filtered,
                        rangeNode.name(), domainNode.name());
                if (series.size() > 0) {
                    series.color(SERIES_COLORS[colorIdx % SERIES_COLORS.length]);
                    seriesList.add(series);
                    colorIdx++;
                }
            }
        }

        if (seriesList.isEmpty()) {
            invocation.println("No numeric data found for '" + rangeNodeName + "'");
            return CommandResult.FAILURE;
        }

        // Compute viewport and Y range from actual data
        int maxPoints = seriesList.stream().mapToInt(DataSeries::size).max().orElse(0);
        viewportSize = Math.max(5, maxPoints / 2);

        // Fix Y-axis range to the full data extent so it doesn't rescale
        // when scrolling. This keeps the visual scale consistent across
        // all scroll positions for reliable comparison.
        double yMin = seriesList.stream().mapToDouble(DataSeries::yMin).min().orElse(0);
        double yMax = seriesList.stream().mapToDouble(DataSeries::yMax).max().orElse(1);
        double yPadding = Math.max((yMax - yMin) * 0.05, 0.001);

        LineChart chart = LineChart.builder()
                .width(chartWidth)
                .height(chartHeight)
                .style(style)
                .xLabel("ordered by " + domainNodeName)
                .yLabel(rangeNodeName)
                .showLegend(selectedFingerprints.size() > 1)
                .viewportSize(viewportSize)
                .yRange(yMin - yPadding, yMax + yPadding)
                .build();

        for (DataSeries series : seriesList) {
            chart.addSeries(series);
        }

        // Add detection markers
        addDetectionMarkers(chart, nodeGroup, selectedFingerprints,
                fingerprintNode, domainNode);

        // Render title and chart
        String title = rangeNodeName + " (" + folderName + ")";
        int padding = Math.max(0, (chartWidth - title.length()) / 2);
        chartTitle = " ".repeat(padding) + CYAN + title + CYAN;
        controlsLine = CYAN + "← → scroll  |  Home/End jump  |  q quit" + CYAN;

        // Use alternate screen buffer for clean interactive display
        var shell = invocation.getShell();
        shell.enableAlternateBuffer();
        try {
            drawChart(shell, chart);
            interactiveLoop(invocation, shell, chart, chartWidth);
        } finally {
            shell.enableMainBuffer();
        }

        return CommandResult.SUCCESS;
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

    private DataSeries buildSeries(String name, List<JqValue> values,
                                    String rangeKey, String domainKey) {
        DataSeries series = new DataSeries(name);
        for (int i = 0; i < values.size(); i++) {
            JqValue row = values.get(i);
            JqValue rangeVal = row.getField(rangeKey);
            Double y = rangeVal != null ? rangeVal.tryDouble() : null;
            if (y != null) {
                // Use sequential index as X coordinate. Domain values may be
                // timestamps or other non-numeric types -- the X-axis label
                // tells the user what the ordering represents.
                series.add(i, y);
            }
        }
        return series;
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

    private void addDetectionMarkers(LineChart chart, NodeGroup nodeGroup,
                                      List<String> selectedFingerprints,
                                      Node fingerprintNode, Node domainNode) {
        // Walk the node tree to find detection nodes
        List<Node> detectionNodes = new ArrayList<>();
        collectDetectionNodes(nodeGroup.sources(), detectionNodes, new HashSet<>());

        for (Node det : detectionNodes) {
            List<Value> detValues = valueService.getNodeValues(det.id());
            for (Value detValue : detValues) {
                if (detValue.data() == null) continue;

                // Filter by selected fingerprints
                if (!selectedFingerprints.isEmpty() && fingerprintNode != null) {
                    JqValue detFp = detValue.data().getField("fingerprint");
                    if (detFp != null && !detFp.isNull()) {
                        String detFpStr = detFp.toJsonString();
                        boolean matches = selectedFingerprints.stream()
                                .anyMatch(fp -> detFpStr.equals(fp)
                                        || detFpStr.contains(fp.replace("\"", "")));
                        if (!matches) continue;
                    }
                }

                addMarkerForDetection(chart, det.type(), detValue.data());
            }
        }
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

    private void addMarkerForDetection(LineChart chart, NodeType type, JqValue data) {
        JqValue domainVal = data.getField("domainvalue");
        Double x = domainVal != null && !domainVal.isNull() ? domainVal.tryDouble() : null;
        if (x == null) return;

        Double y;
        String label;
        char symbol;
        String color;

        switch (type) {
            case FIXED_THRESHOLD -> {
                y = data.has("value") ? data.getField("value").tryDouble() : null;
                String direction = data.has("direction") ? data.getField("direction").asString("") : "";
                symbol = "BELOW".equals(direction) ? '▼' : '▲';
                color = RED;
                label = "FT";
            }
            case RELATIVE_DIFFERENCE -> {
                y = data.has("last") ? data.getField("last").tryDouble() : null;
                double ratio = data.has("ratio") ? data.getField("ratio").asDouble(0) : 0;
                symbol = '▲';
                color = RED;
                label = String.format("%.0f%%", ratio);
            }
            case STDDEV_ANOMALY -> {
                y = data.has("value") ? data.getField("value").tryDouble() : null;
                symbol = '●';
                color = YELLOW;
                label = "SD";
            }
            case EDIVISIVE -> {
                y = data.has("meanAfter") ? data.getField("meanAfter").tryDouble() : null;
                double magnitude = data.has("magnitude") ? data.getField("magnitude").asDouble(0) : 0;
                symbol = '◆';
                color = BLUE;
                label = String.format("ED:%.1f", magnitude);
            }
            default -> { return; }
        }

        if (y != null) {
            chart.addMarker(Marker.at(x, y).label(label).color(color).symbol(symbol));
        }
    }

    private List<String> selectFingerprints(H5mCommandInvocation invocation,
                                             List<String> available) throws InterruptedException {
        if (fingerprintArg != null && !fingerprintArg.isEmpty()) {
            List<String> selected = List.of(fingerprintArg.split(","));
            if (selected.size() > MAX_FINGERPRINTS) {
                invocation.println("Maximum " + MAX_FINGERPRINTS + " fingerprints allowed, using first " + MAX_FINGERPRINTS);
                selected = selected.subList(0, MAX_FINGERPRINTS);
            }
            return selected;
        }

        if (available.size() <= 1) {
            return available;
        }

        // Interactive selection
        invocation.println("Available fingerprints:");
        for (int i = 0; i < available.size(); i++) {
            invocation.println("  " + (i + 1) + ". " + available.get(i));
        }
        String input = invocation.getShell().readLine(
                new Prompt("Select fingerprints (comma-separated, max " + MAX_FINGERPRINTS
                        + ") [1-" + available.size() + "]: "));

        if (input == null || input.trim().isEmpty()) return null;

        List<String> selected = new ArrayList<>();
        for (String part : input.split(",")) {
            try {
                int idx = Integer.parseInt(part.trim()) - 1;
                if (idx >= 0 && idx < available.size()) {
                    selected.add(available.get(idx));
                }
            } catch (NumberFormatException e) {
                // try matching by value
                String trimmed = part.trim();
                for (String fp : available) {
                    if (fp.contains(trimmed)) {
                        selected.add(fp);
                        break;
                    }
                }
            }
        }

        if (selected.isEmpty()) {
            invocation.println("No valid fingerprints selected");
            return null;
        }
        if (selected.size() > MAX_FINGERPRINTS) {
            selected = selected.subList(0, MAX_FINGERPRINTS);
        }
        return selected;
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
                                  LineChart chart, int chartWidth) throws InterruptedException {
        // Scroll by a fraction of the viewport size (in data points).
        // Use at least 2 to ensure each press produces a visible change.
        int scrollAmount = Math.max(2, viewportSize / 3);
        boolean running = true;
        while (running) {
            KeyAction operation = invocation.input();
            if (operation == null) continue;

            if (Key.q.equalTo(operation) || Key.Q.equalTo(operation)) {
                running = false;
            } else if (Key.ESC.equalTo(operation)) {
                // ignore standalone ESC
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
