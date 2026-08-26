package io.hyperfoil.tools.h5m.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.util.table.Table;

import io.hyperfoil.tools.jjq.value.JqNumber;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.h5m.api.ReservedNamespace;
import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.ViewComponent;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;

@CommandDefinition(name = "show", description = "Display data through a configured view as a table", generateHelp = true)
public class ViewShowCmd implements Command<H5mCommandInvocation>, FolderAware {

    @Inject
    ViewServiceInterface viewService;

    @Inject
    FolderServiceInterface folderService;

    @Argument(description = "view name (defaults to 'Default')", completer = ViewNameCompleter.class)
    String viewName;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    String folderName;

    @Option(name = "limit", acceptNameWithoutDashes = true, shortName = 'l',
            description = "maximum number of rows to display", defaultValue = { "50" })
    int limit;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from)");
            return CommandResult.FAILURE;
        }
        if (viewName == null || viewName.isEmpty()) {
            viewName = ReservedNamespace.DEFAULT_VIEW_NAME;
        }

        var folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        List<View> views;
        try {
            views = viewService.getViews(folder.id());
        } catch (Exception e) {
            invocation.println("Failed to list views: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        View view = views.stream()
                .filter(v -> v.name().equalsIgnoreCase(viewName))
                .findFirst()
                .orElse(null);
        if (view == null) {
            invocation.println("View '" + viewName + "' not found. Available views: " +
                    views.stream().map(View::name).reduce((a, b) -> a + ", " + b).orElse("none"));
            return CommandResult.FAILURE;
        }

        if (view.components() == null || view.components().isEmpty()) {
            invocation.println("View '" + viewName + "' has no columns configured. Use 'view update " + viewName + " --add <nodeName>' to add columns.");
            return CommandResult.SUCCESS;
        }

        List<JqValue> rows;
        try {
            rows = viewService.getViewData(folder.id(), view.id());
        } catch (Exception e) {
            invocation.println("Failed to get view data: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        int totalCount = rows.size();
        if (limit > 0 && rows.size() > limit) {
            rows = rows.subList(0, limit);
        }

        List<ViewComponent> components = view.components();

        // Build table columns from view components
        Table.Builder<JqValue> tableBuilder = Table.<JqValue>builder().maxWidth(120);
        for (ViewComponent comp : components) {
            String header = comp.headerName();
            String nodeName = comp.nodeName();
            tableBuilder.column(header, row -> {
                JqValue val = row.getField(nodeName);
                if (val == null || val.isNull()) return "";
                if (val instanceof JqString s) return s.stringValue();
                if (val instanceof JqNumber n) return n.isIntegral() ? (Object) n.longValue() : n.doubleValue();
                return val.toJsonString();
            });
        }

        invocation.println("Count: " + totalCount + (limit > 0 ? " (showing " + rows.size() + ")" : ""));
        invocation.println(tableBuilder.build().render(rows));
        return CommandResult.SUCCESS;
    }

    @Override
    public String getFolderName() { return folderName; }
}
