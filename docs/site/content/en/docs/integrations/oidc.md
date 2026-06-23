---
title: OIDC Authentication
linkTitle: OIDC
weight: 40
description: Configuring OpenID Connect for user authentication in h5m service deployments.
draft: false
---

h5m supports two operational modes: **local mode** (no authentication, default) and **service mode** (OIDC-based authentication for multi-user deployments). This page covers configuring OIDC and understanding h5m's authorization model.

## Modes

### Local Mode (Default)

Security is disabled by default. All API endpoints are accessible without credentials:

```properties
h5m.security.enabled=false
```

This is the recommended mode for CLI use, local development, and single-user deployments.

### Service Mode

Enable security to require authentication on write operations:

```properties
h5m.security.enabled=true
```

In service mode:
- All **read endpoints** remain public (`@PermitAll`) — no token needed to fetch folders or values
- All **write endpoints** (create, upload, delete) require a valid OIDC token or API key

## Configuring OIDC

h5m uses [Quarkus OIDC](https://quarkus.io/guides/security-oidc-bearer-token-authentication) as the authentication layer. Any OIDC-compliant provider works — Keycloak, Auth0, Okta, Dex, etc.

```bash
export H5M_SECURITY_ENABLED=true
export QUARKUS_OIDC_TENANT_ENABLED=true
export QUARKUS_OIDC_AUTH_SERVER_URL=https://keycloak.example.com/realms/h5m
export QUARKUS_OIDC_CLIENT_ID=h5m
export QUARKUS_OIDC_CREDENTIALS_SECRET=your-client-secret
java -jar target/h5m.jar
```

Or in `application.properties`:

```properties
h5m.security.enabled=true
quarkus.oidc.tenant-enabled=true
quarkus.oidc.auth-server-url=https://keycloak.example.com/realms/h5m
quarkus.oidc.client-id=h5m
quarkus.oidc.credentials.secret=your-client-secret
quarkus.oidc.application-type=service
quarkus.oidc.token.principal-claim=preferred_username
```

The `preferred_username` claim from the OIDC token is used as the h5m username. This claim is standard in Keycloak; other providers may use `sub` or `email` — adjust `token.principal-claim` accordingly.

## Authorization Model

OIDC handles **authentication only** — h5m manages its own **authorization** via roles and teams stored in its database.

| Role | Permissions |
|------|-------------|
| `admin` | Full access to all folders and admin operations |
| `user` | Access scoped to folders owned by their team |

On first login via OIDC, a user record is created automatically. An admin must then assign the user to a team or grant admin rights.

## API Keys

As an alternative to OIDC bearer tokens, h5m supports long-lived API keys — useful for CI pipelines and scripts.

### Create an API Key (Admin)

```bash
h5m admin create-api-key <username> --description "CI pipeline"
```

Returns a key of the form `H5M_<UUID_WITH_UNDERSCORES>` (e.g. `H5M_550E8400_E29B_41D4_A716_446655440000`). Store it securely — it is shown only once.

### Use an API Key

Pass the key as a Bearer token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer H5M_550E8400_E29B_41D4_A716_446655440000" \
  -X POST http://localhost:8080/api/folder/my-benchmarks/upload \
  -H "Content-Type: application/json" \
  -d @result.json
```

### Key Properties

| Property | Value |
|----------|-------|
| Format | `H5M_<UUID_WITH_UNDERSCORES>` (dashes replaced by `_`, uppercase) |
| Hash algorithm | SHA-256 (key is stored hashed) |
| Default expiration | 365 days (configurable via `h5m.api-key.expiration-days`) |

### Manage API Keys (Admin)

```bash
h5m admin list-api-keys <username>
h5m admin revoke-api-key <keyId>
```

## Admin Operations

User and team management via the CLI (requires admin role):

```bash
h5m admin create-user <username>
h5m admin create-team <teamname>
h5m admin add-member <username> <teamname>
h5m admin list-users
h5m admin list-teams
```

## Disabling Proactive Auth

h5m sets `quarkus.http.auth.proactive=false` so that unauthenticated requests can still reach public (`@PermitAll`) endpoints without triggering a 401 challenge. This is required for the read-public, write-protected model to work correctly.
