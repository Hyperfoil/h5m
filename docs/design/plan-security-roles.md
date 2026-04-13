# Security & Roles Design for h5m

## Problem

h5m currently has zero security infrastructure — no authentication, no authorization, no user/team model. As we move toward a multi-user system with a REST API, we need to add security without replicating Horreum's complexity.

## Goals

- Simple role model: **admin** (global) and **team member** (binary membership)
- **Two deployment modes**: local (no auth) and service (OIDC + API keys)
- No database-level enforcement (no PostgreSQL RLS)
- **All data is publicly readable** — only write/upload operations require authorization
- Single authorization checkpoint in the application layer

## Deployment Modes

h5m must support two distinct deployment scenarios via Quarkus config profiles:

### Local Mode (developer workstation)

- No authentication required — single user, full access
- No OIDC, no API keys, no login
- Configured via profile: `%dev` or `%local`

```properties
%local.quarkus.oidc.tenant-enabled=false
%local.quarkus.http.auth.permission.default.paths=/*
%local.quarkus.http.auth.permission.default.policy=permit
```

### Service Mode (multi-user)

- OIDC (Keycloak) for web/UI authentication
- API keys for CLI authentication
- Team-based authorization for write operations
- All read endpoints remain public (no auth required)

```properties
quarkus.oidc.auth-server-url=${OIDC_SERVER}
quarkus.oidc.client-id=${OIDC_CLIENT}
quarkus.oidc.credentials.secret=${OIDC_SECRET}
```

### Mode Switching

No code changes needed — purely config-driven via Quarkus profiles. The `AuthorizationService` checks `SecurityIdentity.isAnonymous()` and permits all operations in local mode.

## Horreum Comparison

| Aspect | Horreum | h5m (proposed) |
|--------|---------|----------------|
| Authentication | Keycloak OIDC only | OIDC (auth only) + API keys + no-auth local mode |
| Authorization layers | 5 (filter + augmentor + interceptor + annotation + RLS) | 1 (`AuthorizationService`) |
| Database enforcement | 33 RLS policies, HMAC row signatures | None |
| Role management | Keycloak realm roles | h5m database (OIDC provides auth only) |
| Role granularity | owner, uploader, tester, viewer, machine (per test) | admin, team member |
| Read access | Role-restricted per test | Public — anyone can read all data |
| Ownership | Per-test with role hierarchy | Per-folder with team membership |
| Deployment modes | Service only | Local (no-auth) + Service (OIDC) |

## Entity Model

### New Entities

**Team**
- `id: Long`
- `name: String` (unique)
- `members: List<User>` (`@ManyToMany` join table)

**User**
- `id: Long`
- `username: String` (unique) — mapped from OIDC `preferred_username` claim or API key lookup
- `role: String` — `"admin"` or `"user"`
- `teams: List<Team>` (`@ManyToMany` mapped by Team.members)
- Note: No password field — authentication is handled by OIDC or API keys, not by the DB

**ApiKey**
- `id: Long`
- `keyHash: String` (SHA-256 of the key)
- `user: User`
- `description: String`
- `createdAt: Instant`
- `expiresAt: Instant` (default: 1 year from creation, configurable via `h5m.api-key.expiration-days`)

### Modified Entities

**Folder** — add one field:
- `team: Team` (required — every folder belongs to a team)

All other entities (nodes, values, work) inherit team context from their folder. No changes needed to Node, Value, or Work.

## Authorization Model

A single `AuthorizationService` CDI bean handles all write-access checks:

```java
@ApplicationScoped
public class AuthorizationService {
    boolean isLocalMode();  // true when security is disabled
    boolean isAdmin(SecurityIdentity identity);
    boolean isMemberOfTeam(SecurityIdentity identity, long teamId);
    boolean canModifyFolder(SecurityIdentity identity, Folder folder);
    void requireAdmin(SecurityIdentity identity);
    void requireFolderModify(SecurityIdentity identity, Folder folder);
}
```

### Access Rules

**Read operations — no restrictions:**

| Action | Who can do it |
|--------|---------------|
| List folders | Anyone (no auth required) |
| Read folder data | Anyone |
| Read values / nodes | Anyone |
| Query / search | Anyone |

**Write operations — require authorization (service mode only):**

