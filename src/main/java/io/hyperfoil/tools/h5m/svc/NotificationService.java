package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.entity.NotificationLog;
import io.hyperfoil.tools.h5m.event.ChangeDetectedEvent;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.notification.NotificationPlugin;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Observes {@link ChangeDetectedEvent} and dispatches notifications to
 * configured channels via {@link NotificationPlugin} implementations.
 * <p>
 * Change events arrive pre-enriched with data and fingerprint fields —
 * no additional DB lookups are needed.
 */
@ApplicationScoped
public class NotificationService implements io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface {
    @Inject
    EntityManager em;

    @Inject
    Instance<NotificationPlugin> plugins;

    /**
     * Observes change detected events and dispatches notifications
     * to all enabled notification configs for the folder.
     * <p>
     * The event carries pre-enriched {@link Change} records — no need
     * to load values from the DB.
     */
    @Transactional
    public void onChangeDetected(@Observes ChangeDetectedEvent event) {
        List<Change> changes = event.changes();
        if (changes.isEmpty()) return;

        Change first = changes.getFirst();

        if (!event.dispatch()) {
            Log.debugf("Suppressing notification for node %s (notify=false)", first.nodeName());
            return;
        }

        List<NotificationConfig> configs = NotificationConfig
            .find("folder.id = ?1 AND enabled = true", event.folderId())
            .list();

        if (configs.isEmpty()) {
            return;
        }

        // Resolve folder name
        FolderEntity folder = FolderEntity.findById(event.folderId());
        String folderName = folder != null ? folder.name : "unknown";

        // Dispatch to each configured plugin
        for (NotificationConfig config : configs) {
            findPlugin(config.method).ifPresentOrElse(
                plugin -> {
                    ChangeNotification notification = new ChangeNotification(
                        folderName, event.folderId(), event.rootValueId(),
                        first.nodeId(), first.nodeName(),
                        first.nodeType(), changes, parseConfigJson(config.data), parseConfigJson(config.secrets), config.template
                    );
                    try {
                        plugin.send(notification);
                        logNotification(folder, config, first, changes.size(), "sent", null);
                        Log.infof("Notification sent via %s for %s/%s (%d changes)",
                            config.method, folderName, first.nodeName(), changes.size());
                    } catch (Exception e) {
                        logNotification(folder, config, first, changes.size(), "failed", e.getMessage());
                        Log.errorf(e, "Failed to send %s notification for %s/%s",
                            config.method, folderName, first.nodeName());
                    }
                },
                () -> Log.warnf("No plugin found for notification method '%s'", config.method)
            );
        }
    }

    /**
     * Validates configuration data for a given notification method.
     *
     * @throws IllegalArgumentException if the method is unknown or config is invalid
     */
    @Override
    public void validateConfig(NotificationMethod method, String configData) {
        NotificationPlugin plugin = findPlugin(method)
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification method: " + method));
        plugin.validate(configData);
    }

    private Optional<NotificationPlugin> findPlugin(NotificationMethod method) {
        return plugins.stream()
            .filter(p -> p.method() == method)
            .findFirst();
    }

    /** Parse a JSON config string to JqObject, returning EMPTY for null/blank/non-object. */
    private static JqObject parseConfigJson(String json) {
        if (json == null || json.isBlank()) return JqObject.EMPTY;
        try {
            JqValue parsed = JqValues.parse(json);
            return parsed instanceof JqObject obj ? obj : JqObject.EMPTY;
        } catch (Exception e) {
            return JqObject.EMPTY;
        }
    }

    private void logNotification(FolderEntity folder, NotificationConfig config,
                                  Change change, int changeCount,
                                  String status, String errorMessage) {
        NotificationLog log = new NotificationLog();
        log.folder = folder;
        log.method = config.method.label();
        log.destination = config.data;
        log.status = status;
        log.errorMessage = errorMessage;
        log.nodeId = change.nodeId();
        log.nodeName = change.nodeName();
        log.changeCount = changeCount;
        log.persist();
    }

    @Override
    @Transactional
    public NotificationConfig findByName(long folderId, String name) {
        return NotificationConfig.find("folder.id = ?1 AND name = ?2", folderId, name).firstResult();
    }

    @Override
    @Transactional
    public NotificationConfig findByNameOrId(long folderId, String nameOrId) {
        // Try as id first
        try {
            long id = Long.parseLong(nameOrId);
            NotificationConfig config = NotificationConfig.findById(id);
            if (config != null) return config;
        } catch (NumberFormatException ignored) {
        }
        // Fall back to name lookup within folder
        return findByName(folderId, nameOrId);
    }

    @Override
    @Transactional
    public boolean deleteByNameOrId(long folderId, String nameOrId) {
        NotificationConfig config = findByNameOrId(folderId, nameOrId);
        if (config == null) return false;
        config.delete();
        return true;
    }

    @Override
    @Transactional
    public List<NotificationConfig> listAll() {
        List<NotificationConfig> configs = NotificationConfig.listAll();
        // Eagerly initialize lazy folder.name so it's accessible outside the transaction
        configs.forEach(c -> { if (c.folder != null) { var _ = c.folder.name; } });
        return configs;
    }

    @Override
    @Transactional
    public List<NotificationConfig> listByFolder(long folderId) {
        List<NotificationConfig> configs = NotificationConfig.find("folder.id", folderId).list();
        // Eagerly initialize lazy folder.name so it's accessible outside the transaction
        configs.forEach(c -> { if (c.folder != null) { var _ = c.folder.name; } });
        return configs;
    }

    @Override
    @Transactional
    public NotificationConfig create(long folderId, NotificationMethod method, String name,
                                     String data, String secrets, String template) {
        FolderEntity folder = FolderEntity.findById(folderId);
        if (folder == null) {
            throw new IllegalArgumentException("Folder not found: " + folderId);
        }
        NotificationConfig config = new NotificationConfig(folder, method, data, secrets);
        config.template = template;
        config.persist();
        // Auto-generate name if not provided: "{method}-{id}"
        config.name = name != null ? name : method.label() + "-" + config.id;
        return config;
    }

    @Override
    @Transactional
    public void deleteForFolder(long folderId) {
        em.createNativeQuery("DELETE FROM notification_config WHERE folder_id = :fid")
                .setParameter("fid", folderId).executeUpdate();
        em.createNativeQuery("DELETE FROM notification_log WHERE folder_id = :fid")
                .setParameter("fid", folderId).executeUpdate();
    }

}
