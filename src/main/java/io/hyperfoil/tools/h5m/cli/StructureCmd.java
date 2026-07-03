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

    @Argument(description = "folder name")
    private String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        try {
            JqValue structure = folderService.structure(folderName);
            invocation.println(structure.toString());
        } catch (NoResultException e) {
            invocation.println("could not find folder " + folderName);
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
