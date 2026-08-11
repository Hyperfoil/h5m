package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.View;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.ViewServiceInterface;
import io.quarkus.arc.Arc;

/**
 * Completer that suggests view names from the current folder context.
 * Used by view update, view show, and view remove commands.
 */
public class ViewNameCompleter implements OptionCompleter<CompleterInvocation> {

    @Override
    public void complete(CompleterInvocation completerInvocation) {
        String input = completerInvocation.getGivenCompleteValue();

        String folderName = getFolderName(completerInvocation);
        if (folderName == null) {
            return;
        }

        try {
            FolderServiceInterface folderService = Arc.container().instance(FolderServiceInterface.class).get();
            Folder folder = folderService.find(folderName);
            if (folder == null) {
                return;
            }

            ViewServiceInterface viewService = Arc.container().instance(ViewServiceInterface.class).get();
            List<View> views = viewService.getViews(folder.id());
            if (views == null) {
                return;
            }

            List<String> viewNames = views.stream()
                    .filter(v -> v.name() != null && !v.name().isEmpty())
                    .map(View::name)
                    .filter(name -> input == null || input.isEmpty() || name.startsWith(input))
                    .sorted()
                    .toList();

            completerInvocation.addAllCompleterValues(viewNames);
        } catch (Exception e) {
            // Silently ignore completion errors
        }
    }

    private String getFolderName(CompleterInvocation completerInvocation) {
        var command = completerInvocation.getCommand();
        if (command instanceof FolderAware fa && fa.getFolderName() != null) {
            return fa.getFolderName();
        }

        // Try folder context
        FolderContext folderContext = Arc.container().instance(FolderContext.class).get();
        if (folderContext != null && folderContext.isSet()) {
            return folderContext.getFolderName();
        }

        return null;
    }
}
