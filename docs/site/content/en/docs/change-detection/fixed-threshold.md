---
title: Fixed Threshold
weight: 10
description: Flag values that fall outside static min/max bounds.
draft: false
---

The **Fixed Threshold** detection node compares computed values against static boundaries you configure. Any value that violates a bound is recorded as a detected change.

It is the simplest change detection method — useful when you know the acceptable range for a metric in advance (e.g. "CPU usage must never exceed 90%", "throughput must stay above 100 MB/s").

## How It Works

![Fixed Threshold Bounds Chart](/images/change-detection/fixed-threshold-bounds.png)

1. For each uploaded data file, the Fixed Threshold node reads the values produced by its upstream nodes
2. It compares each value against the configured `min` and/or `max` bounds
3. If a bound is violated, the node writes a `Value` record describing the violation
4. If no bound is violated, no output value is produced

A violation `Value` contains:

```json
{
  "value": 250.1,
  "bound": 200.0,
  "direction": "above",
  "fingerprint": { "platform": "x86", "config": "default" }
}
```

| Field | Description |
|-------|-------------|
| `value` | The actual computed value that violated the bound |
| `bound` | The threshold that was crossed (`min` or `max`) |
| `direction` | `"above"` (exceeded max) or `"below"` (fell below min) |
| `fingerprint` | The fingerprint of the data group this value belongs to |

## Configuration

| Config Field | Type | Description |
|---|---|---|
| `min` | `Double` (nullable) | Lower bound; providing a value enables the check |
| `max` | `Double` (nullable) | Upper bound; providing a value enables the check |
| `minInclusive` | `Boolean` (default `true`) | Whether the min bound is inclusive |
| `maxInclusive` | `Boolean` (default `true`) | Whether the max bound is inclusive |

### Inclusive vs Exclusive

- `minInclusive = true` (default): violation triggers when `value < min`
- `minInclusive = false`: violation triggers when `value <= min`
- `maxInclusive = true` (default): violation triggers when `value > max`
- `maxInclusive = false`: violation triggers when `value >= max`

Set `minInclusive` or `maxInclusive` to `false` when hitting the boundary exactly should itself be considered a violation.

## Adding a Fixed Threshold Node

### CLI

```bash
h5m add fixedthreshold <name> to <folder> range <metric-node> by root fingerprint <fingerprint-node> min <value> max <value>
```

Example — alert when throughput falls below 100 or exceeds 5000:

```bash
h5m add fixedthreshold throughput-check to my-benchmarks \
  range throughput \
  by root fingerprint benchmarkName \
  min 100 max 5000
```

`min` and `max` are both optional. Provide only one to check a single boundary:

```bash
# Only check the upper bound
h5m add fixedthreshold cpu-ceiling to my-benchmarks \
  range cpu_percent \
  by root fingerprint host \
  max 90
```

### REST API

```bash
curl -X POST \
  "http://localhost:8080/api/node/configured?name=throughput-check&groupId=<groupId>&type=ft" \
  -H "Content-Type: application/json" \
  -d '{
    "min": 100.0,
    "max": 5000.0,
    "minInclusive": true,
    "maxInclusive": true
  }'
```

## Querying Detected Violations

Detected violations are stored as `Value` records with node discriminator type `ft`. Query them using the standard Value API:

```bash
# List violations for the threshold node
curl http://localhost:8080/api/value/node/<thresholdNodeId>
```

Example response:

```json
[
  {
    "id": 99,
    "data": {
      "value": 85.2,
      "bound": 100.0,
      "direction": "below",
      "fingerprint": { "host": "ci-runner-1" }
    },
    "node": { "id": 77, "name": "throughput-check", "type": "ft" },
    "folder": { "id": 1, "name": "my-benchmarks" }
  }
]
```

An empty array means no violations were detected for that node.

## Recalculation Behaviour

Fixed Threshold nodes are deterministic. If you change the `min`/`max` bounds, recalculating the folder re-evaluates all existing uploads against the new thresholds and replaces the stored violation values:

```bash
h5m recalculate my-benchmarks
```

## Relationship to Other Nodes

A Fixed Threshold node sits downstream in the DAG — it needs at least an extraction node (jq/js/jsonata) to provide the numeric values it checks:

```
[raw JSON upload]
      │
      ▼
[throughput]          jq: .metrics.throughput
      │
      ▼
[throughput-check]    fixed threshold: min=100, max=5000
```
