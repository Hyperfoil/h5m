package io.hyperfoil.tools.h5m.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

import java.time.Instant;

@Entity(name = "api_key")
public class ApiKey extends PanacheEntity {

    @Column(name = "key_hash")
    public String keyHash; // SHA-256 hex

    @ManyToOne(fetch = FetchType.LAZY)
    public User user;

    public String description;

    public Instant createdAt;

    public Instant expiresAt;

    public ApiKey() {}

    @Override
    public String toString() {
        return "ApiKey<" + id + ">[ user=" + (user != null ? user.username : "null") +
                " description=" + description + " ]";
    }
}
