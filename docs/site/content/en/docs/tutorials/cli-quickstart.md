---
title: CLI Quickstart
weight: 20
description: A fast end-to-end tour of h5m through the command-line interface.
draft: false
---

This tutorial walks through a complete h5m workflow using the CLI — from creating a folder to querying computed values — in under 10 minutes.

## Prerequisites

Build the CLI tool first:

```bash
mvn clean package -Pcli
```

The binary is at `target/cli/h5m`. You can add it to your `PATH` or use the full path throughout this guide.

```bash
alias h5m=./target/cli/h5m
```

## Step 1 — Create a Folder

A **Folder** is the top-level container for your benchmark data. Think of it as a named project or test suite.

```bash
h5m add folder my-benchmarks
```

Verify it was created:

```bash
h5m list folder
```

Output:

```
my-benchmarks
```

## Step 2 — Add Nodes

**Nodes** define how h5m extracts and transforms data from your uploaded JSON. Each node uses a filter expression — `jq`, `js`, or `jsonata`.

Add a `jq` node to extract throughput from the uploaded JSON:

```bash
h5m add jq to my-benchmarks throughput .metrics.throughput
```

This creates a node named `throughput` in the `my-benchmarks` folder. When data is uploaded, h5m applies the jq filter `.metrics.throughput` to each JSON file.

Add a second node that depends on the first — it normalizes the value to MB/s:

```bash
h5m add jq to my-benchmarks throughput_mbs "{throughput}:. / 1000000"
```

The `{throughput}` prefix declares a dependency on the `throughput` node's output.

List nodes to confirm:

```bash
h5m list my-benchmarks nodes
```

## Step 3 — Prepare Sample Data

Create a directory with a sample benchmark result JSON file:

```bash
mkdir -p /tmp/results

cat > /tmp/results/run-001.json << 'EOF'
{
  "timestamp": "2024-01-15T10:30:00Z",
  "metrics": {
    "throughput": 52428800,
    "latency_p99_ms": 12.4,
    "error_rate": 0.001
  }
}
EOF
```

## Step 4 — Upload Data

Upload the results directory to your folder:

```bash
h5m upload /tmp/results to my-benchmarks
```

h5m reads every `.json` file in the directory, queues processing work, and executes the node graph in topological order. The upload returns immediately; processing is asynchronous.

## Step 5 — Query Values

List the computed values for the `throughput` node:

```bash
h5m list my-benchmarks values by throughput as table
```

Example output:

```
FILE          throughput
run-001.json  52428800
```

Query the normalized value:

```bash
h5m list my-benchmarks values by throughput_mbs as table
```

```
FILE          throughput_mbs
run-001.json  52.4288
```

## Step 6 — Add More Data and Compare

Upload a second result to see multiple runs side by side:

```bash
cat > /tmp/results/run-002.json << 'EOF'
{
  "timestamp": "2024-01-16T10:30:00Z",
  "metrics": {
    "throughput": 49807360,
    "latency_p99_ms": 14.1,
    "error_rate": 0.002
  }
}
EOF

h5m upload /tmp/results to my-benchmarks
```

```bash
h5m list my-benchmarks values by throughput as table
```

```
FILE          throughput
run-001.json  52428800
run-002.json  49807360
```

## Available CLI Commands

| Command | Description |
|---------|-------------|
| `h5m add folder <name>` | Create a new folder |
| `h5m add jq to <folder> <node> <expr>` | Add a jq node |
| `h5m add js to <folder> <node> <expr>` | Add a JavaScript node |
| `h5m add jsonata to <folder> <node> <expr>` | Add a JSONata node |
| `h5m upload <path> to <folder>` | Upload JSON files |
| `h5m list folder` | List all folders |
| `h5m list <folder> nodes` | List nodes in a folder |
| `h5m list <folder> values by <node> as table` | Query computed values |
| `h5m remove folder <name>` | Delete a folder |
| `h5m remove node <id>` | Delete a node |
| `h5m recalculate <folder>` | Reprocess all values |
| `h5m purge-values` | Remove all stored values |

## Next Steps

- [Uploading Data](../uploading-data/) — upload via REST API and understand the processing model
- [Querying Results](../querying-results/) — advanced querying and the Value API
- [Concepts — Dependencies](../../concepts/dependencies/) — learn the full `{node}:expression` syntax
