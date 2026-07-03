package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.FolderEntity;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.svc.NotificationService;
import jakarta.inject.Inject;

import io.quarkus.narayana.jta.QuarkusTransaction;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

@CommandDefinition(name = "add", description = "Configure a notification (email, Slack, webhook, or GitHub issue) for change detection events in a folder", generateHelp = true)
public class AddNotification implements Command<H5mCommandInvocation> {

    @Argument(description = "notification method (WEBHOOK, EMAIL, SLACK, GITHUB_ISSUE)", required = true)
    String method;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name", required = true, completer = FolderCompleter.class)
    String folderName;

    @Option(name = "data", acceptNameWithoutDashes = true, description = "configuration data (URL, email, JSON, etc.)", required = true)
    String data;

    @Option(name = "secrets", acceptNameWithoutDashes = true, description = "secret configuration (tokens, passwords)")
    String secrets;

    @Option(name = "template", acceptNameWithoutDashes = true, description = "custom message template with placeholders: {folderName}, {nodeName}, {nodeType}, {changeCount}")
    String template;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NotificationService notificationService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        NotificationMethod notificationMethod;
        try {
            notificationMethod = NotificationMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            invocation.println("Invalid notification method: " + method + ". Use WEBHOOK, EMAIL, SLACK, or GITHUB_ISSUE");
            return CommandResult.FAILURE;
        }

        if (folderName == null && invocation.hasFolderContext()) {
            folderName = invocation.getFolderName();
        }

        Folder folder = folderService.byName(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        try {
            notificationService.validateConfig(notificationMethod, data);
        } catch (IllegalArgumentException e) {
            invocation.println("Invalid notification config: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        long configId = QuarkusTransaction.requiringNew().call(() -> {
            FolderEntity folderEntity = FolderEntity.findById(folder.id());
            NotificationConfig config = new NotificationConfig(folderEntity, notificationMethod, data, secrets);
            config.template = template;
            config.persist();
            return config.id;
        });
        invocation.println("Added " + notificationMethod.label() + " notification to " + folderName + " (id=" + configId + ")");
        return CommandResult.SUCCESS;
    }
}
