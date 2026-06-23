---
title: jq vs JSONPath
weight: 20
description: Why h5m supports jq over JSONPath and the trade-offs involved.
draft: false
---

Horreum uses **PostgreSQL's `jsonpath`** dialect to extract values from uploaded JSON. h5m replaces it with **`jq`**, evaluated in-process via [jackson-jq](https://github.com/eiiches/jackson-jq). This page explains why.

## What is PostgreSQL jsonpath?

`jsonpath` is a query language built into PostgreSQL for querying JSON columns. It is tightly integrated with the database — expressions run inside PostgreSQL, not in application code.

Example — extracting throughput in Horreum:

```sql
$.metrics.throughput
```

It works well for simple extractions but has significant limitations for complex transformations.

## What is jq?

`jq` is a standalone, Turing-complete JSON processing language with a large community, extensive tooling, and broad AI support. It runs in-process in h5m via `jackson-jq` — a pure-Java jq implementation that requires no external binary.

Example — same extraction in h5m:

```bash
.metrics.throughput
```

More complex — something jsonpath cannot express:

```bash
# Filter array elements, transform, and compute
[.endpoints[] | select(.p99_ms > 100) | {name, latency: .p99_ms}]

# Arithmetic across fields
(.metrics.success / .metrics.total) * 100

# Conditional logic
if .metrics.errors > 0 then "degraded" else "healthy" end
```

## Why jq Was Chosen

### 1. Turing-complete

PostgreSQL `jsonpath` is a query language — it can navigate and filter JSON, but it cannot perform arbitrary computation. jq is Turing-complete: it supports conditionals, recursion, arithmetic, string manipulation, array construction, and user-defined functions.

This means a single jq node can replace what would require multiple Horreum entities (Extractor + Label + JS transformer).

### 2. In-process — no database round-trip

In Horreum, every jsonpath expression executes inside PostgreSQL. The application must send data to the database, have it evaluated, and get the result back. For complex pipelines this adds latency and couples the computation model to the database engine.

In h5m, jq runs entirely inside the JVM via jackson-jq. No network, no database query. The expression evaluates in microseconds.

### 3. Tooling and testability

jq has a rich ecosystem:

- **[jqplay.org](https://jqplay.org/)** — interactive playground to test expressions against real JSON before adding them as nodes
- **`jq` CLI** — available on every major OS via package manager
- **IDE plugins** — syntax highlighting and autocomplete in VS Code, IntelliJ
- **AI support** — large language models have extensive jq training data and can generate and explain jq expressions reliably

PostgreSQL jsonpath has none of this. There is no standalone REPL, no widely-used playground, and limited AI support.

### 4. No PostgreSQL dependency for computation

Because jq runs in-process, computation works identically regardless of which database backend is active — SQLite, DuckDB, or PostgreSQL. jsonpath is a PostgreSQL-only feature; switching databases would break all extraction expressions.

## Trade-offs

| | jq (h5m) | PostgreSQL jsonpath (Horreum) |
|-|----------|-------------------------------|
| **Expressiveness** | Turing-complete | Query/filter only |
| **Execution location** | In-process (JVM) | Inside PostgreSQL |
| **Database coupling** | None | Requires PostgreSQL |
| **Tooling** | Rich (CLI, playground, AI) | Limited |
| **Learning curve** | Moderate | Low for simple queries |
| **Performance at scale** | JVM memory | Database-side, can use indexes |

The main trade-off: for very large datasets where you want to filter *before* loading into application memory, running expressions inside the database has advantages. h5m's design accepts this trade-off in exchange for simplicity and portability.

## JSONata and JavaScript

h5m also supports **JSONata** and **JavaScript** nodes alongside jq. JSONata has strong built-in aggregation functions (`$sum`, `$average`, `$count`). JavaScript (via GraalVM) gives full programmatic control when jq or JSONata are not expressive enough.

The choice of query language is per-node — a single folder can mix jq, JSONata, and JS nodes freely.
