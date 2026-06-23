---
title: NodeGroups
weight: 40
description: How NodeGroups organize Nodes within a Folder and control their shared data source.
draft: false
---

A **NodeGroup** is the container that holds all computation nodes for a Folder. It defines the node graph that h5m applies to every JSON file uploaded to that folder.

## Relationship to Folders and Nodes

```
Folder
└── NodeGroup (exactly one)
    ├── RootNode  (auto-created, receives the raw upload)
    ├── Node A
    ├── Node B
    └── Node C (depends on A and B)
```

- A **Folder** has exactly one NodeGroup
- A **NodeGroup** contains one or more Nodes plus an auto-created **RootNode**
- Nodes within the same NodeGroup share access to the same uploaded JSON and can reference each other's outputs

## Automatic Creation

The NodeGroup is created automatically when you create a folder — you never need to create one manually:

```bash
# Creating the folder also creates its NodeGroup
h5m add folder my-benchmarks

# Nodes are then added to that folder's NodeGroup
h5m add jq to my-benchmarks throughput .metrics.throughput
```

## REST API

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/group/{name}` | Get a NodeGroup by name |
| `GET` | `/api/group/id/{id}` | Get a NodeGroup by ID |
| `DELETE` | `/api/group/{id}` | Delete a NodeGroup (and its nodes) |

## NodeGroup vs Horreum

If you are coming from Horreum, there is no direct equivalent. The closest analogy is a **Schema** combined with its associated **Extractors** and **Labels** — the NodeGroup defines which transformations are applied to data in that folder, and the nodes within it define the individual steps.

## Listing NodeGroups

Inspect the folder structure to see its NodeGroup and all nodes:

```bash
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

Or retrieve the group directly:

```bash
curl http://localhost:8080/api/group/my-benchmarks
```
