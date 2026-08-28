package io.hyperfoil.tools.h5m.cli;

import java.util.List;

import org.aesh.command.completer.CompleterInvocation;
import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import io.quarkus.arc.Arc;

/**
 * Completer that suggests notification channel names from the current folder context.
 * Used by the notification remove command.
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

            NotificationServiceInterface notificationService = Arc.container().instance(NotificationServiceInterface.class).get();
            List<NotificationChannel> channels = notificationService.channelsByFolder(folder.id());
            if (channels == null) {
                return;
            }

            List<String> names = channels.stream()
                    .filter(c -> c.name() != null && !c.name().isEmpty())
                    .map(NotificationChannel::name)
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
