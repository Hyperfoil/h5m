---
title: What is h5m?
weight: 10
description: Goals, scope, and the core value proposition of h5m.
draft: false
---

h5m is a lightweight proof-of-concept redesign of [Horreum](https://horreum.hyperfoil.io) — a performance data repository used to store, query, and detect regressions in benchmark results, reflecting its goal of reducing both conceptual and operational complexity.

## Goals

h5m was created to address two interrelated problems with Horreum:

- **Conceptual complexity** — Horreum's data model involves multiple interrelated entities (Labels, Extractors, Transformers, Variables) that are powerful but hard to reason about.
- **Implementation complexity** — Horreum requires an external message broker (ActiveMQ), a PostgreSQL database, and several services to run.

h5m replaces all of this with a small set of primitives and a single-JAR deployment mode.

## Core Concepts

### Folder

A **Folder** is the top-level organizational unit in h5m. It maps to a directory on disk that holds source JSON files — each file representing one benchmark run or data upload.

### Node

A **Node** is a computation unit that takes one or more inputs and produces one or more outputs. Nodes are the primary building block of h5m's data processing pipeline. There are several node categories:

**Transformation nodes** — extract and reshape data using an expression language:

| Type | Language | Description |
|------|----------|-------------|
| `jq` | [jq](https://jqlang.org/) | Turing-complete JSON filter language, evaluated in-process via [jackson-jq](https://github.com/eiiches/jackson-jq) |
| `js` | JavaScript (ECMAScript) | Full scripting via GraalVM Polyglot |
| `jsonata` | [JSONata](https://jsonata.org/) | Declarative JSON query and transformation language |
| `sql` | PostgreSQL JSONPath | Single-result SQL jsonpath query |
| `sql-all` | PostgreSQL JSONPath | Array-result SQL jsonpath query |
| `split` | — | Splits a JSON array into individual values |

**Structural nodes** — control the graph:

| Type | Description |
|------|-------------|
| `root` | Auto-created entry point; receives raw uploaded JSON |
| `fp` (Fingerprint) | Deterministic hash of source values used to group change detection results |

**Change detection nodes** — trigger alerts when thresholds are violated:

| Type | Description |
|------|-------------|
| `ft` (FixedThreshold) | Fires when a value falls outside a static min/max range |
| `rd` (RelativeDifference) | Detects trend shifts by comparing recent values against a historical baseline |
| `ed` (EDivisive) | Change-point detection (stub) |

### NodeGroup

A **NodeGroup** is a collection of nodes that share the same data source. NodeGroups allow multiple nodes to operate on the same input without redundant reads.

### Value

A **Value** is an output produced by a node. A single node can yield multiple values. Values are stored and queryable after each data upload.

## The DAG Model

Nodes are wired together into a **directed acyclic graph (DAG)** using a dependency syntax:

```
{foo}:.bar
```

This means: _take the output of node `foo` and apply the jq filter `.bar` to it_. Dependencies are declared inline within the node's expression, making the data flow visible without a separate wiring step.

A node can depend on multiple upstream nodes:

```
{foo}:.result + {bar}:.baseline
```

h5m resolves the graph at upload time, executing nodes in topological order.

## Design Decisions

### jq over PostgreSQL JSONPath

Horreum uses PostgreSQL's `jsonpath` for querying JSON. h5m replaces it with `jq`, which is Turing-complete, has broad community support, and runs in-process without a database query round-trip.

### Persistence-backed WorkQueue over ActiveMQ

Horreum relies on ActiveMQ (AMQ) as its message broker. h5m replaces it with an in-process, persistence-backed WorkQueue that supports inter-task dependencies and deduplication — features AMQ did not provide — while eliminating the external broker entirely.

### Single-JAR Deployment

h5m is built on [Quarkus](https://quarkus.io/) and can run as a single JAR with no external services. In **CLI mode** the default persistence backend is **SQLite**, stored at `~/h5m.db` (overridable via `H5M_PATH`). The **web server** defaults to **PostgreSQL**. DuckDB is also supported as an experimental analytics backend.

## Quick Example

```bash
# Build the CLI
mvn clean package -Pcli

# Create a folder
target/cli/h5m add folder my-benchmarks

# Add a jq node to extract throughput
target/cli/h5m add jq to my-benchmarks throughput .metrics.throughput

# Add a dependent node that normalizes against a baseline
target/cli/h5m add jq to my-benchmarks normalized "{throughput}:.value / 1000"

# Upload benchmark result JSON
target/cli/h5m upload ./results/ to my-benchmarks

# Query computed values
target/cli/h5m list my-benchmarks values by throughput as table
```

## Project Info

- **Language:** Java 25 (Quarkus), TypeScript (frontend)
- **License:** Apache 2.0
- **Repository:** [github.com/hyperfoil/h5m](https://github.com/hyperfoil/h5m)
- **Part of:** the [Hyperfoil](https://hyperfoil.io) ecosystem
