package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.EmailConfig;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import io.quarkus.logging.Log;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.reactive.ReactiveMailer;
import io.quarkus.qute.Qute;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.List;

/**
 * Notification plugin that sends change notifications via email.
 * <p>
 * Configuration: {@link EmailConfig} — {@code to} (one or more recipients),
 * optional {@code subject}.
 * <p>
 * If a custom template is provided, it is used as the email body.
 * Otherwise, a default plain-text/HTML body is generated.
 */
@ApplicationScoped
public class EmailPlugin implements NotificationPlugin {

    @Inject
    ReactiveMailer mailer;

    @ConfigProperty(name = "h5m.mail.subject.prefix", defaultValue = "[h5m]")
    String subjectPrefix;

    @ConfigProperty(name = "h5m.mail.timeout", defaultValue = "PT15S") // 15 seconds
    Duration sendMailTimeout;

    @Override
    public NotificationMethod method() {
        return NotificationMethod.EMAIL;
    }

    @Override
    public void send(ChangeEvent event, NotificationConfiguration config, NotificationSecret secret, String template) {
        EmailConfig cfg = configAs(EmailConfig.class, config);
        List<String> recipients = cfg.to();
        if (recipients == null || recipients.isEmpty()) {
            throw new IllegalArgumentException("Email config must contain at least one recipient");
        }
        String subject = buildSubject(cfg, event);
        String body = buildBody(event, template);
        String htmlBody = buildHtmlBody(event, template);

        Mail mail = Mail.withHtml(recipients.getFirst().trim(), subject, htmlBody);
        mail.setText(body);
        for (int i = 1; i < recipients.size(); i++) {
            mail.addTo(recipients.get(i).trim());
        }

        mailer.send(mail).await().atMost(sendMailTimeout);
        Log.debugf("Email sent to %s: %s", recipients, subject);
    }

    private String buildSubject(EmailConfig cfg, ChangeEvent event) {
        Change first = event.changes().getFirst();
        String customSubject = cfg.subject();
        if (customSubject != null && !customSubject.isBlank()) {
            return subjectPrefix + " " + applyTemplate(customSubject, event);
        }
        return String.format("%s Change detected in %s by %s",
            subjectPrefix, event.folderName(), first.nodeName());
    }

    private String buildBody(ChangeEvent event, String template) {
        if (template != null && !template.isBlank()) {
            return applyTemplate(template, event);
        }
        Change first = event.changes().getFirst();
        return NotificationTemplates.email_text(
            event.folderName(), first.nodeName(), first.nodeType(), event.changes().size(), event.changes())
            .render();
    }

    private String buildHtmlBody(ChangeEvent event, String template) {
        if (template != null && !template.isBlank()) {
            return "<p>" + applyTemplate(template, event) + "</p>";
        }
        Change first = event.changes().getFirst();
        return NotificationTemplates.email_html(
            event.folderName(), first.nodeName(), first.nodeType(), event.changes().size(), event.changes())
            .render();
    }

    private String applyTemplate(String template, ChangeEvent event) {
        Change first = event.changes().getFirst();
        return Qute.fmt(template)
            .data("folderName", event.folderName())
            .data("nodeName", first.nodeName())
            .data("nodeType", first.nodeType())
            .data("changeCount", event.changes().size())
            .data("changes", event.changes())
            .render();
    }
}
