package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;

import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Location;
import io.quarkus.qute.Qute;
import io.quarkus.qute.Template;
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

    @Inject
    ReactiveMailer mailer;

    @Location("email_change_notification.html")
    Template htmlTemplate;

    @Location("email_change_notification.txt")
    Template textTemplate;

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
        String to = notification.configData().get("to").asString(null);
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
        String customSubject = notification.configData().has("subject")
            ? notification.configData().get("subject").asString(null) : null;
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
        return renderQuteTemplate(textTemplate, notification);
    }

    private String buildHtmlBody(ChangeNotification notification) {
        if (notification.template() != null && !notification.template().isBlank()) {
            return "<p>" + applyTemplate(notification.template(), notification) + "</p>";
        }
        return renderQuteTemplate(htmlTemplate, notification);
    }

    private String renderQuteTemplate(Template template, ChangeNotification notification) {
        return template
            .data("folderName", notification.folderName())
            .data("nodeName", notification.nodeName())
            .data("nodeType", notification.nodeType())
            .data("changeCount", notification.changes().size())
            .data("changes", notification.changes())
            .render();
    }

    private String applyTemplate(String template, ChangeNotification notification) {
        return Qute.fmt(template)
            .data("folderName", notification.folderName())
            .data("nodeName", notification.nodeName())
            .data("nodeType", notification.nodeType())
            .data("changeCount", notification.changes().size())
            .data("changes", notification.changes())
            .render();
    }

    private String extractField(String json, String field) {
        if (json == null) return null;
        try {
            JqValue parsed = JqValues.parse(json);
            if (parsed instanceof JqObject obj && obj.has(field)) {
                return obj.get(field).asString("");
            }
        } catch (Exception e) {
            // not valid JSON
        }
        return null;
    }
}
