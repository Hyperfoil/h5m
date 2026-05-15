package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.event.ChangeNotification;

/**
 * SPI for notification channels. Implementations are discovered via CDI
 * and dispatched by {@link io.hyperfoil.tools.h5m.svc.NotificationService}.
 * <p>
 * To add a new notification channel, create an {@code @ApplicationScoped} bean
 * implementing this interface. It will be automatically picked up.
 */
public interface NotificationPlugin {

    /**
     * The notification method this plugin handles.
     */
    NotificationMethod method();

    /**
     * Send a change notification via this channel.
     *
     * @param notification the enriched notification payload
     */
    void send(ChangeNotification notification);

    /**
     * Validate plugin-specific configuration data before it is saved.
     *
     * @param configData the plugin-specific configuration (JSON string)
     * @throws IllegalArgumentException if the configuration is invalid
     */
    void validate(String configData);
}
