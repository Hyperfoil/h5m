package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.svc.NotificationService;
import io.quarkus.arc.Arc;

/**
 * Completer that suggests notification config names from the current folder context.
 * Used by notification remove command.
 */
public class NotificationNameCompleter implements OptionCompleter<CompleterInvocation> {

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

            NotificationService notificationService = Arc.container().instance(NotificationService.class).get();
            List<NotificationConfig> configs = notificationService.listByFolder(folder.id());
            if (configs == null) {
                return;
            }

            List<String> names = configs.stream()
                    .filter(c -> c.name != null && !c.name.isEmpty())
                    .map(c -> c.name)
                    .filter(name -> input == null || input.isEmpty() || name.startsWith(input))
                    .sorted()
                    .toList();

            completerInvocation.addAllCompleterValues(names);
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
