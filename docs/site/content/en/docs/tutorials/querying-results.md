---
title: Querying Results
weight: 40
description: Retrieve computed values from h5m using the CLI and REST API.
draft: false
---

After uploading data and processing it through the node graph, h5m stores the computed outputs as **Values**. This page explains how to retrieve and interpret them.

## Via the CLI

### List Values by Node

```bash
h5m list <folder> values by <node> as table
```

Example:

```bash
h5m list my-benchmarks values by throughput as table
```

Output:

```
FILE          throughput
run-001.json  52428800
run-002.json  49807360
```

### List All Nodes in a Folder

Before querying values, check what nodes exist:

```bash
h5m list my-benchmarks nodes
```

## Via the REST API

### Get Values for a Node

First, find the node ID:

```bash
curl "http://localhost:8080/api/node/find?name=throughput&groupId=<groupId>"
```

Then fetch its values:

```bash
curl "http://localhost:8080/api/value/node/<nodeId>"
```

Response — an array of Value objects:

```json
[
  {
    "id": 1,
    "nodeId": 42,
    "path": "run-001.json",
    "data": 52428800
  },
  {
    "id": 2,
    "nodeId": 42,
    "path": "run-002.json",
    "data": 49807360
  }
]
```

### Get Grouped Values

For a richer view that groups values by their source path:

```bash
curl "http://localhost:8080/api/value/node/<nodeId>/grouped"
```

### Get a Single Value by ID

```bash
curl "http://localhost:8080/api/value/<valueId>"
```

Returns the raw JSON data for that value.

### Get All Descendant Values

Fetch a value and all values that were computed downstream from it in the DAG:

```bash
curl "http://localhost:8080/api/value/node/<nodeId>/descendants"
```

Useful for understanding the full output of a computation chain.

## Folder Dashboard

Get a high-level summary of all folders and their recent activity:

```bash
curl "http://localhost:8080/api/folder/dashboard"
```

Returns a list of `FolderSummary` objects with counts and metadata.

## Understanding Value Structure

A `Value` represents the output of one node applied to one uploaded data file:

| Field | Description |
|-------|-------------|
| `id` | Unique value identifier |
| `nodeId` | The node that produced this value |
| `path` | Source file path or label provided at upload time |
| `data` | The computed result — any valid JSON (number, string, object, array) |

A single node produces one `Value` per uploaded file. If a node's jq expression returns multiple results (e.g. `.items[]`), all results are captured.

## Example: Comparing Runs

To compare throughput across all uploads:

```bash
# Get all throughput values as a table
h5m list my-benchmarks values by throughput as table

# Get normalized throughput (MB/s) alongside
h5m list my-benchmarks values by throughput_mbs as table
```

To find the node IDs and query via API:

```bash
# List nodes (shows IDs)
curl http://localhost:8080/api/folder/my-benchmarks | jq '.nodeGroups'

# Fetch values for node 42
curl http://localhost:8080/api/value/node/42 | jq '.[] | {path, data}'
```

## Next Steps

- [Core Tasks — Query Values](../../tasks/query-values/) — advanced query patterns
- [Concepts — Values](../../concepts/values/) — how values are stored and structured
- [Concepts — DAG Model](../../concepts/dag-model/) — how node dependencies affect value computation
