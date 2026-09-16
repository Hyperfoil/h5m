---
title: Single-JAR Mode
weight: 10
description: Running h5m as a self-contained executable JAR with no external dependencies.
draft: false
---

Single-JAR mode is the simplest way to run h5m. The build produces one uber-JAR that contains all dependencies. Combined with SQLite as the database backend, h5m requires only a Java 25 runtime — no PostgreSQL, no message broker, no external services.

## Build

```bash
git clone https://github.com/hyperfoil/h5m.git
cd h5m
mvn clean package
```

The output JAR is at:

```
target/h5m.jar
```

This is an **uber-JAR** (`quarkus.package.jar.type=uber-jar`) — all dependencies are bundled inside. No runner suffix is added (`quarkus.package.jar.add-runner-suffix=false`).

## Run with SQLite (No External Dependencies)

```bash
export QUARKUS_DATASOURCE_DB_KIND=sqlite
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/home/user/h5m.db
java -jar target/h5m.jar
```

h5m starts, creates the schema on first run, and begins serving on **http://localhost:8080**.

### Shorthand with `H5M_PATH`

Some run configurations recognise `H5M_PATH` as a shorthand for the SQLite file path:

```bash
H5M_PATH=/data/h5m.db java -jar target/h5m.jar
```

## Run with PostgreSQL

```bash
export QUARKUS_DATASOURCE_DB_KIND=postgresql
export QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/h5m
export QUARKUS_DATASOURCE_USERNAME=h5m
export QUARKUS_DATASOURCE_PASSWORD=secret
java -jar target/h5m.jar
```

## Build the CLI

The CLI is a separate build profile:

```bash
mvn clean package -Pcli
```

Output: `target/cli/h5m`

```bash
./target/cli/h5m list folder
```

The CLI binary connects to a local database file directly (no running server needed when using SQLite) or to a remote h5m server via REST.

## Native Binary (Default for CLI)

The CLI profile builds a GraalVM native executable by default (`h5m.cli.native=true`):

```bash
mvn clean package -Pcli
```

To build a JVM JAR instead (faster build, requires Java 25 runtime):

```bash
mvn clean package -Pcli -Dh5m.cli.native=false
```

The native binary has no JVM dependency and starts significantly faster.

## JVM Flags

For production deployments, tune the JVM to your workload:

```bash
java \
  -Xms256m \
  -Xmx1g \
  -XX:+UseG1GC \
  -jar target/h5m.jar
```

## Startup Verification

After starting, confirm the server is healthy:

```bash
curl http://localhost:8080/api/folder
# Expected: []
```

## Port Configuration

Change the default port (8080) via:

```bash
java -Dquarkus.http.port=9090 -jar target/h5m.jar
# or
export QUARKUS_HTTP_PORT=9090
java -jar target/h5m.jar
```

## Running as a System Service

To run h5m as a `systemd` service on Linux:

```ini
# /etc/systemd/system/h5m.service
[Unit]
Description=h5m Benchmark Data Repository
After=network.target

[Service]
Type=simple
User=h5m
Environment=QUARKUS_DATASOURCE_DB_KIND=sqlite
Environment=QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/var/lib/h5m/h5m.db
ExecStart=/usr/bin/java -jar /opt/h5m/h5m.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now h5m
```
