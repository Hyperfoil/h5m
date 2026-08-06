package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.util.table.Table;

import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.ViewComponent;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;

@CommandDefinition(name = "list", description = "List all views configured for a folder", generateHelp = true)
public class ViewListCmd implements Command<H5mCommandInvocation> {

    @Inject
    ViewServiceInterface viewService;

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

        if (views.isEmpty()) {
            invocation.println("No views found for folder '" + folderName + "'");
            return CommandResult.SUCCESS;
        }

        String output = Table.<View>builder()
                .maxWidth(120)
                .column("name", v -> v.name())
                .column("columns", v -> v.components() != null ? v.components().size() : 0)
                .column("components", v -> {
                    if (v.components() == null || v.components().isEmpty()) return "";
                    return v.components().stream()
                            .map(ViewComponent::headerName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                })
                .build()
                .render(views);
        invocation.println(output);
        return CommandResult.SUCCESS;
    }
}
