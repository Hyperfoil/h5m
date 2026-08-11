package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Option;

import java.util.List;

@CommandDefinition(name = "list", description = "List notification configurations for a folder", generateHelp = true)
public class ListNotification implements Command<H5mCommandInvocation>, FolderAware {

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
            List<NotificationConfig> all = notificationService.listAll();
            if (all.isEmpty()) {
                invocation.println("No notification configs found.");
            } else {
                printConfigs(invocation, all);
            }
            return CommandResult.SUCCESS;
        }

        Folder folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        List<NotificationConfig> configs = notificationService.listByFolder(folder.id());
        if (configs.isEmpty()) {
            invocation.println("No notification configs for " + folderName);
        } else {
            printConfigs(invocation, configs);
        }
        return CommandResult.SUCCESS;
    }

    private void printConfigs(H5mCommandInvocation invocation, List<NotificationConfig> configs) {
        invocation.println(String.format("%-6s %-18s %-20s %-14s %-8s %-30s %s", "ID", "Name", "Folder", "Method", "Enabled", "Data", "Template"));
        invocation.println("-".repeat(120));
        for (NotificationConfig config : configs) {
            String nameDisplay = config.name != null ? config.name : "-";
            String folderDisplay = config.folder != null ? config.folder.name : "?";
            String templateDisplay = config.template != null ? config.template : "(default)";
            invocation.println(String.format("%-6d %-18s %-20s %-14s %-8s %-30s %s",
                config.id, nameDisplay, folderDisplay, config.method.label(), config.enabled, config.data, templateDisplay));
        }
    }

    @Override
    public String getFolderName() { return folderName; }
}
