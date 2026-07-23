package io.hyperfoil.tools.h5m.api;

/**
 * Request body for creating a new API key.
 *
 * @param description human-readable label for the key (e.g., "Jenkins CI", "local testing")
 */
public record ApiKeyRequest(String description) {}
