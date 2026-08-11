package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.entity.NotificationConfig;
import io.hyperfoil.tools.h5m.notification.NotificationMethod;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import jakarta.inject.Inject;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.readline.prompt.Prompt;

@CommandDefinition(name = "add", description = "Configure a notification (email, Slack, webhook, or GitHub issue) for change detection events in a folder", generateHelp = true)
public class AddNotification implements Command<H5mCommandInvocation>, FolderAware {

    private static final Prompt MASKED_PROMPT = new Prompt("", '*');

    @Argument(description = "notification method", required = true)
    NotificationMethod method;

    @Option(name = "name", acceptNameWithoutDashes = true, description = "notification name (optional, auto-generated if not provided, must be unique within folder)")
    String name;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name", completer = FolderCompleter.class)
    String folderName;

    // Raw JSON options (power user / backward compatible)
    @Option(name = "data", acceptNameWithoutDashes = true, description = "configuration data as raw JSON (alternative to method-specific options)")
    String data;

    @Option(name = "secrets", acceptNameWithoutDashes = true, description = "secret configuration as raw JSON (alternative to --token / --auth-header)")
    String secrets;

    @Option(name = "template", acceptNameWithoutDashes = true, description = "custom message template with placeholders: {folderName}, {nodeName}, {nodeType}, {changeCount}")
    String template;

    // WEBHOOK options
    @Option(name = "url", acceptNameWithoutDashes = true, description = "webhook URL")
    String url;

    @Option(name = "auth-header", acceptNameWithoutDashes = true, description = "authorization header value (optional)")
    String authHeader;

    // EMAIL options
    @Option(name = "email", acceptNameWithoutDashes = true, description = "recipient email(s), comma-separated")
    String email;

    @Option(name = "subject", acceptNameWithoutDashes = true, description = "email subject (optional)")
    String subject;

    // SLACK options
    @Option(name = "channel", acceptNameWithoutDashes = true, description = "Slack channel (e.g. #perf-alerts)")
    String channel;

    // SLACK & GITHUB_ISSUE shared option
    @Option(name = "token", acceptNameWithoutDashes = true, description = "bot token (Slack) or personal access token (GitHub)")
    String token;

    // GITHUB_ISSUE options
    @Option(name = "owner", acceptNameWithoutDashes = true, description = "GitHub repository owner")
    String owner;

    @Option(name = "repo", acceptNameWithoutDashes = true, description = "GitHub repository name")
    String repo;

    @Option(name = "title", acceptNameWithoutDashes = true, description = "GitHub issue title template (optional)")
    String title;

    @Option(name = "labels", acceptNameWithoutDashes = true, description = "comma-separated GitHub issue labels (optional)")
    String labels;

    @Inject
    FolderServiceInterface folderService;

    @Inject
    NotificationServiceInterface notificationService;

