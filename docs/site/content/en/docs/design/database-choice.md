---
title: Database Choice
weight: 40
description: Rationale for supporting SQLite, DuckDB, and PostgreSQL as interchangeable backends.
draft: false
---

h5m supports three database backends — **PostgreSQL**, **SQLite**, and **DuckDB** — selectable at runtime without recompiling. This page explains why multiple backends are supported and how the choice was made.

## Horreum's Approach: PostgreSQL Only

Horreum requires PostgreSQL. This is a deliberate choice in Horreum's design — it takes advantage of PostgreSQL-specific features like `jsonpath`, JSON operators, and row-level security. The database is a core part of the system, not a swappable dependency.

The downside: PostgreSQL is a server process that must be installed, configured, and operated separately. It is not feasible to run Horreum locally without it.

## h5m's Approach: Database-Agnostic

h5m deliberately avoids PostgreSQL-specific features in its core computation model. The main reason jq was chosen over `jsonpath` was precisely to remove this coupling. With computation moved in-process, the database's job is reduced to:

- Storing entities (Folders, Nodes, NodeGroups, Values, Work items)
- Querying by ID or foreign key
- Persisting the work queue

These are all standard SQL operations that any JDBC-compatible database can handle. Hibernate ORM abstracts the differences.

## Why SQLite

SQLite is a file-based embedded database — no server, no configuration, no port. A single file on disk contains the entire database. It is the most widely deployed database in the world and has excellent Java support via the `sqlite-jdbc` driver.

For h5m, SQLite enables the **single-JAR deployment model**: one JAR file, one database file, nothing else. This is the simplest possible operational story for local use, CI pipelines, and small-team deployments.

```bash
# The entire h5m deployment
java -jar h5m.jar   # starts with ~/h5m.db automatically
```

SQLite handles h5m's write pattern well — uploads are sequential per folder, and the work queue processes one item at a time. SQLite's single-writer model is not a limitation at this scale.

## Why DuckDB

[DuckDB](https://duckdb.org/) is an embedded analytical database — also file-based and serverless, but optimised for read-heavy, columnar workloads rather than transactional writes.

Where SQLite processes one row at a time, DuckDB processes columns in batches, making aggregations (GROUP BY, ORDER BY, window functions) significantly faster on large datasets.

For h5m, DuckDB becomes relevant when:
- A folder has thousands of uploads and hundreds of nodes
- Users frequently query "what is the p99 throughput across all runs in the last month?"
- Value data grows large enough that SQLite query times become noticeable

DuckDB is currently available but commented out in the default configuration — it is an option for users who need it rather than the out-of-the-box choice.

## Why Keep PostgreSQL

PostgreSQL remains supported for three reasons:

**1. Multi-user production deployments** — PostgreSQL handles concurrent writers correctly and has mature backup, replication, and monitoring tooling that SQLite and DuckDB lack.

**2. Existing infrastructure** — many teams already operate PostgreSQL. For them, adding an h5m database to an existing cluster is simpler than introducing a new embedded database.

**3. Future scale** — if h5m grows to support distributed deployments (multiple application instances sharing one database), PostgreSQL is the natural fit.

## How Interchangeability Works

All three backends are supported through the same Hibernate ORM layer. The only difference at the application level is the JDBC URL and driver. Hibernate translates the same `Panache` queries into the appropriate SQL dialect for each database.

Three named datasources are registered in the build so that drivers for all three backends are compiled in:

```properties
# All three compiled in, only one active at runtime
quarkus.datasource."sqllite".db-kind=sqlite
quarkus.datasource."postgresql".db-kind=postgresql
# quarkus.datasource.db-kind=duckdb  ← activate via env var
```

Switching backends is entirely a runtime configuration change — no recompile needed.

## Decision Summary

| Backend | Why supported | Best for |
|---------|--------------|----------|
| **PostgreSQL** | Production multi-user deployments, existing infrastructure | Teams, shared deployments |
| **SQLite** | Single-JAR simplicity, zero-config, local use | Local dev, CLI, CI pipelines |
| **DuckDB** | Analytical query performance on large value sets | Power users, large datasets |

The philosophy: **match the database to the deployment context**, not the other way around. A tool that forces PostgreSQL on a developer who just wants to run it locally has already failed at its goal of simplicity.
