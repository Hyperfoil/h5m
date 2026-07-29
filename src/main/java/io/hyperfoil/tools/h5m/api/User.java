package io.hyperfoil.tools.h5m.api;

/**
 * User metadata DTO. Used at the API boundary instead of the JPA entity.
 */
public record User(long id, String username, Role role) {}
