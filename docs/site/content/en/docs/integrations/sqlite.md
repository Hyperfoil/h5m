---
title: SQLite
weight: 10
description: Using SQLite as the h5m storage backend — zero-config, single-file persistence.
draft: false
---

SQLite is the simplest h5m backend: no server to install, no credentials to configure, and no network required. Data is stored in a single file on disk. It is the recommended backend for local development, CLI usage, and single-JAR deployments.

## Configuration

Switch to SQLite by setting the datasource kind. The file path is controlled via `H5M_PATH` — **not** via `quarkus.datasource.jdbc.url`, which is ignored for SQLite:

```bash
export QUARKUS_DATASOURCE_DB_KIND=sqlite
export H5M_PATH=/home/user/h5m.db
java -jar target/h5m.jar
```

Or in `application.properties`:

```properties
quarkus.datasource.db-kind=sqlite
```

Set `H5M_PATH` in the environment to control where the file is written. If `H5M_PATH` is not set, h5m falls back to `./h5m.db` (if it exists) and then `~/h5m.db`.

## File Location

The CLI resolves the database path in this order:

1. `H5M_PATH` environment variable (if set)
2. `h5m.db` in the current working directory (if it already exists)
3. `~/h5m.db` (home directory fallback)

Override the path by setting `H5M_PATH`:

```bash
export H5M_PATH=/var/lib/h5m/h5m.db
```

Common conventions:

| Path | When to use |
|------|-------------|
| `~/h5m.db` | Personal / developer use |
| `/var/lib/h5m/h5m.db` | System service deployment |
| `/data/h5m.db` | Docker volume mount |

## In-Memory Mode

For testing or ephemeral use, run SQLite entirely in memory — data is lost when the process exits:

```bash
export QUARKUS_DATASOURCE_DB_KIND=sqlite
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite::memory:
java -jar target/h5m.jar
```

## Connection Pool

SQLite is a file-based database and handles concurrency differently from server databases. Keep the pool small:

```properties
quarkus.datasource.jdbc.initial-size=1
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=2
```

Running more than a few concurrent writers against SQLite will cause contention. For high-concurrency workloads, use PostgreSQL instead.

## Backup

h5m enables SQLite's WAL (Write-Ahead Log) mode, which means up to three files exist alongside the main database: `h5m.db`, `h5m.db-shm`, and `h5m.db-wal`. Copy all three when h5m is stopped (stopping the process checkpoints the WAL back into the main file):

```bash
# Copy all files while h5m is stopped
cp ~/h5m.db ~/h5m.db.bak
cp ~/h5m.db-shm ~/h5m.db-shm.bak
cp ~/h5m.db-wal ~/h5m.db-wal.bak

# Online backup using the SQLite CLI (handles WAL automatically)
sqlite3 ~/h5m.db ".backup ~/h5m.db.bak"
```

## When to Use SQLite

- Local development and experimentation
- CLI-driven workflows with no concurrent writers
- Single-user deployments without infrastructure overhead
- CI pipelines where data does not need to persist between runs (use in-memory mode)

For multi-user or production deployments, see [PostgreSQL](../postgresql/).
