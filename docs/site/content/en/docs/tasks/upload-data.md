---
title: Upload Data
weight: 30
description: Pushing JSON benchmark results into h5m via the CLI or REST API.
draft: false
---

Data is uploaded to h5m as JSON files — one file per benchmark run or data point. After upload, h5m processes the file through the folder's node graph and stores the computed values.

## Prerequisites

- A folder must exist: `h5m add folder <name>`
- At least one node must be defined so there is something to compute

## Upload via CLI

```bash
h5m upload <directory> to <folder>
```

h5m reads every `.json` file in the given directory and uploads each one:

```bash
h5m upload ./results/ to my-benchmarks
```

After queuing all files, the CLI blocks until processing completes (via `workService.terminate`). Values are ready to query once the command exits.

## Upload via REST API

The REST API accepts one JSON document per request:

```bash
curl -X POST \
  "http://localhost:8080/api/folder/my-benchmarks/upload?path=run-001.json" \
  -H "Content-Type: application/json" \
  -d @run-001.json
```

| Parameter | Location | Description |
|-----------|----------|-------------|
| `name` | Path | Folder name |
| `path` | Query (optional) | Accepted by the endpoint but currently not stored — reserved for future traceability use |

### Upload a Directory via Script

```bash
for file in ./results/*.json; do
  filename=$(basename "$file")
  curl -s -X POST \
    "http://localhost:8080/api/folder/my-benchmarks/upload?path=${filename}" \
    -H "Content-Type: application/json" \
    -d @"$file"
  echo "Uploaded $filename"
done
```

## JSON Format Requirements

h5m imposes no schema on uploaded JSON. Any valid JSON object is accepted. The structure only matters in that your node expressions must be able to read it.

A minimal example:

```json
{
  "metrics": {
    "throughput": 52428800
  }
}
```

A richer example with metadata:

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
    "errors": 3,
    "total_requests": 10000
  }
}
```

## Recalculate

If you add or change nodes after uploading data, reprocess existing uploads:

```bash
# CLI
h5m recalculate my-benchmarks

# REST API
curl -X POST http://localhost:8080/api/folder/my-benchmarks/recalculate
```

This re-runs the full node graph against every previously uploaded file and replaces the stored values.

## Checking What Was Uploaded

Inspect the folder structure to see its uploaded data and node graph:

```bash
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

## Tips

- **CLI vs REST processing model** — the CLI blocks until all node computations complete before returning. The REST API returns immediately and processes asynchronously; poll the value API to confirm results are ready before querying.
- **`path` parameter** — the REST endpoint accepts `path` but does not currently store it. Do not rely on it for traceability.
- **JSON arrays** — if your benchmark tool outputs a JSON array of runs, either split it into separate files or use a jq node with `.[]` to iterate over elements.
