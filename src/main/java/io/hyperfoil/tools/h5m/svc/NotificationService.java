package io.hyperfoil.tools.h5m.svc;

import com.fasterxml.jackson.databind.JsonNode;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.entity.NotificationLog;
import io.hyperfoil.tools.h5m.entity.ValueEntity;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeDetectedEvent;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.notification.NotificationPlugin;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Observes {@link ChangeDetectedEvent} and dispatches notifications to
 * configured channels via {@link NotificationPlugin} implementations.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    Instance<NotificationPlugin> plugins;

    /**
     * Observes change detected events and dispatches notifications
     * to all enabled notification configs for the folder.
     */
    @Transactional
    public void onChangeDetected(@Observes ChangeDetectedEvent event) {
        if (!event.dispatch()) {
            Log.debugf("Suppressing notification for node %s (notify=false)", event.nodeName());
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

        // Resolve node type from one of the values
        String nodeType = resolveNodeType(event.valueIds());

        // Enrich with value data
        List<ChangeDetail> details = loadChangeDetails(event.valueIds());

        // Dispatch to each configured plugin
        for (NotificationConfig config : configs) {
            findPlugin(config.method).ifPresentOrElse(
                plugin -> {
                    ChangeNotification notification = new ChangeNotification(
                        folderName, event.nodeId(), event.nodeName(),
                        nodeType, details, config.data, config.secrets, config.template
                    );
                    try {
                        plugin.send(notification);
                        logNotification(folder, config, event, details.size(), "sent", null);
                        Log.infof("Notification sent via %s for %s/%s (%d changes)",
                            config.method, folderName, event.nodeName(), details.size());
                    } catch (Exception e) {
                        logNotification(folder, config, event, details.size(), "failed", e.getMessage());
                        Log.errorf(e, "Failed to send %s notification for %s/%s",
                            config.method, folderName, event.nodeName());
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

    private List<ChangeDetail> loadChangeDetails(List<Long> valueIds) {
        List<ChangeDetail> details = new ArrayList<>();
        for (Long valueId : valueIds) {
            ValueEntity value = ValueEntity.findById(valueId);
            if (value != null) {
                JsonNode fingerprint = null;
                if (value.data != null && value.data.has("fingerprint")) {
                    fingerprint = value.data.get("fingerprint");
                }
                details.add(new ChangeDetail(valueId, value.data, fingerprint));
            }
        }
        return details;
    }

    private String resolveNodeType(List<Long> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) return "unknown";
        ValueEntity value = ValueEntity.findById(valueIds.getFirst());
        if (value != null && value.node != null) {
            return value.node.type().display();
        }
        return "unknown";
    }

    private void logNotification(FolderEntity folder, NotificationConfig config,
                                  ChangeDetectedEvent event, int changeCount,
                                  String status, String errorMessage) {
        NotificationLog log = new NotificationLog();
        log.folder = folder;
        log.method = config.method.label();
        log.destination = config.data;
        log.status = status;
        log.errorMessage = errorMessage;
        log.nodeId = event.nodeId();
        log.nodeName = event.nodeName();
        log.changeCount = changeCount;
        log.persist();
    }
}
