package io.hyperfoil.tools.h5m.event;

import java.util.List;

public record ChangeDetectedEvent(long nodeId, String nodeName, List<Long> valueIds) {}
