package io.hyperfoil.tools.h5m.cli;

import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;


import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;

@CommandDefinition(name = "recalculate", description = "Recalculate all computed values in a folder by reprocessing through the node graph", generateHelp = true)
public class RecalculateCmd implements Command<H5mCommandInvocation> {

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "folder name")
    private String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        try {
            folderService.recalculate(folderName);
            invocation.println("Recalculation complete for folder " + folderName);
        } catch (NoResultException e) {
            invocation.println("could not find folder " + folderName);
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
