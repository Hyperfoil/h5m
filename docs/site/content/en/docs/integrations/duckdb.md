---
title: DuckDB
weight: 20
description: Using DuckDB for analytical query workloads in h5m.
draft: false
---

DuckDB is an embedded analytical database optimised for read-heavy, column-oriented queries. Like SQLite, it requires no external server and stores data in a single file. It is a good fit when you have large numbers of Values and want fast aggregation queries over them.

{{< alert color="warning" >}}
DuckDB support is currently commented out in the default `application.properties`. You will need to uncomment the relevant configuration before using this backend. The DuckDB JDBC driver is already included in the build unconditionally.
{{< /alert >}}

## Configuration

```bash
export QUARKUS_DATASOURCE_DB_KIND=duckdb
export QUARKUS_DATASOURCE_DB_VERSION=1.0.0
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:duckdb:/home/user/h5m.duckdb
java -jar target/h5m.jar
```

Or in `application.properties`:

```properties
quarkus.datasource.db-kind=duckdb
quarkus.datasource.db-version=1.0.0
quarkus.datasource.jdbc.url=jdbc:duckdb:/home/user/h5m.duckdb
```

## Enabling in the Build

The default `application.properties` has DuckDB commented out:

```properties
#quarkus.datasource.db-kind=duckdb
#quarkus.datasource.db-version=1.0.0
```

Uncomment these lines (or override via environment variables) to activate DuckDB. The `duckdb_jdbc` dependency is already unconditionally present in `pom.xml` at version `1.4.4.0` — no build changes are required.

## File Location

```bash
# Explicit path
jdbc:duckdb:/var/lib/h5m/h5m.duckdb

# In-memory (data lost on shutdown)
jdbc:duckdb:
```

## When to Use DuckDB

| Scenario | Recommendation |
|----------|---------------|
| Large value datasets, fast aggregations | DuckDB |
| Low write concurrency, read-heavy queries | DuckDB |
| Simple local use, small datasets | SQLite |
| Multi-user production deployment | PostgreSQL |

DuckDB excels when querying many thousands of Value records with GROUP BY, ORDER BY, or window functions. For typical CI-scale workloads (hundreds of runs), SQLite is sufficient.

## Limitations

- DuckDB does not support multiple concurrent writers. h5m's upload processing is sequential per folder, so this is generally not a problem.
- In-memory DuckDB databases cannot be accessed from multiple connections simultaneously.

## Backup

```bash
# Copy the file while h5m is stopped
cp ~/h5m.duckdb ~/h5m.duckdb.bak
```

DuckDB also provides an `EXPORT DATABASE` SQL command for more structured backups.
