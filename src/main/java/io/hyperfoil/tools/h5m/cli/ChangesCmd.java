package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Value;
import io.hyperfoil.tools.h5m.api.svc.ValueServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

import java.util.List;

@CommandDefinition(name = "changes", description = "List change detection results for an upload", generateHelp = true)
public class ChangesCmd implements Command<H5mCommandInvocation> {

    @Inject
    ValueServiceInterface valueService;

    @Argument(description = "processing ID (root value ID) to query changes for", required = true)
    String id;

    @Option(name = "node", acceptNameWithoutDashes = true, description = "filter changes to a specific detection node name")
    String nodeName;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        long rootValueId;
        try {
            rootValueId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            invocation.println("invalid processing ID: " + id);
            return CommandResult.FAILURE;
        }

        List<Value> detectionValues = valueService.getDetectionDescendants(rootValueId);

        // Filter by node name if specified
        if (nodeName != null && !nodeName.isEmpty()) {
            detectionValues = detectionValues.stream()
                    .filter(v -> v.node() != null && nodeName.equals(v.node().name()))
                    .toList();
        }

        invocation.println(ChangeFormatter.formatSummary(detectionValues));
        return CommandResult.SUCCESS;
    }
}
