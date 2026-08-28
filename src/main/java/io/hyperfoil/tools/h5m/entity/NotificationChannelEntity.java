package io.hyperfoil.tools.h5m.entity;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.persistence.*;

/**
 * A notification channel on a folder.
 * Each folder can have multiple notification channels (e.g., one for email, one for Slack).
 * <p>
 * The plugin-specific configuration and secret are persisted as JSON strings but
 * exposed through typed {@link NotificationConfiguration} / {@link NotificationSecret}
 * accessors. The raw JSON columns are private; callers work with the typed objects.
 */
@Entity(name = "notification_channel")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"folder_id", "name"}))
public class NotificationChannelEntity extends PanacheEntityBase {

    /** Shared JSON-B for (de)serializing the typed config/secret to and from their JSON columns. */
    private static final Jsonb JSONB = JsonbBuilder.create();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /**
     * User-friendly name for this notification channel.
     * Optional — auto-generated as "{method}-{id}" if not provided.
     * Must be unique within a folder.
     */
    public String name;

    /**
     * The folder this notification channel belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    public FolderEntity folder;

    /**
     * The notification method.
     */
    @Enumerated(EnumType.STRING)
    public NotificationMethod method;

    /**
     * Plugin-specific configuration, persisted as a JSON string in the {@code config} column.
     * Contains non-sensitive settings like URLs, channel names, email addresses.
     * Accessed through the typed {@link #getConfig()} / {@link #setConfig(NotificationConfiguration)}.
     * <p>
     * The field name differs from the typed accessors on purpose: Hibernate maps this String
     * field (field-access), while the {@code @Transient} typed getters/setters below never collide
     * with a persistent property of the same name.
     */
    @Column(name = "config", columnDefinition = "TEXT")
    private String configJson;

    /**
     * Plugin-specific secret data, persisted as a JSON string in the {@code secret} column.
     * Contains sensitive values like API tokens, passwords.
     * Accessed through the typed {@link #getSecret()} / {@link #setSecret(NotificationSecret)}.
     * Never returned from the server: ApiMapper never maps it into NotificationChannel.
     */
    @Column(name = "secret", columnDefinition = "TEXT")
    private String secretJson;

    /**
     * User-defined message template with placeholders.
     * Available placeholders: {folderName}, {nodeName}, {nodeType},
     * {changeCount}, {changes}, {fingerprint}.
     * <p>
     * If null or empty, the plugin uses its default message format.
     */
    @Column(columnDefinition = "TEXT")
    public String template;

    /**
     * Whether this notification channel is enabled.
     */
    public boolean enabled = true;

    public NotificationChannelEntity() {}

    public NotificationChannelEntity(FolderEntity folder, NotificationMethod method, NotificationConfiguration config) {
        this.folder = folder;
        this.method = method;
        setConfig(config);
    }

    public NotificationChannelEntity(FolderEntity folder, NotificationMethod method,
                                     NotificationConfiguration config, NotificationSecret secret) {
        this(folder, method, config);
        setSecret(secret);
    }

    @Transient
    public NotificationConfiguration getConfig() {
        return configJson == null || configJson.isBlank() ? null : JSONB.fromJson(configJson, NotificationConfiguration.class);
    }

    @Transient
    public void setConfig(NotificationConfiguration config) {
        this.configJson = config == null ? null : JSONB.toJson(config);
    }

    @Transient
    public NotificationSecret getSecret() {
        return secretJson == null || secretJson.isBlank() ? null : JSONB.fromJson(secretJson, NotificationSecret.class);
    }

    @Transient
    public void setSecret(NotificationSecret secret) {
        this.secretJson = secret == null ? null : JSONB.toJson(secret);
    }
}
