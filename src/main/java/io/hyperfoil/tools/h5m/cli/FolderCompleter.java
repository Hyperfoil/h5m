package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.quarkus.arc.Arc;

public class FolderCompleter implements OptionCompleter<CompleterInvocation> {

    @Override
    public void complete(CompleterInvocation completerInvocation) {
        FolderServiceInterface folderService = Arc.container().instance(FolderServiceInterface.class).get();
        String input = completerInvocation.getGivenCompleteValue();

        if (input != null && input.startsWith("../")) {
            String prefix = input.substring(3);
            List<String> folderNames = folderService.getFolderUploadCount().keySet().stream()
                    .filter(name -> prefix.isEmpty() || name.startsWith(prefix))
                    .map(name -> "../" + name)
                    .sorted()
                    .toList();
            completerInvocation.addAllCompleterValues(folderNames);
        } else if ("..".equals(input) || ".".equals(input)) {
            completerInvocation.addCompleterValue("../");
            completerInvocation.setAppendSpace(false);
        } else {
            List<String> folderNames = folderService.getFolderUploadCount().keySet().stream()
                    .filter(name -> input == null || input.isEmpty() || name.startsWith(input))
                    .sorted()
                    .toList();
            completerInvocation.addAllCompleterValues(folderNames);
        }
    }
}
