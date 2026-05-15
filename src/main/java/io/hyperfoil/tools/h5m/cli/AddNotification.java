package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.svc.NotificationService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import picocli.CommandLine;

import java.util.concurrent.Callable;

@CommandLine.Command(name = "notification", separator = " ",
    description = "add a notification config to a folder",
    mixinStandardHelpOptions = true)
public class AddNotification implements Callable<Integer> {

    @CommandLine.Parameters(index = "0", arity = "1", description = "notification method: ${COMPLETION-CANDIDATES}")
    NotificationMethod method;

    @CommandLine.Option(names = {"to"}, description = "target folder name", required = true)
    String folderName;

    @CommandLine.Parameters(index = "1", arity = "1", description = "configuration data (URL, email, JSON, etc.)")
    String data;

    @CommandLine.Option(names = {"--secrets"}, description = "secret configuration (tokens, passwords)")
    String secrets;

    @CommandLine.Option(names = {"--template"}, description = "custom message template with placeholders: {folderName}, {nodeName}, {nodeType}, {changeCount}")
    String template;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NotificationService notificationService;

    @Override
    @Transactional
    public Integer call() throws Exception {
        Folder folder = folderService.byName(folderName);
        if (folder == null) {
            System.err.println("Folder not found: " + folderName);
            return 1;
        }

        try {
            notificationService.validateConfig(method, data);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid notification config: " + e.getMessage());
            return 1;
        }

        FolderEntity folderEntity = FolderEntity.findById(folder.id());
        NotificationConfig config = new NotificationConfig(folderEntity, method, data, secrets);
        config.template = template;
        config.persist();
        System.out.println("Added " + method.label() + " notification to " + folderName + " (id=" + config.id + ")");
        return 0;
    }
}
