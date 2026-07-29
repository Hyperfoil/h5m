package io.hyperfoil.tools.h5m.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity(name = "api_key")
public class ApiKeyEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "key_hash", unique = true)
    public String keyHash; // SHA-256 hex

    @ManyToOne(fetch = FetchType.EAGER)
    public UserEntity user;

    public String description;

    public Instant createdAt;

    public Instant lastUsedAt;

    public long activeDays; // number of idle days before the key expires

    public boolean revoked;

    public ApiKeyEntity() {}

    /**
     * Checks if this key has expired based on idle time.
     * Expiration is independent of revocation — a key can be revoked but not
     * expired, or expired but not revoked.
     */
    public boolean isExpired(Instant now) {
        Instant reference = lastUsedAt != null ? lastUsedAt : createdAt;
        return reference != null && now.isAfter(reference.plus(activeDays, ChronoUnit.DAYS));
    }

    public void recordAccess() {
        this.lastUsedAt = Instant.now();
    }

    @Override
    public String toString() {
        return "ApiKeyEntity<" + id + ">[ user=" + (user != null ? user.username : "null") +
                " description=" + description + " ]";
    }
}
