package io.hyperfoil.tools.h5m.event;

import io.hyperfoil.tools.h5m.api.Change;

import java.util.List;

/**
 * CDI event fired when a detection node (FixedThreshold, RelativeDifference,
 * StdDevAnomaly, EDivisive) produces new or updated values indicating a change.
 * <p>
 * The {@code changes} list contains pre-enriched {@link Change} records built
 * at event time in WorkService.execute() — the detection values' data is already
 * in memory, so no additional DB lookups are needed by consumers. Each Change
 * record carries nodeId, nodeName, and nodeType.
 *
 * @param folderId     the folder containing the detection node
 * @param folderName   name of the folder (resolved at fire time so consumers need no lookup)
 * @param changes      enriched change detection results (valueId, nodeId, nodeName, nodeType, data, fingerprint)
 * @param dispatch     whether external notifications should be dispatched.
 *                     Set to false for recalculations and bulk imports.
 * @param rootValueId  the root value ID (upload ID) that triggered this detection.
 *                     Used to attribute change results to specific uploads.
 */
public record ChangeEvent(long folderId, String folderName, List<Change> changes, boolean dispatch, long rootValueId) {}
