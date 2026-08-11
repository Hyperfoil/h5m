package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;


import java.nio.file.Path;

@CommandDefinition(name = "export", description = "Export a folder's node graph definition to a JSON file for backup or migration", generateHelp = true)
public class ExportFolder implements Command<H5mCommandInvocation> {

    @Argument(description = "folder name to export", completer = FolderCompleter.class)
    String folderName;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "output JSON file path (default: <folderName>.json)")
    String outputPath;

    @Inject
    FolderServiceInterface folderService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required");
            return CommandResult.FAILURE;
        }
        Path path = outputPath != null
            ? Path.of(outputPath)
            : Path.of(folderName + ".json");

        try {
            var folder = folderService.find(folderName);
            if (folder == null) {
                invocation.println("Folder '" + folderName + "' not found");
                return CommandResult.FAILURE;
            }
            folderService.export(folder.id(), path);
            invocation.println("Exported folder '" + folderName + "' to " + path);
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Export failed: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
