---
title: Dependencies
weight: 60
description: 'The {nodeName}:expression syntax for wiring Node outputs into downstream Node inputs.'
draft: false
---

Node dependencies are how the DAG is wired together. A node declares its upstream inputs inline within its expression using a special prefix syntax.

## Syntax

```
{nodeName}:expression
```

- `{nodeName}` — the name of the upstream node whose output you want as input
- `:expression` — the jq, js, or jsonata expression applied to that upstream value

## Basic Example

Suppose you have a node `throughput` that extracts a raw byte-per-second count:

```bash
h5m add jq to my-benchmarks throughput .metrics.throughput
# Produces: 52428800
```

Now add a node that converts it to MB/s, depending on `throughput`:

```bash
h5m add jq to my-benchmarks throughput_mbs "{throughput}:. / 1000000"
# {throughput} → receives 52428800 as input
# .            → the jq filter applied to that input
# Produces:    52.4288
```

The `{throughput}` prefix tells h5m: _before running this node, fetch the output of the `throughput` node for the same upload, and use it as this node's input._

## Multiple Dependencies

A node can depend on multiple upstream nodes. List them in sequence:

```bash
h5m add jq to my-benchmarks regression \
  "{throughput_mbs}:. < {baseline_mbs}:. * 0.95"
```

h5m resolves both `throughput_mbs` and `baseline_mbs` before running this node. Both must be nodes in the same NodeGroup.

## Dependency on Raw JSON

A node with no `{...}` prefix receives the raw uploaded JSON as its input:

```bash
# No dependency prefix — reads from the raw upload
h5m add jq to my-benchmarks latency .metrics.latency.p99_ms
```

## Chaining

Dependencies can be chained to any depth as long as the graph remains acyclic:

```
raw JSON
  │
  ▼
[throughput]          .metrics.throughput
  │
  ▼
[throughput_mbs]      {throughput}:. / 1000000
  │
  ▼
[regression_flag]     {throughput_mbs}:. < 40.0
```

h5m topologically sorts all nodes in the graph and executes them in the correct order — you do not need to declare the order explicitly.

## Resolution

At upload time, h5m:

1. Parses each node's expression to find `{nodeName}` references
2. Builds the dependency graph
3. Sorts nodes topologically
4. Executes each node, substituting `{nodeName}` with the already-computed `Value.data` for that node and that upload

If an upstream node produces `null` or fails, the downstream node receives `null` as its input.

## Naming Rules

Node names used in `{...}` must match exactly (case-sensitive) the name given when the node was created. Names must be unique within a NodeGroup.

## Invalid: Cycles

A node cannot depend on itself or on a node that transitively depends on it. The following would be rejected:

```
nodeA depends on nodeB
nodeB depends on nodeA   ← cycle — not allowed
```

## Viewing the Graph

Inspect the dependency structure of a folder:

```bash
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

This returns the full node graph including all declared dependencies.
