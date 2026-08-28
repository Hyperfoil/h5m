package io.hyperfoil.tools.h5m.api.svc;

import java.util.List;

import io.hyperfoil.tools.h5m.api.Notification;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;

/**
 * Service for managing notification channels and the notifications sent through them.
 * <p>
 * Speaks only in api types: callers hand in and receive {@link NotificationChannel}
 * and {@link Notification} records; the implementation owns all entity conversion.
 * <p>
 * "Channel" methods manage the configured delivery targets on a folder;
 * {@link #sentNotifications(long, int)} returns the notifications already sent.
 */
public interface NotificationServiceInterface {

    /**
     * Returns all notification channels across all folders.
     */
    List<NotificationChannel> allChannels();

    /**
     * Returns notification channels for a specific folder.
     */
    List<NotificationChannel> channelsByFolder(long folderId);

    /**
     * Finds a notification channel by name within a folder.
     *
     * @return the channel, or null if not found
     */
    NotificationChannel findChannelByName(long folderId, String name);

    /**
     * Finds a notification channel by name or ID within a folder.
     * Tries parsing as ID first, falls back to name lookup.
     *
     * @return the channel, or null if not found
     */
    NotificationChannel findChannel(long folderId, String nameOrId);

    /**
     * Creates a notification channel on the given folder. Auto-generates a
     * name if {@code name} is null; {@code enabled} left null defaults to true.
     *
     * @return the created channel with its generated ID and name
     */
    NotificationChannel createChannel(long folderId, String name, NotificationMethod method,
                                      NotificationConfiguration config, NotificationSecret secret,
                                      String template, Boolean enabled);

    /**
     * Updates a notification channel. Only non-null fields on {@code channel} are
     * applied; {@code enabled} left null means leave unchanged.
     *
     * @return the updated channel, or null if not found
     */
    NotificationChannel updateChannel(long id, NotificationChannel channel);

    /**
     * Deletes a notification channel by ID.
     *
     * @return true if deleted, false if not found
     */
    boolean deleteChannel(long id);

    /**
     * Deletes all notification channels and sent notifications for a folder.
     */
    void deleteChannelsForFolder(long folderId);

    /**
     * Returns notifications sent for a folder, most recent first.
     *
     * @param limit maximum number of entries to return
     */
    List<Notification> sentNotifications(long folderId, int limit);
}
