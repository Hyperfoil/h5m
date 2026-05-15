package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

/**
 * Notification plugin that sends change notifications via email.
 * <p>
 * Configuration data (JSON):
 * <pre>{"to": "team@example.com"}</pre>
 * or multiple recipients:
 * <pre>{"to": "alice@example.com,bob@example.com"}</pre>
 * <p>
 * Optional fields:
 * <pre>{"to": "team@example.com", "subject": "Custom subject: {folderName}"}</pre>
 * <p>
 * If a custom template is provided via {@link ChangeNotification#template()},
 * it is used as the email body. Otherwise a default plain-text body is generated.
 */
@ApplicationScoped
public class EmailPlugin implements NotificationPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    ReactiveMailer mailer;

    @ConfigProperty(name = "h5m.mail.subject.prefix", defaultValue = "[h5m]")
    String subjectPrefix;

    @ConfigProperty(name = "h5m.mail.timeout", defaultValue = "15s")
    Duration sendMailTimeout;

    @Override
    public NotificationMethod method() {
        return NotificationMethod.EMAIL;
    }

    @Override
    public void send(ChangeNotification notification) {
        String to = extractField(notification.configData(), "to");
        String subject = buildSubject(notification);
        String body = buildBody(notification);
        String htmlBody = buildHtmlBody(notification);

        String[] recipients = to.split(",");
        Mail mail = Mail.withHtml(recipients[0].trim(), subject, htmlBody);
        mail.setText(body);
        for (int i = 1; i < recipients.length; i++) {
            mail.addTo(recipients[i].trim());
        }

        mailer.send(mail).await().atMost(sendMailTimeout);
        Log.debugf("Email sent to %s: %s", to, subject);
    }

    @Override
    public void validate(String configData) {
        if (configData == null || configData.isBlank()) {
            throw new IllegalArgumentException("Email config data is required");
        }
        String to = extractField(configData, "to");
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Email config must contain a 'to' field with recipient address(es)");
        }
        // Basic email format check
        for (String recipient : to.split(",")) {
            String trimmed = recipient.trim();
            if (!trimmed.contains("@") || !trimmed.contains(".")) {
                throw new IllegalArgumentException("Invalid email address: " + trimmed);
            }
        }
    }

    private String buildSubject(ChangeNotification notification) {
        String customSubject = extractField(notification.configData(), "subject");
        if (customSubject != null && !customSubject.isBlank()) {
            return subjectPrefix + " " + applyTemplate(customSubject, notification);
        }
        return String.format("%s Change detected in %s by %s",
            subjectPrefix, notification.folderName(), notification.nodeName());
    }

    private String buildBody(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return applyTemplate(notification.template(), notification);
        }
        return buildDefaultBody(notification);
    }

    private String buildDefaultBody(ChangeNotification notification) {
        StringBuilder sb = new StringBuilder();
        sb.append("Change detected in folder '").append(notification.folderName()).append("'\n");
        sb.append("Detection node: ").append(notification.nodeName());
        sb.append(" (").append(notification.nodeType()).append(")\n");
        sb.append("Number of changes: ").append(notification.changes().size()).append("\n");
        sb.append("\n");

        for (int i = 0; i < notification.changes().size(); i++) {
            ChangeDetail change = notification.changes().get(i);
            sb.append("--- Change ").append(i + 1).append(" ---\n");
            if (change.fingerprint() != null) {
                sb.append("Fingerprint: ").append(change.fingerprint()).append("\n");
            }
            if (change.data() != null) {
                formatChangeData(sb, change.data(), notification.nodeType());
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildHtmlBody(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return "<p>" + applyTemplate(notification.template(), notification) + "</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<p>Change detected in folder <strong>").append(notification.folderName()).append("</strong></p>\n");
        sb.append("<p>Detection node: <strong>").append(notification.nodeName());
        sb.append("</strong> (").append(notification.nodeType()).append(")<br>\n");
        sb.append("Number of changes: ").append(notification.changes().size()).append("</p>\n");

        if (!notification.changes().isEmpty()) {
            sb.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\">\n");
            sb.append("<tr><th>#</th><th>Fingerprint</th><th>Details</th></tr>\n");
            for (int i = 0; i < notification.changes().size(); i++) {
                ChangeDetail change = notification.changes().get(i);
                sb.append("<tr><td>").append(i + 1).append("</td>");
                sb.append("<td>").append(change.fingerprint() != null ? change.fingerprint() : "-").append("</td>");
                sb.append("<td>");
                if (change.data() != null) {
                    formatHtmlChangeData(sb, change.data(), notification.nodeType());
                }
                sb.append("</td></tr>\n");
            }
            sb.append("</table>\n");
        }
        return sb.toString();
    }

    private void formatHtmlChangeData(StringBuilder sb, JsonNode data, String nodeType) {
        switch (nodeType) {
            case "ft" -> {
                if (data.has("value")) sb.append("Value: ").append(data.get("value"));
                if (data.has("bound")) sb.append(", Bound: ").append(data.get("bound"));
                if (data.has("direction")) sb.append(", Direction: ").append(data.get("direction").asText());
            }
            case "rd" -> {
                if (data.has("ratio")) sb.append("Ratio: ").append(String.format("%.1f%%", data.get("ratio").asDouble()));
                if (data.has("value")) sb.append(", Value: ").append(data.get("value"));
                if (data.has("previous")) sb.append(", Previous: ").append(data.get("previous"));
            }
            default -> sb.append(data);
        }
    }

    private void formatChangeData(StringBuilder sb, JsonNode data, String nodeType) {
        switch (nodeType) {
            case "ft" -> {
                if (data.has("value")) sb.append("Value: ").append(data.get("value")).append("\n");
                if (data.has("bound")) sb.append("Bound: ").append(data.get("bound")).append("\n");
                if (data.has("direction")) sb.append("Direction: ").append(data.get("direction").asText()).append("\n");
            }
            case "rd" -> {
                if (data.has("value")) sb.append("Value: ").append(data.get("value")).append("\n");
                if (data.has("ratio")) sb.append("Ratio: ").append(String.format("%.1f%%", data.get("ratio").asDouble())).append("\n");
                if (data.has("previous")) sb.append("Previous: ").append(data.get("previous")).append("\n");
                if (data.has("last")) sb.append("Last: ").append(data.get("last")).append("\n");
            }
            default -> sb.append("Data: ").append(data).append("\n");
        }
    }

    private String applyTemplate(String template, ChangeNotification notification) {
        return template
            .replace("{folderName}", notification.folderName())
            .replace("{nodeName}", notification.nodeName())
            .replace("{nodeType}", notification.nodeType())
            .replace("{changeCount}", String.valueOf(notification.changes().size()));
    }

    private String extractField(String json, String field) {
        if (json == null) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.has(field)) {
                return node.get(field).asText();
            }
        } catch (JsonProcessingException e) {
            // not valid JSON
        }
        return null;
    }
}
