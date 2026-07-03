package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;


import java.nio.file.Path;

@CommandDefinition(name = "import", description = "Import a folder's node graph definition from a previously exported JSON file", generateHelp = true)
public class ImportFolder implements Command<H5mCommandInvocation> {

    @Argument(description = "path to the folder JSON file to import", required = true)
    String inputPath;

    @Option(name = "overwrite", acceptNameWithoutDashes = true, description = "delete and recreate the folder if it already exists",
            hasValue = false, defaultValue = "false")
    boolean overwrite;

    @Inject
    FolderServiceInterface folderService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        Path path = Path.of(inputPath);

        if (!path.toFile().exists()) {
            invocation.println("File not found: " + inputPath);
            return CommandResult.FAILURE;
        }

        try {
            String folderName = folderService.importFolder(path, overwrite);
            invocation.println("Imported folder '" + folderName + "' from " + path);
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            invocation.println("Import failed: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }
}
