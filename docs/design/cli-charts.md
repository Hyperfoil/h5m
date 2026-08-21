# CLI Charts: Interactive Terminal Visualization for h5m

## Overview

Add interactive terminal-based charts to the h5m CLI for visualizing
performance data and change detections. Uses `org.aesh:aesh-charts:3.17-dev`
which provides line charts, time series charts, bar charts, sparklines,
and multi-plot layouts with braille sub-cell rendering.

## Dependency

```xml
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>aesh-charts</artifactId>
    <version>3.17-dev</version>
    <exclusions>
        <exclusion>
            <groupId>org.aesh</groupId>
            <artifactId>aesh</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

The transitive `aesh` dependency is excluded to avoid conflicting with
the `aesh:3.16.6` provided by Quarkus 3.38.1.

## Phase 1: `folder chart` Command (implemented)

Interactive line chart of node values over domain values with change
detection markers overlaid.

### Usage

```
folder chart <rangeNode> --domain <domainNode> [--fingerprint <key=value,...>] [--style braille|unicode|ascii]
```

If `--fingerprint` is omitted and multiple fingerprints exist, present
a selection menu. Supports multi-select (up to 3 fingerprints) to
overlay series for comparison (e.g., cpu=4 vs cpu=8).

### Data Flow

1. Resolve range node and domain node by name in current folder
2. Query chart data via `ValueService.getChartData()` -- a lightweight
   method that pairs range + domain values by shared root ancestor
   using separate recursive CTEs joined in Java (~480ms vs 22s with
   the full `getGroupedValues` CTE). Uses `DISTINCT ON (root_id)` in
   PostgreSQL (`GROUP BY` in SQLite) to deduplicate CTE fan-out through
   shared DAG edges.
3. Extract distinct fingerprint values from the results
4. If multiple fingerprints: present numbered selection menu
5. Build data series per fingerprint, using sequential index as X
   coordinate (domain values may be timestamps or other non-numeric
   types -- the X-axis label indicates the ordering)
6. Fetch change detection values from all detection nodes found by
   walking the node tree (FixedThreshold, RelativeDifference,
   StdDevAnomaly, EDivisive)
7. Build chart:
   - One `DataSeries` per selected fingerprint (up to 3), each with a
     distinct color from the palette
   - `Marker` for each change detection point, matched to its fingerprint
     series
   - `HorizontalLine` for FixedThreshold min/max bounds
   - `Legend` showing fingerprint-to-color mapping when multiple
     fingerprints are selected
   - Fixed Y-axis range via `yRange()` computed from the full dataset,
     preventing rescaling during viewport scrolling
   - Viewport size set to half the data points to enable scrolling
8. Auto-detect terminal size via `Shell.size()` for chart dimensions
9. Default to braille style, configurable via `--style`
10. Render in alternate screen buffer (`shell.enableAlternateBuffer()`)
    for clean full-screen display
11. Enter interactive mode with `commandInvocation.input()` for key
    capture (same pattern as aesh-extensions More/Less commands)

### Chart Display

The chart is rendered in the terminal's alternate screen buffer with:
- Centered title showing "rangeNodeName (folderName)"
- Braille-rendered line chart with Y-axis (vertical label) and X-axis
  ("ordered by domainNodeName")
- Controls line at the bottom showing available key bindings
- On exit (`q`), the alternate buffer is closed and the original
  terminal content is restored

### Marker Types

| Detection Type | Symbol | Color |
|---------------|--------|-------|
| Relative Difference | `▲` | Red |
| Fixed Threshold (below min) | `▼` | Red |
| Fixed Threshold (above max) | `▲` | Red |
| StdDev Anomaly | `●` | Yellow |
| E-Divisive | `◆` | Blue |

### Interactive Controls

| Key | Action |
|-----|--------|
| `←` / `h` | Scroll left |
| `→` / `l` | Scroll right |
| `Home` | Jump to start |
| `End` | Jump to end |
| `q` | Exit chart |

Note: `Esc` is not used for exit because it's the start of arrow key
escape sequences in terminal input.

### Fingerprint Selection UX

When multiple fingerprints exist, multi-select is supported (up to 3
for readability -- each fingerprint becomes a separate colored series):
```
Available fingerprints:
  1. cpu=4
  2. cpu=8
  3. cpu=16
  4. cpu=32
