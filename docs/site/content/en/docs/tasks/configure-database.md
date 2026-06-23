---
title: Configure the Database
weight: 50
description: Switching between PostgreSQL, SQLite, and DuckDB backends in h5m.
draft: false
---

h5m supports three persistence backends — **PostgreSQL** (default), **SQLite**, and **DuckDB** — selectable via Quarkus configuration properties. All three use the same Hibernate ORM layer, so the application code is identical regardless of which backend is active.

## Default: PostgreSQL

Out of the box, h5m connects to a PostgreSQL database. Configure the JDBC URL, username, and password via environment variables or an `application.properties` override:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/h5m
quarkus.datasource.username=h5m
quarkus.datasource.password=secret
```

Or as environment variables (Quarkus maps these automatically):

```bash
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/h5m
export QUARKUS_DATASOURCE_USERNAME=h5m
export QUARKUS_DATASOURCE_PASSWORD=secret
java -jar target/h5m.jar
```

## SQLite (Single-JAR / Local Mode)

SQLite requires no external server. It writes to a single file on disk, making it ideal for CLI use, local experimentation, and single-JAR deployment.

Switch to SQLite by overriding the datasource kind:

```properties
quarkus.datasource.db-kind=sqlite
```

The CLI profile (`%cli`) sets this automatically — no manual override is needed when using the CLI binary.

**Important:** `quarkus.datasource.jdbc.url` is **ignored for SQLite**. h5m resolves the SQLite file path programmatically in `DatasourceConfiguration.getPath()`, in this order:

1. `H5M_PATH` environment variable (if set)
2. `./h5m.db` in the current working directory (if the file already exists)
3. `~/h5m.db` (default fallback)

To set a custom path, use `H5M_PATH`:

```bash
H5M_PATH=/data/my-benchmarks.db java -jar target/h5m.jar
```

To run the web server against a local SQLite file:

```bash
QUARKUS_DATASOURCE_DB_KIND=sqlite H5M_PATH=/data/my-benchmarks.db java -jar target/h5m.jar
```

## DuckDB (Analytical Workloads)

DuckDB is optimised for analytical queries and can handle large value datasets more efficiently than SQLite. It also writes to a single file.

```properties
quarkus.datasource.db-kind=duckdb
quarkus.datasource.db-version=1.0.0
quarkus.datasource.jdbc.url=jdbc:duckdb:/home/user/h5m.duckdb
```

{{< alert color="warning" >}}
DuckDB support is currently commented out in the default `application.properties`. Uncomment the relevant lines and ensure the DuckDB JDBC driver is on the classpath before using this backend.
{{< /alert >}}

## Connection Pool Settings

These defaults apply to all backends:

```properties
quarkus.datasource.jdbc.acquisition-timeout=PT30S
quarkus.datasource.jdbc.initial-size=1
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=10
```

Increase `max-size` for high-concurrency deployments on PostgreSQL.

## Schema Management

h5m uses Hibernate ORM with `update` as the schema management strategy:

```properties
quarkus.hibernate-orm.schema-management.strategy=update
```

This automatically applies schema changes on startup. For a fresh database, the schema is created on first run. For an existing database, only additive changes (new tables, new columns) are applied.

{{< alert color="warning" >}}
`strategy=update` does not drop columns or tables. For destructive migrations, apply SQL changes manually before starting h5m.
{{< /alert >}}

## Choosing a Backend

| Scenario | Recommended Backend |
|----------|-------------------|
| CLI / local development | SQLite |
| Single-JAR, no external services | SQLite |
| Multi-user / production | PostgreSQL |
| Large analytical queries over values | DuckDB |
| CI pipeline with ephemeral storage | SQLite in-memory |

## Overriding at Runtime

Quarkus configuration properties can be overridden at runtime without recompiling. The order of precedence (highest to lowest) is:

1. System properties (`-Dquarkus.datasource.jdbc.url=...`)
2. Environment variables (`QUARKUS_DATASOURCE_JDBC_URL=...`)
3. `application.properties` in the JAR
4. Default values

This means you can ship a single JAR and configure it for different backends purely via environment variables.
