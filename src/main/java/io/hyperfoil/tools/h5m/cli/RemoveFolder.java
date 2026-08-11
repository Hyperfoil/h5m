package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

@CommandDefinition(name="remove", description = "Delete a folder and all its associated nodes and values", generateHelp = true)
public class RemoveFolder implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "folder name", required = true, completer = FolderCompleter.class)
    String name;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (invocation.hasFolderContext() && name.equals(invocation.getFolderName())) {
            invocation.println("Cannot delete the current folder. Use 'cd ..' first.");
            return CommandResult.FAILURE;
        }
        var folder = folderService.find(name);
        if (folder == null) {
            invocation.println("Folder '" + name + "' not found");
            return CommandResult.FAILURE;
        }
        folderService.delete(folder.id());
        return CommandResult.SUCCESS;
    }
}