    @Override
    public CommandResult execute(H5mCommandInvocation invocation) throws InterruptedException {
        if (folderName == null && invocation.hasFolderContext()) {
            folderName = invocation.getFolderName();
        }

        Folder folder = folderService.find(folderName);
        if (folder == null) {
            invocation.println("Folder not found: " + folderName);
            return CommandResult.FAILURE;
        }

        // Validate name uniqueness within folder
        if (name != null && notificationService.findByName(folder.id(), name) != null) {
            invocation.println("Notification '" + name + "' already exists in folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }

        // Resolve data and secrets: --data/--secrets (raw JSON) → method-specific options → interactive dialog
        if (data == null) {
            switch (method) {
                case WEBHOOK -> { if (!resolveWebhook(invocation)) return CommandResult.FAILURE; }
                case EMAIL -> { if (!resolveEmail(invocation)) return CommandResult.FAILURE; }
                case SLACK -> { if (!resolveSlack(invocation)) return CommandResult.FAILURE; }
                case GITHUB_ISSUE -> { if (!resolveGitHubIssue(invocation)) return CommandResult.FAILURE; }
            }
        }

        try {
            notificationService.validateConfig(method, data);
        } catch (IllegalArgumentException e) {
            invocation.println("Invalid notification config: " + e.getMessage());
            return CommandResult.FAILURE;
        }

        NotificationConfig config = notificationService.create(folder.id(), method, name, data, secrets, template);
        invocation.println("Added " + method.label() + " notification '" + config.name + "' to " + folderName + " (id=" + config.id + ")");
        return CommandResult.SUCCESS;
    }

    private boolean resolveWebhook(H5mCommandInvocation invocation) throws InterruptedException {
        boolean interactive = url == null;
        if (interactive) {
            url = prompt(invocation, "URL: ");
            if (isEmpty(url)) {
                invocation.println("URL is required");
                return false;
            }
            if (authHeader == null && secrets == null) {
                invocation.print("Auth header (optional, Enter to skip): ");
                authHeader = readMasked(invocation);
            }
        } else if (isEmpty(url)) {
            invocation.println("URL is required (use --url)");
            return false;
        }

        data = "{\"url\":\"" + escapeJson(url) + "\"}";
        if (secrets == null && !isEmpty(authHeader)) {
            secrets = "{\"authHeader\":\"" + escapeJson(authHeader) + "\"}";
        }
        return true;
    }

    private boolean resolveEmail(H5mCommandInvocation invocation) throws InterruptedException {
        boolean interactive = email == null;
        if (interactive) {
            email = prompt(invocation, "Recipients (comma-separated): ");
            if (isEmpty(email)) {
                invocation.println("At least one recipient is required");
                return false;
            }
            if (subject == null) {
                subject = prompt(invocation, "Subject (optional, Enter for default): ");
            }
        } else if (isEmpty(email)) {
            invocation.println("At least one recipient is required (use --email)");
            return false;
        }

        StringBuilder sb = new StringBuilder("{\"to\":\"").append(escapeJson(email)).append("\"");
        if (!isEmpty(subject)) {
            sb.append(",\"subject\":\"").append(escapeJson(subject)).append("\"");
        }
        sb.append("}");
        data = sb.toString();
        return true;
    }

    private boolean resolveSlack(H5mCommandInvocation invocation) throws InterruptedException {
        boolean interactive = channel == null && token == null;
        if (interactive) {
            channel = prompt(invocation, "Channel: ");
            if (isEmpty(channel)) {
                invocation.println("Channel is required");
                return false;
            }
            if (secrets == null) {
                invocation.print("Bot token: ");
                token = readMasked(invocation);
                if (isEmpty(token)) {
                    invocation.println("Bot token is required");
                    return false;
                }
            }
        } else {
            if (isEmpty(channel)) {
                invocation.println("Channel is required (use --channel)");
                return false;
            }
        }

        data = "{\"channel\":\"" + escapeJson(channel) + "\"}";
        if (secrets == null && !isEmpty(token)) {
            secrets = "{\"token\":\"" + escapeJson(token) + "\"}";
        }
        return true;
    }

    private boolean resolveGitHubIssue(H5mCommandInvocation invocation) throws InterruptedException {
        boolean interactive = owner == null && repo == null && token == null;
        if (interactive) {
            owner = prompt(invocation, "Owner: ");
            if (isEmpty(owner)) {
                invocation.println("Owner is required");
                return false;
            }
            repo = prompt(invocation, "Repository: ");
            if (isEmpty(repo)) {
                invocation.println("Repository is required");
                return false;
            }
            if (secrets == null) {
                invocation.print("GitHub token: ");
                token = readMasked(invocation);
                if (isEmpty(token)) {
                    invocation.println("GitHub token is required");
                    return false;
                }
            }
            if (title == null) {
                title = prompt(invocation, "Title (optional, Enter for default): ");
            }
            if (labels == null) {
                labels = prompt(invocation, "Labels (comma-separated, optional, Enter for default): ");
            }
        } else {
            if (isEmpty(owner)) {
                invocation.println("Owner is required (use --owner)");
                return false;
            }
            if (isEmpty(repo)) {
                invocation.println("Repository is required (use --repo)");
                return false;
            }
        }

        StringBuilder sb = new StringBuilder("{\"owner\":\"").append(escapeJson(owner))
                .append("\",\"repo\":\"").append(escapeJson(repo)).append("\"");
        if (!isEmpty(title)) {
            sb.append(",\"title\":\"").append(escapeJson(title)).append("\"");
        }
        if (!isEmpty(labels)) {
            sb.append(",\"labels\":[");
            String[] parts = labels.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escapeJson(parts[i].trim())).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        data = sb.toString();
        if (secrets == null && !isEmpty(token)) {
            secrets = "{\"token\":\"" + escapeJson(token) + "\"}";
        }
        return true;
    }

    private String prompt(H5mCommandInvocation invocation, String message) throws InterruptedException {
        return invocation.getShell().readLine(new Prompt(message));
    }

    private String readMasked(H5mCommandInvocation invocation) throws InterruptedException {
        return invocation.getShell().readLine(MASKED_PROMPT);
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String getFolderName() { return folderName; }
}
