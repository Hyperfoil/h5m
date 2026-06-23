---
title: Create Folders
weight: 10
description: How to create, inspect, and delete Folders in h5m.
draft: false
---

Folders are the top-level containers for benchmark data in h5m. This page covers creating them via the CLI and REST API, verifying they exist, and removing them when no longer needed.

## Create a Folder

### CLI

```bash
h5m add folder <name>
```

Example:

```bash
h5m add folder throughput-benchmarks
```

If the CLI is attached to a terminal and you omit the name, you will be prompted interactively.

### REST API

```bash
curl -X POST http://localhost:8080/api/folder/throughput-benchmarks
```

Returns the folder ID as a `long`.

## List Folders

### CLI

```bash
h5m list folder
```

### REST API

```bash
# Full list
curl http://localhost:8080/api/folder

# Dashboard summaries (counts, metadata)
curl http://localhost:8080/api/folder/dashboard
```

## Inspect a Folder

Retrieve a single folder's details including its node groups:

```bash
curl http://localhost:8080/api/folder/throughput-benchmarks
```

Inspect the folder's node graph and uploaded data structure:

```bash
curl http://localhost:8080/api/folder/throughput-benchmarks/structure
```

## Delete a Folder

Deleting a folder removes it along with all its node groups, nodes, and computed values. This action is irreversible.

### CLI

```bash
h5m remove folder throughput-benchmarks
```

### REST API

```bash
curl -X DELETE http://localhost:8080/api/folder/throughput-benchmarks
```

## Naming Conventions

Folder names:
- Must be unique across all folders
- Are used directly as URL path segments (`/api/folder/{name}`)
- Should use lowercase letters, numbers, and hyphens for safe URL usage

## Next Steps

After creating a folder:
1. [Add nodes](../add-nodes/) to define what data to extract
2. [Upload data](../upload-data/) to populate the folder
3. [Query values](../query-values/) to retrieve computed results
