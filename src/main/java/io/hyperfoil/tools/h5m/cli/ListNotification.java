package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;

import java.util.List;

@CommandDefinition(name = "list", description = "List notification configurations for a folder", generateHelp = true)
public class ListNotification implements Command<H5mCommandInvocation> {

    @Argument(description = "folder name", completer = FolderCompleter.class)
    String folderName;

    @Inject
    FolderServiceInterface folderService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) {
            folderName = invocation.getFolderName();
        }

        if (folderName == null) {
            List<NotificationConfig> all = NotificationConfig.listAll();
            if (all.isEmpty()) {
                invocation.println("No notification configs found.");
            } else {
                printConfigs(invocation, all);
            }
            return CommandResult.SUCCESS;
        }

        Folder folder = folderService.byName(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        List<NotificationConfig> configs = NotificationConfig.find("folder.id", folder.id()).list();
        if (configs.isEmpty()) {
            invocation.println("No notification configs for " + folderName);
        } else {
            printConfigs(invocation, configs);
        }
        return CommandResult.SUCCESS;
    }

    private void printConfigs(H5mCommandInvocation invocation, List<NotificationConfig> configs) {
        invocation.println(String.format("%-6s %-20s %-14s %-8s %-30s %s", "ID", "Folder", "Method", "Enabled", "Data", "Template"));
        invocation.println("-".repeat(100));
        for (NotificationConfig config : configs) {
            String folderDisplay = config.folder != null ? config.folder.name : "?";
            String templateDisplay = config.template != null ? config.template : "(default)";
            invocation.println(String.format("%-6d %-20s %-14s %-8s %-30s %s",
                config.id, folderDisplay, config.method.label(), config.enabled, config.data, templateDisplay));
        }
    }
}
