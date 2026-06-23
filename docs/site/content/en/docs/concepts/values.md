---
title: Values
weight: 50
description: The outputs produced by Node computation — stored, queryable, and composable.
draft: false
---

A **Value** is the output produced when a Node's expression is applied to an uploaded data file. Values are persisted in h5m's database and are the primary artifact you query after uploading benchmark data.

## Value Structure

Each Value record contains:

| Field | Description |
|-------|-------------|
| `id` | Unique identifier |
| `data` | The computed result — any valid JSON |
| `node` | The node that produced this value (full Node object: id, name, type, operation, etc.) |
| `folder` | The folder this value belongs to (id and name) |

The `data` field can hold a scalar (number, string, boolean), an array, or an object — whatever the node's expression returns.

## One Value Per Node Per Upload

For each JSON file uploaded to a folder, h5m runs every node in the graph and stores one Value per node. So if a folder has 5 nodes and you upload 10 JSON files, you get 50 Value records.

```
Upload run-001.json  →  [throughput=52428800] [latency_p99=12.4] [error_rate=0.001] ...
Upload run-002.json  →  [throughput=49807360] [latency_p99=14.1] [error_rate=0.002] ...
```

## Values as DAG Edges

Values are not just outputs — they are also the data that flows between nodes in the DAG. When node B depends on node A, h5m passes node A's `Value.data` as the input to node B's expression. This makes the full computation history traceable: every intermediate result is stored, not just the final output.

## Querying Values

### CLI

```bash
h5m list <folder> values by <node-name> as table
```

### REST API

```bash
# All values for a node
GET /api/value/node/{nodeId}

# Grouped by node name
GET /api/value/node/{nodeId}/grouped

# A single value by ID
GET /api/value/{id}

# All descendants of a value in the DAG
GET /api/value/node/{nodeId}/descendants
```

## Value Lifecycle

Values are created during upload processing and persist until:
- The folder is deleted
- The node is deleted
- `purge-values` is called (removes **all** values across all folders — use with caution)

Recalculating a folder (`h5m recalculate <folder>`) reprocesses existing uploads and **replaces** existing values with freshly computed ones.

## Multiple Values from One Node

If a node's expression returns an array, each element is stored as a separate value entry associated with that node and upload. For example, a jq expression of `.endpoints[]` over a JSON file with 5 endpoints produces 5 Value records for that one upload.

## Tracing Lineage

Because every intermediate computation is stored, you can trace the full lineage of any value:

1. Find the value ID for a suspicious result
2. Fetch `/api/value/{id}` to see the raw data
3. Look at the node that produced it to see the expression used
4. Check upstream node values via `/api/value/node/{upstreamNodeId}` to see the inputs

This makes debugging a computation graph straightforward without needing to re-run anything.
