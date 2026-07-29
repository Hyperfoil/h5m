package io.hyperfoil.tools.h5m.api;

/**
 * Team metadata DTO. Used at the API boundary instead of the JPA entity.
 */
public record Team(long id, String name, int memberCount) {}
