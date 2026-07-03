package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;

@CommandDefinition(name = "create", description = "Create a new named view for a folder", generateHelp = true)
public class ViewCreateCmd implements Command<H5mCommandInvocation> {

    @Inject
    ViewServiceInterface viewService;

    @Argument(description = "view name", required = true)
    String name;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name")
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --to)");
            return CommandResult.FAILURE;
        }

        try {
            View created = viewService.createView(folderName, new View(null, name, null, List.of()));
            invocation.println("Created view '" + created.name() + "' (id=" + created.id() + ")");
        } catch (Exception e) {
            invocation.println("Failed to create view: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
