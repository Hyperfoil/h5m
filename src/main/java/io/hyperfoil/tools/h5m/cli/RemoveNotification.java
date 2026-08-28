package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "remove", description = "Remove a notification channel by name or ID", generateHelp = true)
public class RemoveNotification implements Command<H5mCommandInvocation>, FolderAware {

    @Argument(description = "notification channel name or ID", required = true, completer = NotificationNameCompleter.class)
    String nameOrId;

    @Option(name = "from", acceptNameWithoutDashes = true, description = "folder name",
            completer = FolderCompleter.class)
    String folderName;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NotificationServiceInterface notificationService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) {
            folderName = invocation.getFolderName();
        }
        if (folderName == null) {
            invocation.println("folder name is required (use --from or cd)");
            return CommandResult.FAILURE;
        }

        Folder folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        NotificationChannel channel = notificationService.findChannel(folder.id(), nameOrId);
        if (channel == null || !notificationService.deleteChannel(channel.id())) {
            invocation.println("Notification channel not found: " + nameOrId);
            return CommandResult.FAILURE;
        }
        invocation.println("Removed notification channel '" + nameOrId + "'");
        return CommandResult.SUCCESS;
    }

    @Override
    public String getFolderName() { return folderName; }
}
