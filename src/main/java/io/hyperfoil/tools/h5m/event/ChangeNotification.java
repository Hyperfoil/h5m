package io.hyperfoil.tools.h5m.event;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.jjq.value.JqObject;

import java.util.List;

/**
 * Enriched notification payload passed to notification plugins.
 *
 * @param folderName    name of the folder where the change was detected
 * @param folderId      ID of the folder (enables API calls without name-to-ID lookup)
 * @param valueId       the root value ID that triggered this detection
 * @param nodeId        ID of the detection node
 * @param nodeName      name of the detection node
 * @param nodeType      the detection node type
 * @param changes       list of individual change results
 * @param configData    plugin-specific configuration (URL, email, channel, etc.), pre-parsed
 * @param configSecrets plugin-specific secrets (API tokens, passwords, etc.), pre-parsed
 * @param template      user-defined message template with placeholders, or null for default
 */
public record ChangeNotification(
    String folderName,
    long folderId,
    long valueId,
    long nodeId,
    String nodeName,
    NodeType nodeType,
    List<Change> changes,
    JqObject configData,
    JqObject configSecrets,
    String template
) {}
