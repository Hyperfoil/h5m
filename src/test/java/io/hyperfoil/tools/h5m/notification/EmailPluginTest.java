package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class EmailPluginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    EmailPlugin plugin;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    // === Validation tests ===

    @Test
    public void validate_valid_config() {
        assertDoesNotThrow(() -> plugin.validate("{\"to\": \"team@example.com\"}"));
    }

    @Test
    public void validate_multiple_recipients() {
        assertDoesNotThrow(() -> plugin.validate("{\"to\": \"alice@example.com,bob@example.com\"}"));
    }

    @Test
    public void validate_rejects_null() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate(null));
    }

    @Test
    public void validate_rejects_empty() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate(""));
    }

    @Test
    public void validate_rejects_missing_to() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"subject\": \"test\"}"));
    }

    @Test
    public void validate_rejects_invalid_email() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"to\": \"not-an-email\"}"));
    }

    // === Send tests ===

    @Test
    public void send_delivers_email() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"team@example.com\"}",
            null
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals(1, sent.size());
        assertTrue(sent.getFirst().getSubject().contains("test-folder"));
        assertTrue(sent.getFirst().getSubject().contains("threshold-node"));
        assertTrue(sent.getFirst().getSubject().startsWith("[h5m]"), "Subject should have prefix");
    }

    @Test
    public void send_includes_change_details_in_body() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"team@example.com\"}",
            null
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        // Check plain text body
        String body = sent.getFirst().getText();
        assertTrue(body.contains("test-folder"), "Text body should contain folder name");
        assertTrue(body.contains("threshold-node"), "Text body should contain node name");
        assertTrue(body.contains("95.3"), "Text body should contain the detection value");
        assertTrue(body.contains("90.0"), "Text body should contain the bound");
        // Check HTML body exists
        String html = sent.getFirst().getHtml();
        assertNotNull(html, "HTML body should be present");
        assertTrue(html.contains("test-folder"), "HTML body should contain folder name");
    }

    @Test
    public void send_to_multiple_recipients() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"alice@example.com,bob@example.com\"}",
            null
        );

        plugin.send(notification);

        assertEquals(1, mailbox.getMailsSentTo("alice@example.com").size());
        assertEquals(1, mailbox.getMailsSentTo("bob@example.com").size());
    }

    @Test
    public void send_with_custom_subject() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"team@example.com\", \"subject\": \"ALERT: {folderName} regression\"}",
            null
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals("[h5m] ALERT: test-folder regression", sent.getFirst().getSubject());
    }

    @Test
    public void send_with_custom_template() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"team@example.com\"}",
            "Regression in {folderName} by {nodeName}: {changeCount} change(s)"
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        assertEquals(
            "Regression in test-folder by threshold-node: 1 change(s)",
            sent.getFirst().getText()
        );
    }

    @Test
    public void send_formats_fixed_threshold_data() {
        ChangeNotification notification = createTestNotification(
            "{\"to\": \"team@example.com\"}",
            null
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        String body = sent.getFirst().getText();
        assertTrue(body.contains("Direction: above"), "Body should contain direction for ft type");
    }

    @Test
    public void send_formats_relative_difference_data() {
        ObjectNode data = MAPPER.createObjectNode();
        data.set("value", new DoubleNode(750.0));
        data.set("ratio", new DoubleNode(-25.0));
        data.set("previous", new DoubleNode(1000.0));
        data.set("last", new DoubleNode(750.0));
        ChangeDetail detail = new ChangeDetail(42L, data, null);

        ChangeNotification notification = new ChangeNotification(
            "test-folder", 1L, "regression-node", "rd",
            List.of(detail),
            "{\"to\": \"team@example.com\"}",
            null, null
        );

        plugin.send(notification);

        var sent = mailbox.getMailsSentTo("team@example.com");
        String body = sent.getFirst().getText();
        assertTrue(body.contains("Ratio: -25.0%"), "Body should contain ratio for rd type");
        assertTrue(body.contains("Previous: 1000.0"), "Body should contain previous value");
    }

    @Test
    public void method_returns_email() {
        assertEquals(NotificationMethod.EMAIL, plugin.method());
    }

    // === Helpers ===

    private ChangeNotification createTestNotification(String configData, String template) {
        ObjectNode detectionData = MAPPER.createObjectNode();
        detectionData.set("value", new DoubleNode(95.3));
        detectionData.set("bound", new DoubleNode(90.0));
        detectionData.put("direction", "above");

        ObjectNode fingerprint = MAPPER.createObjectNode();
        fingerprint.put("testName", "perf-test");

        ChangeDetail detail = new ChangeDetail(42L, detectionData, fingerprint);

        return new ChangeNotification(
            "test-folder", 1L, "threshold-node", "ft",
            List.of(detail), configData, null, template
        );
    }
}
