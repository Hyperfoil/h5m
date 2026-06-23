---
title: Uploading Data
weight: 30
description: How to push benchmark result JSON into h5m via the CLI or REST API.
draft: false
---

h5m accepts benchmark results as JSON files. Each file represents one run or data point. This page covers uploading via the CLI and the REST API.

## Data Format

h5m places no schema requirements on uploaded JSON. Any valid JSON object is accepted. The structure you use determines what jq/js/jsonata expressions you write in your nodes.

Example benchmark result:

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "build": "main-abc123",
  "environment": "ci-runner-1",
  "metrics": {
    "throughput": 52428800,
    "latency": {
      "p50_ms": 4.2,
      "p95_ms": 8.7,
      "p99_ms": 12.4
    },
    "errors": 3
  }
}
```

## Upload via CLI

### Single Directory

Upload all `.json` files from a directory to a folder:

```bash
h5m upload /path/to/results/ to my-benchmarks
```

### What Happens on Upload

1. h5m reads each `.json` file from the directory
2. A root `Value` is created for each file
3. Work items are created for each node in the folder's graph
4. Work items are executed in topological order (respecting node dependencies)
5. Computed `Value` records are stored for each node

The upload command returns immediately. Processing is **asynchronous** — allow a moment before querying values.

### Recalculate

If you add new nodes after uploading data, recalculate to apply them to existing data:

```bash
h5m recalculate my-benchmarks
```

## Upload via REST API

### Endpoint

```
POST /api/folder/{name}/upload
```

### Parameters

| Parameter | Location | Description |
|-----------|----------|-------------|
| `name` | Path | The folder name |
| `path` | Query | Optional: a label or source path to associate with this upload |

### Request Body

The request body is the raw JSON content of one benchmark result:

```bash
curl -X POST \
  "http://localhost:8080/api/folder/my-benchmarks/upload?path=run-001.json" \
  -H "Content-Type: application/json" \
  -d '{
    "metrics": {
      "throughput": 52428800,
      "latency_p99_ms": 12.4
    }
  }'
```

### Uploading Multiple Results

The REST API accepts one JSON document per request. To upload a directory of results, loop over your files:

```bash
for file in /path/to/results/*.json; do
  filename=$(basename "$file")
  curl -s -X POST \
    "http://localhost:8080/api/folder/my-benchmarks/upload?path=${filename}" \
    -H "Content-Type: application/json" \
    -d @"$file"
  echo "Uploaded $filename"
done
```

## Checking the Folder Structure

After uploading, inspect what h5m has ingested:

```bash
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

This returns a JSON summary of the folder's content and the node graph.

## Recalculate via REST API

```bash
curl -X POST http://localhost:8080/api/folder/my-benchmarks/recalculate
```

Triggers reprocessing of all uploaded data against the current node graph. Useful after adding or modifying nodes.

## Tips

- **File naming** — the `path` query parameter (or the CLI filename) is stored as metadata on each value, making it easy to trace a result back to its source file.
- **JSON arrays** — if your benchmark tool outputs a JSON array, wrap it or use a `jq` node with `.[]` to iterate over items.
- **Large batches** — for large uploads, consider uploading in parallel using `xargs` or a simple script, since each upload is independent.
