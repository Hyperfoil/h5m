package io.hyperfoil.tools.h5m.svc;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.Notification;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationChannelEntity;
import io.hyperfoil.tools.h5m.entity.NotificationEntity;
import io.hyperfoil.tools.h5m.entity.mapper.ApiMapper;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.notification.NotificationPlugin;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

/**
 * Observes {@link ChangeEvent} and dispatches notifications to
 * configured channels via {@link NotificationPlugin} implementations.
 * <p>
 * Change events arrive pre-enriched with data and fingerprint fields —
 * no additional DB lookups are needed.
 */
@ApplicationScoped
public class NotificationService implements NotificationServiceInterface {
    @Inject
    EntityManager em;

    @Inject
    ApiMapper apiMapper;

    @Inject
    Instance<NotificationPlugin> plugins;

    /**
     * Observes change detected events and dispatches notifications
     * to all enabled notification channels for the folder.
     * <p>
     * The event carries pre-enriched {@link Change} records — no need
     * to load values from the DB.
     */
    @Transactional
    public void onChangeDetected(@Observes ChangeEvent event) {
        List<Change> changes = event.changes();
        if (changes.isEmpty()) return;

        Change first = changes.getFirst();

        if (!event.dispatch()) {
            Log.debugf("Suppressing notification for node %s (notify=false)", first.nodeName());
            return;
        }

        List<NotificationChannelEntity> channels = NotificationChannelEntity
            .find("folder.id = ?1 AND enabled = true", event.folderId())
            .list();

        if (channels.isEmpty()) {
            return;
        }

        FolderEntity folder = FolderEntity.findById(event.folderId());

        // Dispatch through each configured channel's plugin
        for (NotificationChannelEntity channel : channels) {
            findPlugin(channel.method).ifPresentOrElse(
                plugin -> {
                    try {
                        plugin.send(event, channel.getConfig(), channel.getSecret(), channel.template);
                        logNotification(folder, channel, first, changes.size(), Notification.Status.SENT, null);
                        Log.infof("Notification sent via %s for %s/%s (%d changes)", channel.method, event.folderName(), first.nodeName(), changes.size());
                    } catch (Exception e) {
                        logNotification(folder, channel, first, changes.size(), Notification.Status.FAILED, e.getMessage());
                        Log.errorf(e, "Failed to send %s notification for %s/%s", channel.method, event.folderName(), first.nodeName());
                    }
                },
                () -> Log.warnf("No plugin found for notification method '%s'", channel.method)
            );
        }
    }

    private Optional<NotificationPlugin> findPlugin(NotificationMethod method) {
        return plugins.stream()
            .filter(p -> p.method() == method)
            .findFirst();
    }

    private void logNotification(FolderEntity folder, NotificationChannelEntity channel,
                                  Change change, int changeCount,
                                  Notification.Status status, String errorMessage) {
        NotificationEntity log = new NotificationEntity();
        log.folder = folder;
        log.method = channel.method;
        log.channel = channel;
        log.status = status;
        log.errorMessage = errorMessage;
        log.nodeId = change.nodeId();
        log.nodeName = change.nodeName();
        log.changeCount = changeCount;
        log.persist();
    }

    /**
     * Validates a user-supplied channel name: not blank, and unique within the folder.
     * Names are looked up by {@link #findEntity}, so a blank or duplicate name makes a channel unaddressable.
     *
     * @param excludeId the channel being renamed, so it does not collide with itself; {@code null} on create
     * @throws BadRequestException if the name is blank or another channel in the folder already has the name
     */
    private void checkName(String name, long folderId, Long excludeId) {
        if (name.isBlank()) {
            throw new BadRequestException("Notification channel name cannot be blank");
        }
        NotificationChannelEntity clash = NotificationChannelEntity.find("folder.id = ?1 AND name = ?2", folderId, name).firstResult();
        if (clash != null && !clash.id.equals(excludeId)) {
            throw new BadRequestException("Notification channel already exists in folder " + folderId + ": " + name);
        }
    }

