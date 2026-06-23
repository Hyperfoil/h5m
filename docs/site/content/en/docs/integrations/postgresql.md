---
title: PostgreSQL
weight: 30
description: Using PostgreSQL for production multi-user h5m deployments.
draft: false
---

PostgreSQL is h5m's default database backend and the recommended choice for production deployments. It supports concurrent writers, robust transactions, and scales well for multi-user scenarios.

## Configuration

Set the JDBC URL, username, and password via environment variables:

```bash
export QUARKUS_DATASOURCE_DB_KIND=postgresql
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/h5m
export QUARKUS_DATASOURCE_USERNAME=h5m
export QUARKUS_DATASOURCE_PASSWORD=secret
java -jar target/h5m.jar
```

Or in `application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/h5m
quarkus.datasource.username=h5m
quarkus.datasource.password=secret
```

## Creating the Database

h5m creates its schema automatically on first startup (`schema-management.strategy=update`). You only need to create the empty database and user:

```sql
CREATE USER h5m WITH PASSWORD 'secret';
CREATE DATABASE h5m OWNER h5m;
```

Then start h5m — Hibernate ORM will create all tables on startup.

## Running PostgreSQL Locally

For development, use Docker:

```bash
docker run -d \
  --name h5m-postgres \
  -e POSTGRES_USER=h5m \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=h5m \
  -p 5432:5432 \
  postgres:16
```

## Connection Pool

The defaults work for most workloads:

```properties
quarkus.datasource.jdbc.acquisition-timeout=PT30S
quarkus.datasource.jdbc.initial-size=1
quarkus.datasource.jdbc.min-size=1
quarkus.datasource.jdbc.max-size=10
```

For high-concurrency deployments, increase `max-size`. For a deployment with a small connection limit on the PostgreSQL side, reduce it accordingly.

## SSL / TLS

To connect over SSL, append parameters to the JDBC URL:

```properties
quarkus.datasource.jdbc.url=jdbc:postgresql://db.example.com:5432/h5m?ssl=true&sslmode=require
```

## Schema Management

h5m uses `strategy=update`, which:
- Creates tables on first run
- Applies additive changes (new columns, new tables) on subsequent runs
- Does **not** drop existing columns or tables

For destructive schema changes between versions, apply SQL migrations manually before starting h5m.

## When to Use PostgreSQL

- Multi-user deployments with concurrent uploads and queries
- Production environments where data durability and backup tooling matter
- Deployments already running PostgreSQL for other services
- Scenarios requiring row-level security or advanced access control in the future

For single-user or CLI-only use, [SQLite](../sqlite/) is simpler. For read-heavy analytical workloads, [DuckDB](../duckdb/) may offer better query performance.
