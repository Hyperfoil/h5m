package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import java.util.List;

/**
 * Type-safe Qute templates for the notification plugins.
 * <p>
 * Templates live in {@code templates/notification/} — one file per method,
 * matched by name. The parameters below are the only bindings the templates
 * may reference, checked at build time.
 */
@CheckedTemplate(basePath = "notification")
public class NotificationTemplates {

    public static native TemplateInstance email_html(
        String folderName, String nodeName, NodeType nodeType, int changeCount, List<Change> changes);

    public static native TemplateInstance email_text(
        String folderName, String nodeName, NodeType nodeType, int changeCount, List<Change> changes);

    public static native TemplateInstance github_issue(
        String folderName, String nodeName, NodeType nodeType, int changeCount, List<Change> changes);

    public static native TemplateInstance webhook(
        String folderName, String nodeName, NodeType nodeType, int changeCount, List<Change> changes);

    public static native TemplateInstance slack(
        String folderName, String nodeName, NodeType nodeType, int changeCount, List<Change> changes);
}
