package io.hyperfoil.tools.h5m.api.svc;

import java.util.List;

import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;

/**
 * Service interface for managing notification configurations.
 */
public interface NotificationServiceInterface {

    /**
     * Validates configuration data for a given notification method.
     *
     * @throws IllegalArgumentException if the method is unknown or config is invalid
     */
    void validateConfig(NotificationMethod method, String configData);

    /**
     * Returns all notification configs across all folders.
     */
    List<NotificationConfig> listAll();

    /**
     * Returns notification configs for a specific folder.
     */
    List<NotificationConfig> listByFolder(long folderId);

    /**
     * Finds a notification config by name within a folder.
     *
     * @return the config, or null if not found
     */
    NotificationConfig findByName(long folderId, String name);

    /**
     * Finds a notification config by name or ID within a folder.
     * Tries parsing as ID first, falls back to name lookup.
     *
     * @return the config, or null if not found
     */
    NotificationConfig findByNameOrId(long folderId, String nameOrId);

    /**
     * Deletes a notification config by name or ID within a folder.
     *
     * @return true if deleted, false if not found
     */
    boolean deleteByNameOrId(long folderId, String nameOrId);

    /**
     * Deletes all notification configs and logs for a folder.
     */
    void deleteForFolder(long folderId);

    /**
     * Creates a notification config for a folder.
     * Auto-generates a name if not provided.
     *
     * @return the created config with its auto-generated ID and name
     */
    NotificationConfig create(long folderId, NotificationMethod method, String name,
                              String data, String secrets, String template);
}
