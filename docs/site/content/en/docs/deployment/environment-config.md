---
title: Environment Configuration
weight: 30
description: Complete reference for environment variables and configuration options in h5m.
draft: false
---

h5m is configured via [Quarkus configuration](https://quarkus.io/guides/config-reference). All `application.properties` keys can be overridden at runtime as environment variables by uppercasing the key and replacing `.` and `-` with `_`.

For example:
```
quarkus.datasource.jdbc.url  →  QUARKUS_DATASOURCE_JDBC_URL
h5m.security.enabled         →  H5M_SECURITY_ENABLED
```

## Database

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `QUARKUS_DATASOURCE_DB_KIND` | `postgresql` | Backend type: `postgresql`, `sqlite`, or `duckdb` |
| `QUARKUS_DATASOURCE_JDBC_URL` | _(none)_ | JDBC connection URL |
| `QUARKUS_DATASOURCE_USERNAME` | _(none)_ | Database username (PostgreSQL) |
| `QUARKUS_DATASOURCE_PASSWORD` | _(none)_ | Database password (PostgreSQL) |
| `QUARKUS_DATASOURCE_DB_VERSION` | _(none)_ | Required for DuckDB: `1.0.0` |
| `QUARKUS_DATASOURCE_JDBC_MAX_SIZE` | `10` | Maximum connection pool size |
| `QUARKUS_DATASOURCE_JDBC_MIN_SIZE` | `1` | Minimum connection pool size |
| `QUARKUS_DATASOURCE_JDBC_ACQUISITION_TIMEOUT` | `PT30S` | Max wait for a connection (ISO 8601 duration) |

### SQLite URL examples

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/home/user/h5m.db
QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite::memory:
```

### PostgreSQL URL example

```bash
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/h5m
```

## Security

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `H5M_SECURITY_ENABLED` | `false` | Enable authentication and authorization |
| `H5M_API_KEY_EXPIRATION_DAYS` | `365` | Lifetime of generated API keys in days |
| `QUARKUS_HTTP_AUTH_PROACTIVE` | `false` | Set to `false` to allow public endpoints without auth challenge |

## OIDC

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `QUARKUS_OIDC_TENANT_ENABLED` | `false` | Enable OIDC authentication |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | _(none)_ | OIDC provider URL (e.g. Keycloak realm URL) |
| `QUARKUS_OIDC_CLIENT_ID` | `h5m` | OIDC client ID registered with the provider |
| `QUARKUS_OIDC_CREDENTIALS_SECRET` | _(none)_ | OIDC client secret |
| `QUARKUS_OIDC_APPLICATION_TYPE` | `service` | Application type — keep as `service` for API use |
| `QUARKUS_OIDC_TOKEN_PRINCIPAL_CLAIM` | `preferred_username` | JWT claim used as the h5m username |

## HTTP Server

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `QUARKUS_HTTP_PORT` | `8080` | HTTP listen port |
| `QUARKUS_HTTP_HOST` | `0.0.0.0` | HTTP bind address |
| `QUARKUS_HTTP_CORS` | `false` | Enable CORS (auto-enabled in dev profile) |
| `QUARKUS_HTTP_CORS_ORIGINS` | _(none)_ | Allowed CORS origins (regex supported) |

## Notifications

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `H5M_SLACK_API_URL` | `https://slack.com/api/chat.postMessage` | Slack API endpoint for notifications |
| `MAILER_FROM` | `h5m@localhost` | Sender address for email notifications |
| `MAILER_HOST` | `localhost` | SMTP server host |
| `MAILER_PORT` | `25` | SMTP server port |
| `QUARKUS_MAILER_MOCK` | `true` | Set to `false` to send real emails |

## Logging

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `QUARKUS_LOG_LEVEL` | `INFO` | Root log level |
| `QUARKUS_LOG_CATEGORY__IO_QUARKUS__LEVEL` | `ERROR` | Quarkus framework log level |
| `QUARKUS_LOG_CATEGORY__ORG_HIBERNATE__LEVEL` | `ERROR` | Hibernate log level |
| `QUARKUS_HIBERNATE_ORM_LOG_QUERIES_SLOWER_THAN_MS` | `100` | Log queries slower than N ms |

## Transactions

| Environment Variable | Default | Description |
|----------------------|---------|-------------|
| `QUARKUS_TRANSACTION_MANAGER_DEFAULT_TRANSACTION_TIMEOUT` | `PT10M` | Max transaction duration (ISO 8601) |

## Minimal Production Configuration

A minimal set of environment variables for a production PostgreSQL deployment with OIDC:

```bash
# Database
QUARKUS_DATASOURCE_DB_KIND=postgresql
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db.internal:5432/h5m
QUARKUS_DATASOURCE_USERNAME=h5m
QUARKUS_DATASOURCE_PASSWORD=<secret>

# Security
H5M_SECURITY_ENABLED=true
QUARKUS_OIDC_TENANT_ENABLED=true
QUARKUS_OIDC_AUTH_SERVER_URL=https://keycloak.example.com/realms/h5m
QUARKUS_OIDC_CLIENT_ID=h5m
QUARKUS_OIDC_CREDENTIALS_SECRET=<secret>
```

## Minimal Local / CLI Configuration

```bash
QUARKUS_DATASOURCE_DB_KIND=sqlite
QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/home/user/h5m.db
```
