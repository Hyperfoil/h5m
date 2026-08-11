package io.hyperfoil.tools.h5m.cli;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;


import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.jjq.value.JqValue;

@CommandDefinition(name = "structure", description = "Display the hierarchical structure of a folder's node graph", generateHelp = true)
public class StructureCmd implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "folder name", completer = FolderCompleter.class)
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required");
            return CommandResult.FAILURE;
        }
        try {
            var folder = folderService.find(folderName);
            if (folder == null) {
                invocation.println("Folder '" + folderName + "' not found");
                return CommandResult.FAILURE;
            }
            JqValue structure = folderService.structure(folder.id());
            invocation.println(structure.toString());
        } catch (NoResultException e) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
