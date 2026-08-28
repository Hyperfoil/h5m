package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.h5m.api.Folder;
import io.hyperfoil.tools.h5m.api.NotificationChannel;
import io.hyperfoil.tools.h5m.api.svc.FolderServiceInterface;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.AuthHeaderSecret;
import io.hyperfoil.tools.h5m.api.notification.EmailConfig;
import io.hyperfoil.tools.h5m.api.notification.GitHubIssueConfig;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.hyperfoil.tools.h5m.api.svc.NotificationServiceInterface;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.validation.Validator;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.readline.prompt.Prompt;

@CommandDefinition(name = "add", description = "Add a notification channel (email, Slack, webhook, or GitHub issue) for change detection events in a folder", generateHelp = true)
public class AddNotification implements Command<H5mCommandInvocation>, FolderAware {

    private static final Prompt MASKED_PROMPT = new Prompt("", '*');

    private static final Jsonb JSONB = JsonbBuilder.create();

    @Argument(description = "notification method", required = true)
    NotificationMethod method;

    @Option(name = "name", acceptNameWithoutDashes = true, description = "notification name (optional, auto-generated if not provided, must be unique within folder)", validator = ReservedNamespaceValidator.class)
    String name;

    @Option(name = "to", acceptNameWithoutDashes = true, description = "target folder name", completer = FolderCompleter.class)
    String folderName;

    // Raw JSON options (power user / backward compatible)
    @Option(name = "data", acceptNameWithoutDashes = true, description = "configuration data as raw JSON (alternative to method-specific options)")
    String data;

    @Option(name = "secret", acceptNameWithoutDashes = true, description = "secret configuration as raw JSON (alternative to --token / --auth-header)")
    String secret;

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

    @Inject
    Validator validator;

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
        if (name != null && notificationService.findChannelByName(folder.id(), name) != null) {
            invocation.println("Notification channel '" + name + "' already exists in folder '" + folderName + "'");
            return CommandResult.FAILURE;
        }

        NotificationConfiguration configObj;
        NotificationSecret secretObj;
        try {
            // --data/--secret (raw JSON, including the 'type' discriminator) take precedence;
            // otherwise build the typed config/secret from method-specific options or interactive prompts.
            configObj = data != null && !data.isBlank()
                    ? JSONB.fromJson(data, NotificationConfiguration.class)
                    : switch (method) {
                        case WEBHOOK -> resolveWebhook(invocation);
                        case EMAIL -> resolveEmail(invocation);
                        case SLACK -> resolveSlack(invocation);
                        case GITHUB_ISSUE -> resolveGitHubIssue(invocation);
                    };
            secretObj = secret != null && !secret.isBlank()
                    ? JSONB.fromJson(secret, NotificationSecret.class)
                    : resolveSecret();
        } catch (Exception e) {
            invocation.println("Invalid JSON: " + e.getMessage());
            return CommandResult.USAGE_ERROR;
        }

        // Slack and GitHub-issue channels are useless without a token: every dispatch would fail at delivery.
        // A null secret is skipped by validate() below, so guard it explicitly here.
        if ((method == NotificationMethod.SLACK || method == NotificationMethod.GITHUB_ISSUE) && secretObj == null) {
            invocation.println("A token is required for " + method.label() + " notifications (use --token or --secret)");
            return CommandResult.USAGE_ERROR;
        }

        String violations = validate(configObj, secretObj);
        if (violations != null) {
            invocation.println("Invalid configuration: " + violations);
            return CommandResult.USAGE_ERROR;
        }

        NotificationChannel channel = notificationService.createChannel(
                folder.id(), name, method, configObj, secretObj, template, null);
        invocation.println("Added " + method.label() + " notification channel '" + channel.name() + "' to " + folderName + " (id=" + channel.id() + ")");
        return CommandResult.SUCCESS;
    }

    private NotificationConfiguration resolveWebhook(H5mCommandInvocation invocation) throws InterruptedException {
        if (url == null) {
            url = prompt(invocation, "URL: ");
            if (authHeader == null && secret == null) {
                invocation.print("Auth header (optional, Enter to skip): ");
                authHeader = readMasked(invocation);
            }
        }
        return WebhookConfig.of(url);
    }

    private NotificationConfiguration resolveEmail(H5mCommandInvocation invocation) throws InterruptedException {
        if (email == null) {
            email = prompt(invocation, "Recipients (comma-separated): ");
            if (subject == null) {
                subject = prompt(invocation, "Subject (optional, Enter for default): ");
            }
        }
        return EmailConfig.of(splitCsv(email), isEmpty(subject) ? null : subject);
    }

    private NotificationConfiguration resolveSlack(H5mCommandInvocation invocation) throws InterruptedException {
        if (channel == null && token == null) {
            channel = prompt(invocation, "Channel: ");
            if (secret == null) {
                invocation.print("Bot token: ");
                token = readMasked(invocation);
            }
        }
        return SlackConfig.of(channel);
    }

    private NotificationConfiguration resolveGitHubIssue(H5mCommandInvocation invocation) throws InterruptedException {
        if (owner == null && repo == null && token == null) {
            owner = prompt(invocation, "Owner: ");
            repo = prompt(invocation, "Repository: ");
            if (secret == null) {
                invocation.print("GitHub token: ");
                token = readMasked(invocation);
            }
            if (title == null) {
                title = prompt(invocation, "Title (optional, Enter for default): ");
            }
            if (labels == null) {
                labels = prompt(invocation, "Labels (comma-separated, optional, Enter for default): ");
            }
        }
        return GitHubIssueConfig.of(owner, repo, isEmpty(title) ? null : title,
                isEmpty(labels) ? null : splitCsv(labels));
    }

    /** Build the typed secret from the collected --token / --auth-header options for the current method. */
    private NotificationSecret resolveSecret() {
        return switch (method) {
            case EMAIL -> null;
            case WEBHOOK -> isEmpty(authHeader) ? null : AuthHeaderSecret.webHook(authHeader);
            case SLACK -> isEmpty(token) ? null : TokenSecret.slack(token);
            case GITHUB_ISSUE -> isEmpty(token) ? null : TokenSecret.github(token);
        };
    }

    /** Bean-validate the config and secret; returns a message of violations, or null if valid. */
    private String validate(NotificationConfiguration config, NotificationSecret secret) {
        String messages = Stream.of(config, secret)
                .filter(Objects::nonNull)
                .flatMap(o -> validator.validate(o).stream())
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .collect(Collectors.joining(", "));
        return messages.isEmpty() ? null : messages;
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

    /** Split a comma-separated option into a list of trimmed, non-empty values. */
    private static List<String> splitCsv(String commaSeparated) {
        if (isEmpty(commaSeparated)) {
            return List.of();
        }
        return Stream.of(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @Override
    public String getFolderName() { return folderName; }
}