    /** Resolve a channel entity by name or id within a folder (internal, entity-typed). */
    private NotificationChannelEntity findEntity(long folderId, String nameOrId) {
        try {
            long id = Long.parseLong(nameOrId);
            NotificationChannelEntity channel = NotificationChannelEntity.findById(id);
            if (channel != null) return channel;
        } catch (NumberFormatException ignored) {
        }
        return NotificationChannelEntity.find("folder.id = ?1 AND name = ?2", folderId, nameOrId).firstResult();
    }

    @Override
    @Transactional
    public NotificationChannel findChannelByName(long folderId, String name) {
        return apiMapper.toNotificationChannel(NotificationChannelEntity.find("folder.id = ?1 AND name = ?2", folderId, name).firstResult());
    }

    @Override
    @Transactional
    public NotificationChannel findChannel(long folderId, String nameOrId) {
        return apiMapper.toNotificationChannel(findEntity(folderId, nameOrId));
    }

    @Override
    @Transactional
    public boolean deleteChannel(long id) {
        return NotificationChannelEntity.deleteById(id);
    }

    @Override
    @Transactional
    public List<NotificationChannel> allChannels() {
        return NotificationChannelEntity.<NotificationChannelEntity>listAll().stream()
            .map(apiMapper::toNotificationChannel)
            .toList();
    }

    @Override
    @Transactional
    public List<NotificationChannel> channelsByFolder(long folderId) {
        return NotificationChannelEntity.<NotificationChannelEntity>find("folder.id", folderId).list().stream()
            .map(apiMapper::toNotificationChannel)
            .toList();
    }

    @Override
    @Transactional
    public NotificationChannel createChannel(long folderId, String name, NotificationMethod method,
                                             NotificationConfiguration config, NotificationSecret secret,
                                             String template, Boolean enabled) {
        FolderEntity folder = FolderEntity.findById(folderId);
        if (folder == null) {
            throw new NotFoundException("Folder not found: " + folderId);
        }
        if (name != null) checkName(name, folderId, null);
        NotificationChannelEntity entity = new NotificationChannelEntity(folder, method, config, secret);
        entity.template = template;
        entity.enabled = enabled == null || enabled;
        entity.persist();
        // Auto-generate name if not provided: "{method}-{id}"
        entity.name = name != null ? name : method.label() + "-" + entity.id;
        return apiMapper.toNotificationChannel(entity);
    }

    @Override
    @Transactional
    public NotificationChannel updateChannel(long id, NotificationChannel channel) {
        NotificationChannelEntity entity = NotificationChannelEntity.findById(id);
        if (entity == null) return null;
        if (channel.config() != null && channel.config().method() != entity.method) {
            // The method is immutable once created and is derived from the config's discriminator. A config whose type differs would repoint the channel at a different plugin, leaving stored config/secret incompatible. Reject it.
            throw new BadRequestException("Notification channel method cannot be changed (is " + entity.method + ", requested " + channel.config().method() + ")");
        }
        if (channel.name() != null) {
            checkName(channel.name(), entity.folder.id, id);
            entity.name = channel.name();
        }
        if (channel.config() != null) entity.setConfig(channel.config());
        if (channel.secret() != null) entity.setSecret(channel.secret());
        if (channel.template() != null) entity.template = channel.template();
        if (channel.enabled() != null) entity.enabled = channel.enabled();
        return apiMapper.toNotificationChannel(entity);
    }

    @Override
    @Transactional
    public List<Notification> sentNotifications(long folderId, int limit) {
        return NotificationEntity.<NotificationEntity>find("folder.id = ?1 ORDER BY sentAt DESC", folderId)
            .page(0, limit)
            .list().stream()
            .map(apiMapper::toNotification)
            .toList();
    }

    @Override
    @Transactional
    public void deleteChannelsForFolder(long folderId) {
        em.createNativeQuery("DELETE FROM notification WHERE folder_id = :fid")
                .setParameter("fid", folderId).executeUpdate();
        em.createNativeQuery("DELETE FROM notification_channel WHERE folder_id = :fid")
                .setParameter("fid", folderId).executeUpdate();
    }

}
