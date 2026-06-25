package io.hyperfoil.tools.h5m.event;

import io.hyperfoil.tools.jjq.value.JqObject;

import java.util.List;

/**
 * Enriched notification payload passed to notification plugins.
 *
 * @param folderName    name of the folder where the change was detected
 * @param nodeId        ID of the detection node
 * @param nodeName      name of the detection node
 * @param nodeType      type discriminator of the detection node ("ft" or "rd")
 * @param changes       list of individual change details
 * @param configData    plugin-specific configuration (URL, email, channel, etc.), pre-parsed
 * @param configSecrets plugin-specific secrets (API tokens, passwords, etc.), pre-parsed
 * @param template      user-defined message template with placeholders, or null for default
 */
public record ChangeNotification(
    String folderName,
    long nodeId,
    String nodeName,
    String nodeType,
    List<ChangeDetail> changes,
    JqObject configData,
    JqObject configSecrets,
    String template
) {}
