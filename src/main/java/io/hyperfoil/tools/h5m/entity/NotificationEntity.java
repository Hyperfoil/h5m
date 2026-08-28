package io.hyperfoil.tools.h5m.entity;

import io.hyperfoil.tools.h5m.api.Notification;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * A sent notification, recorded for auditing and the web UI notification history page.
 */
@Entity(name = "notification")
@Table(indexes = {
    @Index(name = "idx_notification_folder", columnList = "folder_id"),
    @Index(name = "idx_notification_sent_at", columnList = "sentAt")
})
public class NotificationEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    public FolderEntity folder;

    /** The notification method used, denormalized so the log stays readable if the channel is deleted */
    @Enumerated(EnumType.STRING)
    public NotificationMethod method;

    /**
     * The channel this notification was sent through.
     * Nullable — set to null (ON DELETE SET NULL) if the channel is later deleted, so the log row survives.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", foreignKey = @ForeignKey(name = "fk_notification_channel"))
    @OnDelete(action = OnDeleteAction.SET_NULL)
    public NotificationChannelEntity channel;

    /** Delivery status */
    @Enumerated(EnumType.STRING)
    public Notification.Status status;

    /** Error message on failure, null on success */
    @Column(columnDefinition = "TEXT")
    public String errorMessage;

    /** Detection node that triggered this notification */
    public long nodeId;

    /** Name of the detection node */
    public String nodeName;

    /** Number of changes in this notification */
    public int changeCount;

    @CreationTimestamp
    @Column(updatable = false)
    public LocalDateTime sentAt;

    public NotificationEntity() {}
}