Select fingerprints (comma-separated, max 3) [1-4]: 1,2,3
```

Each selected fingerprint gets its own `DataSeries` with a distinct
color from the palette. The `Legend` shows fingerprint-to-color mapping.
Change detection markers are per-fingerprint (matched to their series).

With `--fingerprint cpu=4,cpu=8` on the command line, the selection
menu is skipped.

### Known Limitations (Phase 1)

- Query performance: ~480ms warm due to recursive CTE fan-out through
  shared DAG edges (19,600 intermediate rows for 100 values). The
  `DISTINCT ON` deduplicates but the CTE still traverses all paths.
  A `root_id` column on ValueEntity was considered but cannot eliminate
  all recursive CTEs due to shared edges in the DAG.
- `folder_id` is not propagated to child values (only root values have
  it set), which prevents folder-scoped query filtering on non-root
  nodes.
- X-axis shows sequential indices rather than actual domain values
  (pending aesh-charts custom tick formatter support, issue #589).
- Y-axis tick precision may be insufficient for narrow data ranges
  (pending aesh-charts fix, issue #593).
- Viewport scrolling may show inconsistent data point counts at some
  positions (pending aesh-charts fix, issue #597).

## Phase 2: Multi-Plot Comparison

### Usage

```
folder chart throughput,latency --domain startTime --fingerprint "3.12"
```

Uses `MultiPlot` to stack multiple range nodes vertically with shared
X-axis, synchronized scrolling, separate Y-axis per chart, and detection
markers for each range node.

## Phase 3: Sparklines in Table Output

### Usage

```
folder values --as table --sparkline
```

Each numeric column in the table gets a compact inline sparkline showing
the value trend.

## Phase 4: Bar Chart for Fingerprint Comparison

### Usage

```
folder chart --bar <rangeNode> --latest
```

Bar chart comparing the latest value of a range node across all
fingerprints. Useful for "which configuration is fastest?" views.

## Design Inspiration: Horreum Web UI

Horreum's web charts (using Recharts) provide design precedent:

- **Fingerprint as filter and visual grouping** -- Horreum supports
  showing multiple fingerprints as overlaid series for comparison
  (e.g., cpu=4 vs cpu=8). h5m supports multi-select (up to 3) via
  numbered selection menu or `--fingerprint` flag.
- **Variables grouped into panels** -- variables sharing the same group
  are plotted together on one chart (h5m Phase 2: MultiPlot with
  comma-separated nodes).
- **Change points are overlay markers** -- rendered on top of data lines
  as `ReferenceDot` (confirmed=green, unconfirmed=red) or
  `ReferenceLine` (vertical line when no matching datapoint). In h5m:
  `Marker` with type-specific symbols and colors.
- **Threshold lines** -- Horreum doesn't display these (thresholds are
  config, not visual). h5m adds `HorizontalLine` for FixedThreshold
  min/max bounds.
- **Time window is sliding** -- Horreum uses end-time + timespan presets
  (1 week, 1 month, etc.). h5m uses interactive arrow-key scrolling via
  the `LineChart` viewport with alternate screen buffer.
- **Clicking change markers navigates to detail** -- In Horreum, clicking
  a change dot scrolls to the change table entry. In h5m CLI, this could
  print change details below the chart on marker selection (future
  enhancement).

## Open Questions

1. Should `folder chart` support a `--changes` flag to toggle change
   markers, or always show them when detection nodes exist?
2. How should we handle very long series (>1000 data points)?
   Downsample, or rely on viewport scrolling?
3. Should the fingerprint selector support cascading filters (like
   Horreum's `LabelsSelect` dropdowns) in later phases?
4. Should we support exporting the chart data (CSV) alongside the
   visual chart?
