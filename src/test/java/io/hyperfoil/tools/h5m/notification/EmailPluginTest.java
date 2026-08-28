package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.EmailConfig;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class EmailPluginTest {

    @Inject
    EmailPlugin plugin;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    // === Recipient validation ===

    @Test
    public void send_rejects_empty_recipients() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), EmailConfig.of(List.of(), null), null, null));
    }

    // === Config type ===

    @Test
    public void send_rejects_foreign_config() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), SlackConfig.of("#alerts"), null, null));
        assertTrue(ex.getMessage().contains("EmailConfig"), "Message should name the expected type: " + ex.getMessage());
    }

    @Test
    public void send_rejects_null_config() {
        assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), null, null, null));
    }

    // === Send tests ===

    @Test
    public void send_delivers_email() {
        plugin.send(testEvent(), EmailConfig.of(List.of("team@example.com"), null), null, null);

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().getSubject().contains("test-folder"));
        assertTrue(sent.getFirst().getSubject().contains("threshold-node"));
        assertTrue(sent.getFirst().getSubject().startsWith("[h5m]"), "Subject should have prefix");
    }

    @Test
    public void send_includes_change_details_in_body() {
        plugin.send(testEvent(), EmailConfig.of(List.of("team@example.com"), null), null, null);

        var sent = mailbox.getMailsSentTo("team@example.com");
        // Check plain text body
        String body = sent.getFirst().getText();
        assertTrue(body.contains("test-folder"), "Text body should contain folder name");
        assertTrue(body.contains("threshold-node"), "Text body should contain node name");
        assertTrue(body.contains("95.3"), "Text body should contain the detection value");
        // jjq serializes integer-valued doubles without decimal suffix (90.0 → 90)
        assertTrue(body.contains("90"), "Text body should contain the bound");
        // Check HTML body exists
        String html = sent.getFirst().getHtml();
        assertNotNull(html, "HTML body should be present");
        assertTrue(html.contains("test-folder"), "HTML body should contain folder name");
    }

    @Test
    public void send_to_multiple_recipients() {
        plugin.send(testEvent(),
            EmailConfig.of(List.of("alice@example.com", "bob@example.com"), null), null, null);

        assertEquals(1, mailbox.getMailsSentTo("alice@example.com").size());
        assertEquals(1, mailbox.getMailsSentTo("bob@example.com").size());
    }

    @Test
    public void send_with_custom_subject() {
        plugin.send(testEvent(),
            EmailConfig.of(List.of("team@example.com"), "ALERT: {folderName} regression"), null, null);

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals("[h5m] ALERT: test-folder regression", sent.getFirst().getSubject());
    }

    @Test
    public void send_with_custom_template() {
        plugin.send(testEvent(), EmailConfig.of(List.of("team@example.com"), null),
            null, "Regression in {folderName} by {nodeName}: {changeCount} change(s)");

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals(
            "Regression in test-folder by threshold-node: 1 change(s)",
            sent.getFirst().getText()
        );
    }

    @Test
    public void send_formats_fixed_threshold_data() {
        plugin.send(testEvent(), EmailConfig.of(List.of("team@example.com"), null), null, null);

        var sent = mailbox.getMailsSentTo("team@example.com");
        String body = sent.getFirst().getText();
        assertTrue(body.contains("above"), "Body should contain direction for ft type");
    }

    @Test
    public void send_formats_relative_difference_data() {
        JqValue data = JqObject.builder()
                .put("value", 750.0)
                .put("ratio", -25.0)
                .put("previous", 1000.0)
                .put("last", 750.0)
                .build();
        Change change = new Change(42L, 1L, "regression-node", NodeType.RELATIVE_DIFFERENCE, data, null);

        ChangeEvent event = new ChangeEvent(5L, "test-folder", List.of(change), true, 42L);

        plugin.send(event, EmailConfig.of(List.of("team@example.com"), null), null, null);

        var sent = mailbox.getMailsSentTo("team@example.com");
        String body = sent.getFirst().getText();
        // jjq serializes integer-valued doubles without decimal suffix (-25.0 → -25)
        assertTrue(body.contains("-25"), "Body should contain ratio for rd type");
        assertTrue(body.contains("1000"), "Body should contain previous value");
    }

    @Test
    public void method_returns_email() {
        assertEquals(NotificationMethod.EMAIL, plugin.method());
    }

    // === Helpers ===

    private ChangeEvent testEvent() {
        JqValue detectionData = JqObject.builder()
                .put("value", 95.3)
                .put("bound", 90.0)
                .put("direction", "above")
                .build();

        JqValue fingerprint = JqObject.builder()
                .put("testName", "perf-test")
                .build();

        Change change = new Change(42L, 1L, "threshold-node", NodeType.FIXED_THRESHOLD, detectionData, fingerprint);

        return new ChangeEvent(5L, "test-folder", List.of(change), true, 42L);
    }
}
