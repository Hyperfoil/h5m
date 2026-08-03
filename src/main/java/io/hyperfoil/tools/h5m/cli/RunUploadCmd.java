package io.hyperfoil.tools.h5m.cli;

import org.aesh.command.CommandDefinition;

/**
 * Upload command registered under the {@code run} command group.
 * Delegates all logic to {@link UploadCmd}.
 */
@CommandDefinition(name = "upload", description = "Upload JSON files to a folder for processing through its computation node graph", generateHelp = true)
public class RunUploadCmd extends UploadCmd {
}
