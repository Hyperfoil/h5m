package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.Node;
import io.hyperfoil.tools.h5m.api.NodeGroup;
import io.hyperfoil.tools.h5m.api.svc.NodeGroupServiceInterface;
import io.quarkus.arc.Arc;

/**
 * Completer that suggests node names from the current folder context.
 * Used by view update --add/--remove and other commands that accept node names.
 */
public class NodeNameCompleter implements OptionCompleter<CompleterInvocation> {

    @Override
    public void complete(CompleterInvocation completerInvocation) {
        String input = completerInvocation.getGivenCompleteValue();

        String folderName = getFolderName(completerInvocation);
        if (folderName == null) {
            return;
        }

        try {
            NodeGroupServiceInterface nodeGroupService = Arc.container().instance(NodeGroupServiceInterface.class).get();
            NodeGroup nodeGroup = nodeGroupService.byName(folderName);
            if (nodeGroup == null || nodeGroup.sources() == null) {
                return;
            }

            List<String> nodeNames = nodeGroup.sources().stream()
                    .filter(n -> n.name() != null && !n.name().isEmpty())
                    .map(Node::name)
                    .filter(name -> input == null || input.isEmpty() || name.startsWith(input))
                    .sorted()
                    .toList();

            completerInvocation.addAllCompleterValues(nodeNames);
        } catch (Exception e) {
            // Silently ignore completion errors
        }
    }

    private String getFolderName(CompleterInvocation completerInvocation) {
        // Try to get folder name from the command's option
        var command = completerInvocation.getCommand();
        if (command instanceof ViewUpdateCmd viewUpdate) {
            if (viewUpdate.folderName != null) return viewUpdate.folderName;
        }
        if (command instanceof ViewShowCmd viewShow) {
            if (viewShow.folderName != null) return viewShow.folderName;
        }
        if (command instanceof RunShowCmd runShow) {
            if (runShow.folderName != null) return runShow.folderName;
        }

        // Try folder context
        FolderContext folderContext = Arc.container().instance(FolderContext.class).get();
        if (folderContext != null && folderContext.isSet()) {
            return folderContext.getFolderName();
        }

        return null;
    }
}
