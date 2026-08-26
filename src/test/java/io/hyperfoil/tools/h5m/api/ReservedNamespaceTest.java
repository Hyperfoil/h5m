package io.hyperfoil.tools.h5m.api;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReservedNamespaceTest {

    @Test
    public void detects_reserved_prefix() {
        assertTrue(ReservedNamespace.isReserved("h5m."));
        assertTrue(ReservedNamespace.isReserved("h5m.internal"));
    }

    @Test
    public void reserved_check_is_case_insensitive() {
        assertTrue(ReservedNamespace.isReserved("H5M.internal"));
        assertTrue(ReservedNamespace.isReserved("H5m.Something"));
    }

    @Test
    public void allows_regular_names() {
        assertFalse(ReservedNamespace.isReserved("my-folder"));
        assertFalse(ReservedNamespace.isReserved("h5many")); // no dot delimiter
        assertFalse(ReservedNamespace.isReserved("prefix-h5m.")); // reserved must be at the start
        assertFalse(ReservedNamespace.isReserved(null));
    }

    @Test
    public void checkNotReserved_rejects_reserved_with_bad_request() {
        assertThrows(BadRequestException.class, () -> ReservedNamespace.checkNotReserved("h5m.system"));
    }

    @Test
    public void checkNotReserved_allows_regular_names() {
        assertDoesNotThrow(() -> ReservedNamespace.checkNotReserved("regular"));
        assertDoesNotThrow(() -> ReservedNamespace.checkNotReserved(null));
    }

    @Test
    public void pattern_matches_isReserved_semantics() {
        // The @Pattern regex on the API records must agree with the runtime check.
        assertFalse("h5m.x".matches(ReservedNamespace.ALLOWED_NAME_PATTERN));
        assertFalse("H5M.x".matches(ReservedNamespace.ALLOWED_NAME_PATTERN));
        assertTrue("regular".matches(ReservedNamespace.ALLOWED_NAME_PATTERN));
        assertTrue("h5many".matches(ReservedNamespace.ALLOWED_NAME_PATTERN));
    }
}
