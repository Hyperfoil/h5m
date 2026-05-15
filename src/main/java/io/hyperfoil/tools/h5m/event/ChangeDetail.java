package io.hyperfoil.tools.h5m.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Detail of a single detected change, enriched from the detection value.
 *
 * @param valueId     ID of the detection value
 * @param data        the detection value data (ratio, bound, direction, fingerprint, etc.)
 * @param fingerprint fingerprint data extracted from the detection value, or null
 */
public record ChangeDetail(long valueId, JsonNode data, JsonNode fingerprint) {}
