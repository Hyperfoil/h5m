---
title: Nodes
weight: 30
description: Computation nodes — expression languages (jq, JavaScript, JSONata), aggregation, detection, and special node types.
draft: false
---

A **Node** is the fundamental computation unit in h5m. It takes one or more inputs — either the raw uploaded JSON or the outputs of upstream nodes — applies an expression, and produces one or more **Values**.

Every node has:

- A **name** — unique within its NodeGroup
- A **type** — `jq`, `js`, `nata` (JSONata), `sql`, `sql-all`, `split`, `fp`, `ft`, `rd`, `ed`, or `user`
- An **expression** — the filter or function to apply
- **Dependencies** (optional) — references to other nodes' outputs, declared inline in the expression

## Node Types

### jq

Uses [jq](https://jqlang.org/) syntax, evaluated in-process via [jackson-jq](https://github.com/eiiches/jackson-jq).

jq is a Turing-complete JSON filter language with strong community support and broad tooling (editors, playgrounds, AI assistants). It is the recommended default for most extraction and transformation tasks.

```bash
# Extract a nested value
h5m add jq to my-benchmarks throughput .metrics.throughput

# Compute a ratio
h5m add jq to my-benchmarks error_rate ".metrics.errors / .metrics.total_requests"

# Filter an array
h5m add jq to my-benchmarks slow_endpoints "[.endpoints[] | select(.p99_ms > 100)]"
```

**When to use:** Extracting fields, filtering arrays, arithmetic, string manipulation, most data transformation needs.

### js

Uses JavaScript (ECMAScript), executed via [GraalVM Polyglot](https://www.graalvm.org/reference-manual/js/).

The expression must be a **function** — either an arrow function or a named function. The parameter names must match the names of existing upstream nodes; h5m passes each source node's output to the corresponding parameter automatically.

```bash
# First add source nodes
h5m add jq to my-benchmarks cpu_score .results.cpu
h5m add jq to my-benchmarks mem_score .results.memory

# JS node: parameter names match upstream node names
h5m add js to my-benchmarks efficiency "(cpu_score, mem_score) => cpu_score / mem_score"
```

**When to use:** Multi-source arithmetic, complex logic, or computations that jq makes awkward.

### jsonata

Uses [JSONata](https://jsonata.org/), a declarative JSON query and transformation language with built-in aggregation functions.

```bash
# Sum all values in an array
h5m add jsonata to my-benchmarks total_ops "$sum(metrics.operations)"

# Average with a built-in function
h5m add jsonata to my-benchmarks avg_latency "$average(runs.latency_ms)"
```

**When to use:** Aggregations, when JSONata's built-in functions (`$sum`, `$average`, `$count`, etc.) match your need.

## Adding Nodes

### CLI

```bash
# jq node
h5m add jq to <folder> <name> <expression>

# js node
h5m add js to <folder> <name> <expression>

# jsonata node
h5m add jsonata to <folder> <name> <expression>
```

### REST API

```bash
curl -X POST \
  "http://localhost:8080/api/node?name=throughput&groupId=<groupId>&type=jq&operation=.metrics.throughput"
```

| Parameter | Description |
|-----------|-------------|
| `name` | Node name (unique within the group) |
| `groupId` | The NodeGroup this node belongs to |
| `type` | Node type discriminator: `jq`, `js`, `nata`, `sql`, etc. |
| `operation` | The expression string |

## Node Outputs

A node produces one **Value** per uploaded data file. If the expression returns a scalar (number, string, boolean), that is stored as the value. If it returns an array or object, the entire structure is stored.

A single node can produce multiple scalar outputs if the expression returns an array — each element becomes a separate value entry.

## Removing Nodes

```bash
# CLI (by ID shown in list node output)
h5m remove node <id>

# REST
curl -X DELETE http://localhost:8080/api/node/<id>
```

Removing a node does not automatically delete its computed values. Use `recalculate` after structural changes to keep values consistent.

## Node Dependencies

Nodes can reference other nodes' outputs using the `{nodeName}:expression` syntax. See [Dependencies](../dependencies/) for full details.
