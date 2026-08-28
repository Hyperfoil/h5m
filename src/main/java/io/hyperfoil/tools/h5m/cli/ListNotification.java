package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import java.util.List;

@CommandDefinition(name = "list", description = "List notification channels for a folder", generateHelp = true)
public class ListNotification implements Command<H5mCommandInvocation>, FolderAware {

    private static final Jsonb JSONB = JsonbBuilder.create();

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
            List<NotificationChannel> all = notificationService.allChannels();
            if (all.isEmpty()) {
                invocation.println("No notification channels found.");
            } else {
                printChannels(invocation, all);
            }
            return CommandResult.SUCCESS;
        }

        Folder folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        List<NotificationChannel> channels = notificationService.channelsByFolder(folder.id());
        if (channels.isEmpty()) {
            invocation.println("No notification channels for " + folderName);
        } else {
            printChannels(invocation, channels);
        }
        return CommandResult.SUCCESS;
    }

    private void printChannels(H5mCommandInvocation invocation, List<NotificationChannel> channels) {
        invocation.println(String.format("%-6s %-18s %-20s %-14s %-8s %-30s %s", "ID", "Name", "Folder", "Method", "Enabled", "Config", "Template"));
        invocation.println("-".repeat(120));
        for (NotificationChannel channel : channels) {
            String nameDisplay = channel.name() != null ? channel.name() : "-";
            String folderDisplay = channel.folderName() != null ? channel.folderName() : "?";
            String templateDisplay = channel.template() != null ? channel.template() : "(default)";
            String configDisplay = channel.config() != null ? JSONB.toJson(channel.config()) : "-";
            invocation.println(String.format("%-6d %-18s %-20s %-14s %-8s %-30s %s",
                channel.id(), nameDisplay, folderDisplay, channel.method().label(), channel.enabled(), configDisplay, templateDisplay));
        }
    }

    @Override
    public String getFolderName() { return folderName; }
}
