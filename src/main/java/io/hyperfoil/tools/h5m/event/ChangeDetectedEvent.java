package io.hyperfoil.tools.h5m.event;

import java.util.List;

/**
 * CDI event fired when a detection node (FixedThreshold, RelativeDifference)
 * produces new or updated values indicating a change.
 *
 * @param folderId  the folder containing the detection node
 * @param nodeId    the detection node that produced the values
 * @param nodeName  name of the detection node
 * @param valueIds  IDs of the detection values produced
 * @param dispatch  whether external notifications should be dispatched.
 *                  Set to false for recalculations and bulk imports.
 */
public record ChangeDetectedEvent(long folderId, long nodeId, String nodeName, List<Long> valueIds, boolean dispatch) {}
