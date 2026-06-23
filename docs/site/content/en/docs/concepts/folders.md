---
title: Folders
weight: 20
description: Folders are the top-level organizational containers for Nodes and data in h5m.
draft: false
---

A **Folder** is the primary organizational unit in h5m. It groups a node graph with the data uploaded against it, forming a self-contained benchmark data repository.

## What a Folder Contains

|  Component           | Description |
|----------------------|-------------|
| **Name**             | A unique identifier for the folder |
| **NodeGroup**        | One group of computation nodes (one per folder) |
| **Uploaded data**    | The raw JSON files submitted to this folder |
| **Values**           | Computed outputs produced by the node graph |
| **Views** (optional) | Named column configurations for displaying values |

## Folder as a "Test" Analogue

If you are familiar with Horreum, a Folder in h5m is roughly equivalent to a **Test** in Horreum. It represents a particular benchmark scenario — for example, a specific workload, configuration, or component under test. All runs of that scenario are uploaded to the same folder so their values can be compared over time.

## Lifecycle

### Create

```bash
# CLI
h5m add folder my-benchmarks

# REST
curl -X POST http://localhost:8080/api/folder/my-benchmarks
```

### Upload Data

```bash
h5m upload /path/to/results/ to my-benchmarks
```

Each `.json` file in the directory becomes one data point in the folder.

### Query

```bash
h5m list my-benchmarks values by throughput as table
```

### Delete

```bash
# CLI
h5m remove folder my-benchmarks

# REST
curl -X DELETE http://localhost:8080/api/folder/my-benchmarks
```

Deleting a folder removes its node groups, nodes, and all associated values.

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/folder` | List all folders |
| `GET` | `/api/folder/{name}` | Get a single folder by name |
| `POST` | `/api/folder/{name}` | Create a folder |
| `DELETE` | `/api/folder/{name}` | Delete a folder |
| `GET` | `/api/folder/dashboard` | Folder summaries for dashboards |
| `GET` | `/api/folder/{name}/structure` | Folder structure and node graph |
| `POST` | `/api/folder/{name}/upload` | Upload a JSON data point |
| `POST` | `/api/folder/{name}/recalculate` | Reprocess all data against current nodes |

## Isolation

Folders are the primary unit of isolation. Uploads to one folder do not affect another. Nodes are primarily self-contained within their folder, but can reference nodes in another folder's group using a fully-qualified `folderName:nodeName` syntax. This makes folders safe to use concurrently.

## Recalculate

If you add, remove, or modify nodes after data has already been uploaded, use recalculate to apply the updated graph to existing data:

```bash
h5m recalculate my-benchmarks
# or
curl -X POST http://localhost:8080/api/folder/my-benchmarks/recalculate
```
