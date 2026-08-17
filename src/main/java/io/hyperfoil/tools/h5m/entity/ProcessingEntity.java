package io.hyperfoil.tools.h5m.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NativeGenerator;

import java.time.LocalDateTime;

/**
 * Tracks whether a processing operation (ingestion, recalculation) has completed.
 * Used for crash recovery: on startup, incomplete operations are re-triggered.
 *
 * Ingestion records have {@code valueId} set (the root value being processed).
 * Recalculation records have {@code nodeId} set (the target node being recalculated).
 */
@Entity(name = "processing")
public class ProcessingEntity extends PanacheEntityBase {

    @Id
    @NativeGenerator
    public Long id;

    @Column(name = "folder_id", nullable = false, updatable = false)
    public long folderId;

    @Column(name = "node_id", updatable = false)
    public Long nodeId;

    @Column(name = "value_id", updatable = false)
    public Long valueId;

    @Column(nullable = false)
    public boolean completed = false;

    @CreationTimestamp
    @Column(updatable = false)
    public LocalDateTime createdAt;

    public ProcessingEntity() {}

    public ProcessingEntity(long folderId, Long nodeId, Long valueId) {
        this.folderId = folderId;
        this.nodeId = nodeId;
        this.valueId = valueId;
    }

    public boolean isIngestion() {
        return valueId != null;
    }

    public boolean isRecalculation() {
        return nodeId != null;
    }
}
