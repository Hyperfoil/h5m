# Notification System for Change Detection

## Summary

Implement a notification system that dispatches alerts when change detection nodes (FixedThreshold, RelativeDifference) detect regressions. Should support multiple notification channels and be usable from CLI, REST API, and a future web frontend.

Tracked in [issue #89](https://github.com/Hyperfoil/h5m/issues/89). Supersedes the notification sketch in [phase4-notifications.md](phase4-notifications.md).

## Current State

h5m fires a `ChangeDetectedEvent` CDI event from `WorkService.execute()` when a detection node produces values. There are no production consumers — only a test observer exists. The CDI event carries `nodeId`, `nodeName`, and `valueIds`.

```
WorkService.execute()
  └─ if activeNode.isDetection() && !newOrUpdated.isEmpty()
       └─ changeDetectedEvent.fire(new ChangeDetectedEvent(...))
            └─ [test only] ChangeDetectedEventObserver
            └─ [production] no observer yet
```

## Design

### NotificationMethod Enum

Supported notification methods are defined as an enum for type safety:

```java
public enum NotificationMethod {
    WEBHOOK("webhook"),
    EMAIL("email"),
    SLACK("slack"),
    GITHUB_ISSUE("github-issue");
}
```

### NotificationPlugin SPI

A single SPI covers all notification channels. Horreum splits into NotificationPlugin (email) and ActionPlugin (webhooks, Slack, GitHub). h5m uses one interface to keep the architecture simple:

```java
public interface NotificationPlugin {
    /** The notification method this plugin handles */
    NotificationMethod method();

    /** Send a change notification */
    void send(ChangeNotification notification);

    /** Validate configuration data before saving (throw on invalid) */
    void validate(String configData);
}
```

Plugins are `@ApplicationScoped` CDI beans. CDI `Instance<NotificationPlugin>` auto-discovers them — no explicit registration needed. Adding a new channel means adding one new bean implementing `NotificationPlugin`.

### ChangeNotification Record

Enriched event data passed to plugins:

```java
public record ChangeNotification(
    String folderName,
    long nodeId,
    String nodeName,
    String nodeType,            // "ft" or "rd"
    List<ChangeDetail> changes,
    String configData,          // plugin-specific config (URL, email, channel)
    String configSecrets,       // plugin-specific secrets (API tokens, passwords)
    String template             // user-defined message template, or null for default
) {}

public record ChangeDetail(
    long valueId,
    JsonNode data,              // detection value data (ratio, bound, direction, fingerprint)
    JsonNode fingerprint
) {}
```

### NotificationConfig Entity

Per-folder notification configuration with separate fields for config, secrets, and message template:

```java
@Entity(name = "notification_config")
public class NotificationConfig extends PanacheEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    public FolderEntity folder;

    @Enumerated(EnumType.STRING)
    public NotificationMethod method;

    public String data;         // non-sensitive JSON config: URL, email, channel
    public String secrets;      // sensitive JSON: API tokens, passwords (never returned in API)
    public String template;     // user-defined message template with placeholders
    public boolean enabled = true;
}
```

#### Configuration Fields

| Field | Purpose | Example |
|-------|---------|---------|
| `data` | Non-sensitive plugin config | `{"url": "https://hooks.slack.com/..."}` |
| `secrets` | Sensitive credentials (never exposed in API responses) | `{"token": "xoxb-..."}` |
| `template` | Custom message with placeholders | `"Regression in *{folderName}*: {changeCount} changes. cc @perf-team"` |

#### Template Placeholders

| Placeholder | Value |
|------------|-------|
| `{folderName}` | Name of the folder |
| `{nodeName}` | Name of the detection node |
| `{nodeType}` | Type of detection ("ft" or "rd") |
| `{changeCount}` | Number of changes detected |
| `{changes}` | Formatted list of change details |
| `{fingerprint}` | Fingerprint data from the detection |

If `template` is null or empty, plugins use their default message format.

### NotificationLog Entity

Stores sent notification history for the web UI and debugging:

```java
@Entity(name = "notification_log")
public class NotificationLog extends PanacheEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    public FolderEntity folder;

    public String method;
    public String destination;      // resolved target (email, URL, channel)
    public String status;           // "sent", "failed", "suppressed"
    public String errorMessage;     // null on success
    public long nodeId;
    public String nodeName;
    public int changeCount;
    @CreationTimestamp
    private LocalDateTime sentAt;
}
```

### NotificationService

Observes `ChangeDetectedEvent`, loads configs for the folder, enriches with value data, and dispatches to matching plugins:

```java
@ApplicationScoped
public class NotificationService {

    @Inject
    Instance<NotificationPlugin> plugins;

    @Inject
    ValueService valueService;

    @Inject
    EntityManager em;

    void onChangeDetected(@Observes ChangeDetectedEvent event) {
        if (!event.notify()) return;  // suppress for recalculate/bulk import

        List<NotificationConfig> configs = NotificationConfig
            .find("folder.id = ?1 AND enabled = true", event.folderId())
            .list();

        if (configs.isEmpty()) return;

        // Enrich with value data
        List<ChangeDetail> details = loadChangeDetails(event.valueIds());

        // Dispatch to each configured plugin
        for (NotificationConfig config : configs) {
            findPlugin(config.method).ifPresent(plugin -> {
                ChangeNotification notification = new ChangeNotification(
                    folderName, event.nodeId(), event.nodeName(),
                    nodeType, details, config.data
                );
                try {
                    plugin.send(notification);
                    logNotification(config, event, "sent", null);
                } catch (Exception e) {
                    logNotification(config, event, "failed", e.getMessage());
                }
            });
        }
    }

    private Optional<NotificationPlugin> findPlugin(String method) {
        return plugins.stream()
            .filter(p -> p.method().equals(method))
            .findFirst();
    }
}
```

### ChangeDetectedEvent Enhancement

Add `folderId` (for config lookup) and `notify` flag (for suppression):

```java
public record ChangeDetectedEvent(
    long folderId, long nodeId, String nodeName,
    List<Long> valueIds, boolean notify
) {}
```

Normal uploads set `notify=true`. Recalculate and bulk imports set `notify=false`.

### Suppression Policy

- **Normal upload**: `notify=true` — external notifications are dispatched
- **Recalculate**: `notify=false` — administrative operation, don't re-alert for known regressions
- **Bulk import (load-legacy-runs)**: `notify=false` — importing historical data should not spam
- **In-app (SSE)**: Always delivered regardless of `notify` flag — the web UI should show all changes in real-time

## Web Frontend Considerations

The notification system must support a future web frontend where most users will interact:

### In-app Notifications (SSE/WebSocket)

The `NotificationService` should broadcast change events to connected browsers via an SSE endpoint. This is the real-time "bell icon" experience — separate from external channels like email/Slack:

```java
@GET
@Path("/stream")
@Produces(MediaType.SERVER_SENT_EVENTS)
@RestStreamElementType(MediaType.APPLICATION_JSON)
public Multi<ChangeNotification> streamChanges() {
    // Return a Multi that emits ChangeNotification events
    // from a BroadcastProcessor fed by NotificationService
}
```

In-app notifications bypass the `notify` flag — connected browsers should always see changes, even during recalculation.

### Notification History Page

The `NotificationLog` entity enables a web UI notification history page showing:
- What was sent, when, to which channel
- Success/failure status
- Link to the detection values in the Value DAG

### REST API for Configuration

CRUD endpoints for `NotificationConfig` so the web UI can manage notification preferences per folder:

```
POST   /api/notification/config              — create config
GET    /api/notification/config?folderId=N    — list configs for folder
PUT    /api/notification/config/{id}          — update config
DELETE /api/notification/config/{id}          — delete config
POST   /api/notification/config/{id}/test     — send a test notification
GET    /api/notification/log?folderId=N       — notification history
```

The CLI commands are thin wrappers around these same REST endpoints.

### User Preferences (Future)

When multi-user is fully adopted, a `NotificationPreference` entity decouples user contact details from per-folder configuration:

```java
@Entity(name = "notification_preference")
public class NotificationPreference extends PanacheEntity {
    @ManyToOne
    public User user;
    public String method;       // "email", "slack"
    public String data;         // email address, Slack handle
}
```

A user sets their email/Slack once globally, then subscribes to folders via a Watch-like entity. This is similar to Horreum's Watch + NotificationSettings split but deferred until there is a concrete multi-user need.

## Notification Channels

| Channel | Plugin | Config data | Description |
|---------|--------|------------|-------------|
| **Webhook** | `WebhookPlugin` | `{"url": "https://..."}` | HTTP POST with JSON payload. Covers Slack incoming webhooks, generic receivers. |
| **Email** | `EmailPlugin` | `{"to": "team@example.com"}` | SMTP via `quarkus-mailer`. |
| **GitHub Issue** | `GitHubIssuePlugin` | `{"owner": "org", "repo": "perf", "token": "..."}` | Creates/comments on GitHub issues. |
| **Slack** | `SlackPlugin` | `{"channel": "#alerts", "token": "xoxb-..."}` | Slack Web API with Block Kit formatting. |
| **In-app (SSE)** | Built-in | N/A | Broadcasts to connected web clients. |

## Implementation Phases

Each phase is a separate PR.

### Phase 1: Core Infrastructure

- `NotificationPlugin` SPI interface
- `ChangeNotification` and `ChangeDetail` records
- `NotificationConfig` entity
- `NotificationLog` entity
- `NotificationService` (CDI observer + dispatcher)
- `ChangeDetectedEvent` enhancement (add `folderId`, `notify` flag)
- Update `WorkService.execute()` to pass `folderId` and `notify`
- REST endpoints for config CRUD and notification history
- CLI commands: `add notification`, `list notifications`, `remove notification`

### Phase 2: Webhook Plugin

- `WebhookPlugin` implementation (HTTP POST with JSON)
- Tests with mock HTTP server (WireMock or similar)

### Phase 3: Email Plugin

- `EmailPlugin` implementation (Quarkus reactive mailer)
- Qute templates for email body formatting
- Requires `quarkus-mailer` dependency

### Phase 4: GitHub Issue Plugin

- `GitHubIssuePlugin` implementation (GitHub REST API)
- Create issue on first detection, comment on subsequent detections for same fingerprint

### Phase 5: Slack Plugin

- `SlackPlugin` implementation (Slack Web API)
- Block Kit message formatting for rich notifications

### Phase 6: In-app Notifications (Web Frontend)

- SSE endpoint streaming `ChangeNotification` events
- `BroadcastProcessor` in `NotificationService` feeding the SSE stream
- Notification badge/bell component data source
- Notification history page backed by `NotificationLog`

Phases 2-6 are independent of each other and can be implemented in any order after Phase 1.

## Files to Create/Modify

| Phase | File | Action |
|-------|------|--------|
| 1 | `svc/NotificationPlugin.java` | Create — SPI interface |
| 1 | `event/ChangeNotification.java` | Create — enriched notification record |
| 1 | `event/ChangeDetail.java` | Create — per-change detail record |
| 1 | `entity/NotificationConfig.java` | Create — JPA entity |
| 1 | `entity/NotificationLog.java` | Create — JPA entity |
| 1 | `svc/NotificationService.java` | Create — CDI observer + dispatcher |
| 1 | `event/ChangeDetectedEvent.java` | Modify — add folderId, notify flag |
| 1 | `svc/WorkService.java` | Modify — pass folderId and notify flag |
| 1 | `rest/NotificationResource.java` | Create — REST endpoints |
| 1 | `cli/AddNotification.java` | Create — CLI command |
| 1 | `cli/ListNotifications.java` | Create — CLI command |
| 1 | `cli/RemoveNotification.java` | Create — CLI command |
| 2 | `notification/WebhookPlugin.java` | Create |
| 3 | `notification/EmailPlugin.java` | Create |
| 4 | `notification/GitHubIssuePlugin.java` | Create |
| 5 | `notification/SlackPlugin.java` | Create |
| 6 | `rest/NotificationStreamResource.java` | Create — SSE endpoint |

## Key Design Differences from Horreum

| Aspect | Horreum | h5m |
|--------|---------|-----|
| SPIs | Two (NotificationPlugin + ActionPlugin) | One (NotificationPlugin) |
| Subscription | Watch + NotificationSettings (per-user/team) | NotificationConfig (per-folder initially) |
| Event aggregation | 1-second delay to batch changes | No batching (changes already per-upload) |
| Mediation | ServiceMediator pattern | Direct CDI @Observes |
| Channels | Email (notifications) + HTTP/Slack/GitHub (actions) | All via single SPI |
| Notification log | None | NotificationLog entity for history + debugging |
| Web support | Full React UI | Planned — SSE endpoint + notification log |
| User preferences | Global per-user NotificationSettings | Deferred until multi-user is adopted |
