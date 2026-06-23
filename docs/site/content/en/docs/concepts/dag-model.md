---
title: DAG Computation Model
linkTitle: DAG Model
weight: 10
description: How h5m uses a directed acyclic graph to represent data processing pipelines.
draft: false
---

The central idea in h5m is that all data processing — extraction, transformation, aggregation, change detection — is expressed as a **directed acyclic graph (DAG)** of computation nodes.

## Why a DAG?

Horreum models data processing as a linear pipeline through multiple distinct entity types: Extractors pull values, Labels combine them, Transformers convert schemas, Variables link outputs to change detection. Adding a new step means touching several of these layers.

h5m replaces this with a single polymorphic entity — the **Node** — and lets nodes reference each other's outputs directly. The result is a graph where:

- **Nodes** are the vertices
- **Dependencies** are the directed edges
- **Values** are what flow along the edges

Because the graph is acyclic (no cycles allowed), h5m can always determine a valid execution order via topological sort.

## Structure

```
raw JSON upload
      │
      ▼
  [throughput]  ─────────────────────────────────┐
  .metrics.throughput                             │
      │                                           │
      ▼                                           ▼
  [throughput_mbs]                        [latency_p99]
  {throughput}:. / 1000000               .metrics.latency.p99_ms
      │
      ▼
  [regression_flag]
  {throughput_mbs}:. < 40.0
```

Each node in this graph reads either the raw uploaded JSON or the output of one or more upstream nodes, and produces a new value.

## Execution Order

When data is uploaded to a folder, h5m:

1. Creates a root `Value` for the raw JSON
2. Identifies all nodes in the folder's graph
3. Performs a **topological sort** based on declared dependencies
4. Executes nodes in order — leaf nodes (no dependencies) first, dependent nodes after their inputs are ready
5. Stores the computed `Value` for each node

Nodes with no dependencies on each other may execute in any order, and independent work items do run concurrently via the worker thread pool.

## Acyclic Constraint

h5m does not permit cycles. A node cannot depend on itself or on a node that (transitively) depends on it. Attempting to create a circular dependency will be rejected.

## One Graph Per Folder

Each **Folder** has its own independent node graph. Nodes are primarily self-contained within a folder, but can reference nodes from another folder's group using a fully-qualified name (`folderName:nodeName`). This makes folders the natural unit of isolation while still allowing cross-folder reuse.

## Polymorphic Nodes

Every node in the graph is the same entity type — `NodeEntity` — but with a different `type` field (`jq`, `js`, `nata`, `sql`, `sql-all`, `split`, `fp`, `ft`, `rd`, `ed`, `root`, `user`). This means:

- Adding a new computation type requires only one new subclass
- The graph structure is the same regardless of what language the nodes use
- Mixed-language graphs are fully supported (a `jq` node can feed a `js` node)

## Relationship to Horreum's Model

| DAG concept | Horreum equivalent |
|-------------|-------------------|
| Node (leaf, no deps) | Extractor |
| Node (with deps, JS) | Label with JS function |
| Node chain | Transformer pipeline |
| Node with threshold check | Variable + Change Detection |
| Full graph | Schema + Extractors + Labels + Variables |

The DAG makes the data flow explicit and visible, whereas in Horreum the connections between entities are implicit and spread across multiple configuration screens.
