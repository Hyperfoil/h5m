---
title: Query Values
weight: 40
description: Fetching and interpreting computed Values from h5m.
draft: false
---

After data is uploaded and processed, the computed outputs are stored as **Values**. This page covers how to retrieve them via the CLI and REST API.

## Via the CLI

### Query by Node Name

```bash
h5m list <folder> values by <node-name> as table
```

Example:

```bash
h5m list my-benchmarks values by throughput as table
```

Output:

```
throughput
52428800
49807360
51200000
```

Each row corresponds to one uploaded JSON file. Columns are the node names from the grouped result.

## Via the REST API

### Step 1 — Find the Node ID

List nodes to find the node ID:

```bash
curl "http://localhost:8080/api/node/find?name=throughput&groupId=<groupId>"
```

Or inspect the folder structure for all node IDs:

```bash
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

### Step 2 — Fetch Values

```bash
# All values for a node (one per uploaded file)
curl http://localhost:8080/api/value/node/<nodeId>
```

Response — an array of `Value` objects with fields `id`, `data`, `node` (full Node object), and `folder`:

```json
[
  {
    "id": 1,
    "data": 52428800,
    "node": { "id": 42, "name": "throughput", "type": "jq", "operation": ".metrics.throughput", "groupId": 1 },
    "folder": { "id": 1, "name": "my-benchmarks" }
  },
  {
    "id": 2,
    "data": 49807360,
    "node": { "id": 42, "name": "throughput", "type": "jq", "operation": ".metrics.throughput", "groupId": 1 },
    "folder": { "id": 1, "name": "my-benchmarks" }
  }
]
```

### Grouped Values

Get values grouped by their source path:

```bash
curl http://localhost:8080/api/value/node/<nodeId>/grouped
```

### Single Value by ID

Fetch the raw computed data for one value:

```bash
curl http://localhost:8080/api/value/<valueId>
```

### Descendant Values

Fetch a value and all values computed downstream from it in the DAG:

```bash
curl http://localhost:8080/api/value/node/<nodeId>/descendants
```

Useful for understanding the full output of a computation chain starting from a given node.

## Comparing Runs

To compare a metric across all uploads, query the node's values and sort by `path` or `data`:

```bash
curl http://localhost:8080/api/value/node/<nodeId> | \
  jq 'sort_by(.data) | .[] | {id, data}'
```

Example output:

```json
{ "id": 2, "data": 49807360 }
{ "id": 3, "data": 51200000 }
{ "id": 1, "data": 52428800 }
```

## Folder Dashboard

For a high-level overview of all folders:

```bash
curl http://localhost:8080/api/folder/dashboard
```

## Tracing a Value Back to Its Source

Each value carries a nested `node` object and a `folder` object. To understand how a value was computed:

1. Note `node.id` from the value response
2. Look up the node expression via `node.operation` (already in the response) or `curl /api/node/find?name=<name>&groupId=<gid>`
3. Check upstream inputs: fetch values for the nodes it depends on using `/api/value/node/<nodeId>/descendants`
4. Identify the source upload by tracing back to the root value for that folder

## Purge All Values

To wipe all stored values across all folders (destructive — use with care):

```bash
h5m purge-values
# or (requires admin role)
curl -X DELETE http://localhost:8080/api/value
```

This does not delete nodes or folders — only the computed value records. Run `recalculate` afterwards to recompute.
