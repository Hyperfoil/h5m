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

@CommandDefinition(name = "remove", description = "Remove a view from a folder", generateHelp = true)
public class ViewRemoveCmd implements Command<H5mCommandInvocation> {

    @Inject
    ViewServiceInterface viewService;

    @Argument(description = "view name", required = true)
    String name;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name")
    String folderName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from)");
            return CommandResult.FAILURE;
        }

        List<View> views;
        try {
            views = viewService.getViews(folderName);
        } catch (Exception e) {
            invocation.println("Failed to list views: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        View view = views.stream()
                .filter(v -> v.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
        if (view == null) {
            invocation.println("View '" + name + "' not found");
            return CommandResult.FAILURE;
        }

        try {
            viewService.deleteView(view.id());
            invocation.println("Removed view '" + name + "'");
        } catch (IllegalArgumentException e) {
            invocation.println(e.getMessage());
            return CommandResult.FAILURE;
        } catch (Exception e) {
            invocation.println("Failed to remove view: " + e.getMessage());
            return CommandResult.FAILURE;
        }
        return CommandResult.SUCCESS;
    }
}
