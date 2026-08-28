package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.event.ChangeEvent;

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
     * @param event    the change detection event that triggered the notification
     * @param config   the typed, plugin-specific configuration for this channel
     * @param secret  the typed, plugin-specific secret for this channel (may be null)
     * @param template optional custom message template with placeholders, or null for the default
     */
    void send(ChangeEvent event, NotificationConfiguration config, NotificationSecret secret, String template);

    /**
     * Narrows a channel's configuration to the type this plugin expects.
     * <p>
     * The configuration type is keyed to the method by the {@code method} discriminator, so a
     * mismatch means the stored config and the channel's method disagree. Fail with a message
     * naming both types rather than with a bare {@link ClassCastException}.
     *
     * @throws IllegalArgumentException if {@code config} is null or not of the expected type
     */
    default <T extends NotificationConfiguration> T configAs(Class<T> type, NotificationConfiguration config) {
        if (!type.isInstance(config)) {
            throw new IllegalArgumentException(method().label() + " notifications require a " + type.getSimpleName() + ", got " + (config == null ? "null" : config.getClass().getSimpleName()));
        }
        return type.cast(config);
    }
}
