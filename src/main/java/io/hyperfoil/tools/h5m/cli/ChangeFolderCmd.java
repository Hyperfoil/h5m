package io.hyperfoil.tools.h5m.cli;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;

@CommandDefinition(name = "cd", description = "Set the active folder context so subsequent commands don't need --to/--from", generateHelp = true)
public class ChangeFolderCmd implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "folder name (or '..' to exit current folder)", completer = FolderCompleter.class)
    private String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null || folderName.isEmpty()) {
            invocation.clearFolderContext();
            invocation.println("Folder context cleared");
            return CommandResult.SUCCESS;
        }

        if ("..".equals(folderName)) {
            invocation.clearFolderContext();
            invocation.println("Folder context cleared");
            return CommandResult.SUCCESS;
        }

        String targetFolder = folderName;
        if (targetFolder.startsWith("../")) {
            targetFolder = targetFolder.substring(3);
        }

        Folder folder = folderService.byName(targetFolder);
        if (folder == null) {
            invocation.println("Folder not found: " + targetFolder);
            return CommandResult.FAILURE;
        }
        invocation.setFolderName(targetFolder);
        invocation.println("Using folder: " + targetFolder);
        return CommandResult.SUCCESS;
    }
}
