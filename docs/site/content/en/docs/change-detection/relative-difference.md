---
title: Relative Difference
weight: 20
description: Flag values that deviate from a historical baseline by more than a configured percentage.
draft: false
---

The **Relative Difference** detection node compares the most recent uploaded value against a historical baseline and flags it if the change exceeds a configured percentage threshold. Unlike Fixed Threshold — which checks against a static bound — Relative Difference adapts to the natural level of your metric over time.

It is well-suited for catching regressions like "throughput dropped by more than 20% compared to last week" without requiring you to know the exact absolute numbers in advance.

## How It Works

![Relative Difference Sliding Window](/images/change-detection/relative-difference-timeseries.png)

1. For each new upload, the node identifies the relevant historical baseline value (the previous result for the same fingerprint group)
2. It computes the ratio: `(last - previous) / previous × 100`
3. If the absolute ratio exceeds the configured threshold, a violation `Value` is recorded
4. Positive ratios indicate improvement; negative ratios indicate regression

A violation `Value` contains:

```json
{
  "previous": 1000.0,
  "last": 750.0,
  "value": 750.0,
  "ratio": -25.0,
  "domainvalue": { "platform": "x86", "config": "default" }
}
```

| Field | Description |
|-------|-------------|
| `previous` | The baseline mean computed from the preceding window |
| `last` | The test value computed from the current window |
| `value` | Same as `last` — the current value |
| `ratio` | Percentage change: `(last / previous - 1) × 100` |
| `domainvalue` | The domain value associated with this detection |

A `ratio` of `-25.0` means a 25% drop — the value decreased from 1000 to 750.

## Window Configuration

The Relative Difference node does not simply compare the latest value against the single immediately preceding one. It uses two configurable windows to compute means across a range of uploads:

- **`window`** — the number of most-recent (test) values to evaluate against the baseline
- **`minPrevious`** — the minimum number of prior baseline values required before a comparison can be made. The effective baseline size used is `max(window, minPrevious)`

For each upload the algorithm fetches two sets of domain values sorted by the upload order:

- **Preceding values** — uploads *before* the current one, used to build the baseline mean
- **Following values** — uploads *after* the current one, included to evaluate the full window context

The baseline mean is computed from the first `minPrevious` entries. The test value (mean, min, or max across the window) is computed from the remaining `window` entries. The ratio is then `test / baselineMean`.

### Change Point Localisation

When a violation is detected, the algorithm walks backward through the window to find the exact upload where the value first crossed the baseline mean. This pinpoints the change point rather than flagging the last value in the window.

### Stale Detection Cleanup

After each recalculation pass, previously persisted violation records are checked against the current window scope. If a prior violation's domain value falls within the scope but no current calculation produces a matching violation, the old record is **deleted**. This keeps violation history accurate when thresholds or window sizes are changed and the folder is recalculated.

## Insufficient Data

If there are fewer accumulated uploads than `window + minPrevious`, the algorithm cannot calculate the means and **will not alert**. The change detection silently skips that upload until enough data has accumulated.

![Insufficient Data](/images/change-detection/relative-difference-insufficient-data.png)

In this case the change detection will wait until there are sufficient results to calculate the mean of the two windows. No false positives are raised during the warm-up period — the node simply produces no output until the minimum sample count is met.

## Configuration

| Config Field | Type | Default | Description |
|---|---|---|---|
| `threshold` | `Double` | `0.2` | Fractional change to trigger a violation (e.g. `0.2` = 20%) |
| `window` | `Integer` | `1` | Number of current (test) values to evaluate |
| `minPrevious` | `Integer` | `5` | Minimum number of prior baseline values required |
| `filter` | `String` | `"mean"` | How to reduce the window to a single value: `"mean"`, `"min"`, or `"max"` |

## Adding a Relative Difference Node

### CLI

```bash
h5m add relativedifference <name> to <folder> range <metric-node> by root fingerprint <fingerprint-node> threshold <value>
```

Example — flag if throughput drops more than 20%:

```bash
h5m add relativedifference throughput-rd to my-benchmarks \
  range throughput \
  by root fingerprint buildConfig \
  threshold 0.2
```

### REST API

```bash
curl -X POST \
  "http://localhost:8080/api/node/configured?name=throughput-rd&groupId=<groupId>&type=rd" \
  -H "Content-Type: application/json" \
  -d '{
    "threshold": 0.2,
    "window": 1,
    "minPrevious": 5,
    "filter": "mean"
  }'
```

## Querying Detected Regressions

Relative Difference violations are stored as `Value` records with discriminator type `rd`. Query them using the standard Value API:

```bash
curl http://localhost:8080/api/value/node/<rdNodeId>
```

Example response showing a 25% throughput drop:

```json
[
  {
    "id": 103,
    "data": {
      "previous": 1000.0,
      "last": 750.0,
      "value": 750.0,
      "ratio": -25.0,
      "domainvalue": { "buildConfig": "release" }
    },
    "node": { "id": 88, "name": "throughput-rd", "type": "rd" },
    "folder": { "id": 1, "name": "my-benchmarks" }
  }
]
```

An empty array means no violations exceeded the threshold.

## Fingerprinting

Both Fixed Threshold and Relative Difference nodes use **fingerprints** to group comparable runs. A fingerprint is a set of key-value pairs that identify "same environment" comparisons — for example `{platform: "x86", config: "release"}`. The Relative Difference node only compares runs that share the same fingerprint, so a result from a `debug` build does not incorrectly flag against a `release` baseline.

See the Concepts section for details on how fingerprinting works within the DAG.

## DAG Position

A Relative Difference node sits downstream of both an extraction node and a fingerprint node:

```
[raw JSON upload]
      │
      ├──────────────────┐
      ▼                  ▼
[throughput]         [buildConfig]
jq: .throughput      jq: .build.config
      │                  │
      └────────┬──────────┘
               ▼
        [throughput-rd]
        relative difference
        threshold: 20%
```

## Comparison to Fixed Threshold

| | Fixed Threshold | Relative Difference |
|-|----------------|---------------------|
| **Bound type** | Static (`min`/`max` values) | Dynamic (fraction of baseline mean) |
| **Best for** | Hard limits (SLAs, safety bounds) | Regression detection across releases |
| **Requires history** | No | Yes — needs `window + minPrevious` uploads |
| **Config fields** | `min`, `max`, `minInclusive`, `maxInclusive` | `threshold`, `window`, `minPrevious`, `filter` |
| **Output `ratio` field** | No | Yes |
| **Node discriminator** | `ft` | `rd` |
