package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;
import org.aesh.util.table.Table;

import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;

@CommandDefinition(name = "list", description = "List uploaded runs (root values) for a folder", generateHelp = true)
public class RunListCmd implements Command<H5mCommandInvocation> {

    @Inject
    NodeGroupServiceInterface nodeGroupService;

    @Inject
    ValueServiceInterface valueService;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name")
    String folderName;

    @Option(name = "limit", acceptNameWithoutDashes = true, shortName = 'l',
            description = "maximum number of results to display", defaultValue = { "50" })
    int limit;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) folderName = invocation.getFolderName();
        if (folderName == null) {
            invocation.println("folder name is required (use --from)");
            return CommandResult.FAILURE;
        }

        NodeGroup nodeGroup = nodeGroupService.byName(folderName);
        if (nodeGroup == null) {
            invocation.println("Node group '" + folderName + "' not found");
            return CommandResult.FAILURE;
        }

        if (nodeGroup.root() == null) {
            invocation.println("No root node found for folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }

        List<Value> rootValues;
        try {
            rootValues = valueService.getNodeValues(nodeGroup.root().id());
        } catch (Exception e) {
            invocation.println("Failed to list runs: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        if (rootValues.isEmpty()) {
            invocation.println("No runs found for folder '" + folderName + "'");
            return CommandResult.SUCCESS;
        }

        int totalCount = rootValues.size();
        if (limit > 0 && rootValues.size() > limit) {
            rootValues = rootValues.subList(rootValues.size() - limit, rootValues.size());
        }

        String output = Table.<Value>builder()
                .maxWidth(120)
                .column("id", v -> v.id())
                .column("preview", v -> {
                    if (v.data() == null || v.data().isNull()) return "(no data)";
                    String json = v.data().toJsonString();
                    return json.length() > 60 ? json.substring(0, 57) + "..." : json;
                })
                .build()
                .render(rootValues);
        invocation.println("Count: " + totalCount + (limit > 0 ? " (showing " + rootValues.size() + ")" : ""));
        invocation.println(output);
        return CommandResult.SUCCESS;
    }
}
