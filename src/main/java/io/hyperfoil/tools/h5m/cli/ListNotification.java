package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import jakarta.inject.Inject;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "notification", aliases = {"notifications"}, separator = " ",
    description = "list notification configs for a folder",
    mixinStandardHelpOptions = true)
public class ListNotification implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "0..1", description = "folder name")
    String folderName;

    @Inject
    FolderServiceInterface folderService;

    @Override
    public Integer call() throws Exception {
        if (folderName == null) {
            List<NotificationConfig> all = NotificationConfig.listAll();
            if (all.isEmpty()) {
                System.out.println("No notification configs found.");
            } else {
                printConfigs(all);
            }
            return 0;
        }

        Folder folder = folderService.byName(folderName);
        if (folder == null) {
            System.err.println("Folder not found: " + folderName);
            return 1;
        }

        List<NotificationConfig> configs = NotificationConfig.find("folder.id", folder.id()).list();
        if (configs.isEmpty()) {
            System.out.println("No notification configs for " + folderName);
        } else {
            printConfigs(configs);
        }
        return 0;
    }

    private void printConfigs(List<NotificationConfig> configs) {
        System.out.printf("%-6s %-20s %-14s %-8s %-30s %s%n", "ID", "Folder", "Method", "Enabled", "Data", "Template");
        System.out.println("-".repeat(100));
        for (NotificationConfig config : configs) {
            String folderDisplay = config.folder != null ? config.folder.name : "?";
            String templateDisplay = config.template != null ? config.template : "(default)";
            System.out.printf("%-6d %-20s %-14s %-8s %-30s %s%n",
                config.id, folderDisplay, config.method.label(), config.enabled, config.data, templateDisplay);
        }
    }
}
