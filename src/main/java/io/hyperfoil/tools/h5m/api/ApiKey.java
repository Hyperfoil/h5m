package io.hyperfoil.tools.h5m.api;

import java.time.Instant;

/**
 * API key metadata. The {@code rawKey} field is only populated in the response
 * from the create endpoint — this is the only time the raw key is displayed.
 * List responses set {@code rawKey} to {@code null}.
 */
public record ApiKey(
        long id,
        String description,
        Instant createdAt,
        Instant lastUsedAt,
        boolean revoked,
        boolean expired,
        String rawKey
) {}
