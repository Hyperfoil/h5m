---
title: Getting Started
weight: 10
description: Install h5m and run it for the first time.
draft: false
---

This guide walks you through building h5m from source, running the server, and verifying everything works with a simple data upload.

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java | 25+ |
| Maven | 3.9.0+ |
| Git | Any recent version |

## Clone the Repository

```bash
git clone https://github.com/hyperfoil/h5m.git
cd h5m
```

## Build Options

h5m supports multiple build profiles depending on how you want to run it.

### Web Server (default)

Builds h5m as a Quarkus web application with a REST API and browser UI:

```bash
mvn clean package
```

The output JAR is at `target/h5m.jar`.

### CLI Tool

Builds a command-line interface. By default the CLI is compiled to a native binary:

```bash
mvn clean package -Pcli
```

The native binary is at `target/cli/h5m`. To build a JAR instead (faster build, requires a Java runtime):

```bash
mvn clean package -Pcli -Dh5m.cli.native=false
```

The JAR is at `target/cli/h5m.jar`. Run it with `java -jar target/cli/h5m.jar`.

## Run the Server

```bash
java -jar target/h5m.jar
```

By default h5m:
- Listens on **http://localhost:8080**
- Connects to **PostgreSQL** (the default datasource for the web server)

You must have a PostgreSQL instance available and configure the connection via system properties or `application.properties`:

```bash
java \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/h5m \
  -Dquarkus.datasource.username=h5m \
  -Dquarkus.datasource.password=h5m \
  -jar target/h5m.jar
```

> **CLI mode** uses SQLite by default (stored at `~/h5m.db`, overridable with `H5M_PATH`). The web server does not use SQLite by default.

## Verify the Server is Running

```bash
curl http://localhost:8080/api/folder
```

Expected response:

```json
[]
```

An empty array means the server is up and no folders exist yet.

## Run in Dev Mode

For local development, Quarkus dev mode gives you live reload:

```bash
mvn quarkus:dev
```

The UI is available at `http://localhost:8080` and the API at `http://localhost:8080/api/`.

## Next Steps

- Follow the [CLI Quickstart](../cli-quickstart/) for a full end-to-end walkthrough
- Learn how to [upload data](../uploading-data/) from benchmark runs
- Read about [core concepts](../../concepts/) to understand Folders, Nodes, and Values