| Action | Who can do it |
|--------|---------------|
| Create team | Admin |
| Add team members | Any member of that team (or admin) |
| Remove team members | Any member of that team (or admin) |
| Manage users | Admin |
| Create folder | Team member (for their team) or admin |
| Upload to folder | Team member or admin |
| Modify folder (add/edit nodes) | Team member or admin |
| Delete folder | Team member or admin |

**Local mode:** All users are treated as admin — all operations permitted, no team enforcement needed.

### Why Not RLS?

Horreum's 33 RLS policies are the most complex part of its security model. They provide defense-in-depth but at significant cost:
- Every query must set the correct PostgreSQL role via `SET LOCAL ROLE`
- Row-level HMAC signatures must be computed and verified
- Debugging authorization failures requires understanding both application and DB layers
- Schema migrations must update policies in lockstep

For h5m, read access is fully public and write access uses a simple ownership model (folder -> team). Application-level checks in `AuthorizationService` are sufficient and much easier to reason about, test, and debug.

## Authentication

### OIDC (Keycloak) — Service Mode

- Add `quarkus-oidc` extension to `pom.xml`
- OIDC provides authentication only — it verifies "who are you", nothing more
- No role mapping from Keycloak tokens — all roles and team memberships are managed in h5m's own database
- On first OIDC login, auto-provision a `User` from the token's `preferred_username` claim with default role `"user"` and no team memberships
- Admin promotes users and assigns team memberships via `h5m admin` CLI commands

### API Keys — Service Mode (CLI)

- Custom `ApiKeyHttpAuthenticationMechanism` reads `Authorization: Bearer h5m_<key>` header
- Hashes the key with SHA-256, looks up `ApiKey`, resolves to the user's `SecurityIdentity`
- Coexists with OIDC — API key mechanism returns `null` if no `h5m_` prefix, letting OIDC handle it
- API keys are the primary auth method for CLI usage

### CLI Flow

```
# Service mode — authenticate and get API key
$ h5m auth login --url http://h5m.example.com --user admin
Password: ********
API key stored in ~/.h5m/token

$ h5m upload my-folder data.json   # uses stored token

$ h5m auth logout
Token removed.

# Local mode — no auth needed
$ h5m upload my-folder data.json   # just works
```

### Read Endpoints

All GET/read endpoints use `@PermitAll` — no authentication required even in service mode. This means:
- Dashboards and external tools can read data without credentials
- Only upload/modify/delete operations challenge for authentication

## Implementation Phases

### Phase 1: Entity Model + Deployment Modes
- Create `Team`, `User`, `Team`/`User` join table
- Create `TeamService`, `UserService` with basic CRUD
- Add `team` field to `Folder`
- Configure Quarkus profiles for local vs service mode
- CLI command `h5m admin init` — bootstraps first admin user (service mode only)

### Phase 2: OIDC + API Key Authentication
- Add `quarkus-oidc` dependency
- Implement OIDC auto-provisioning (create `User` on first login)
- Create `ApiKey` and `ApiKeyService`
- Implement `ApiKeyHttpAuthenticationMechanism`
- CLI commands: `h5m auth login`, `h5m auth logout`

### Phase 3: Authorization Enforcement
- Implement `AuthorizationService` with local-mode bypass
- Add authorization checks to write operations in `FolderService`, `NodeService`, `ValueService`
- Mark all read endpoints `@PermitAll`
- Mark write endpoints `@Authenticated`
- Admin endpoints `@RolesAllowed("admin")`
- Existing tests continue to work (use local/dev profile — no auth)

### Phase 4: REST API + HTTP Security
- Apply security annotations on REST endpoints as they are added
- `AuthorizationService` checks remain in service layer (team membership is data-dependent)

## Design Decisions

- **Migration path**: On first startup with security enabled, auto-create a team for each existing folder (team name = folder name) and assign it. Admin can merge/reorganize teams afterwards.
- **Local mode**: All users are treated as admin — all operations permitted, no team enforcement needed.
- **Team membership**: Binary (member or not) — no per-team roles. `@ManyToMany` join table, no separate entity.
- **API key expiration**: Configurable via `h5m.api-key.expiration-days`, default 1 year.
- **OIDC**: Authentication only — all roles managed in h5m's database.
- **Read access**: Fully public, no auth required.
- **Team management**: Any team member can add/remove members on their team.

