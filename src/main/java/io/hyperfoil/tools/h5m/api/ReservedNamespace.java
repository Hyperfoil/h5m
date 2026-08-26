package io.hyperfoil.tools.h5m.api;

import jakarta.ws.rs.BadRequestException;

/**
 * The {@code h5m.} namespace, reserved for internal, system-created entities.
 * <p>
 * Reserved names are rejected at the REST edge via the {@link #ALLOWED_NAME_PATTERN}
 * {@code @Pattern} annotations, not in the services (which may persist reserved names on the
 * system's behalf). The CLI is a trusted surface and is not guarded. {@link #checkNotReserved}
 * is a programmatic equivalent for any non-REST caller that needs the same check.
 */
public final class ReservedNamespace {

    /** Regex rejecting any name that starts with the reserved prefix (case-insensitive). */
    public static final String ALLOWED_NAME_PATTERN = "^(?![hH]5[mM]\\.).*$";

    /** Prefix reserved for internal use. Names starting with this (case-insensitive) are rejected. */
    public static final String RESERVED_PREFIX = "h5m.";

    /** Name of the system-managed default view created for every folder. Lives in the reserved namespace. */
    public static final String DEFAULT_VIEW_NAME = RESERVED_PREFIX + "default";

    private ReservedNamespace() {
    }

    /** @return {@code true} if the name is reserved for internal use (starts with the reserved prefix). */
    public static boolean isReserved(String name) {
        // Zero-allocation, case-insensitive prefix check — faster than substring/toLowerCase or regex.
        return name != null && name.regionMatches(true, 0, RESERVED_PREFIX, 0, RESERVED_PREFIX.length());
    }

    /**
     * Rejects user-supplied names that intrude on the reserved namespace.
     *
     * @throws BadRequestException (400) if {@code name} starts with the reserved prefix
     */
    public static void checkNotReserved(String name) {
        if (isReserved(name)) {
            throw new BadRequestException("Names starting with '" + RESERVED_PREFIX + "' are reserved for internal use: " + name);
        }
    }
}
