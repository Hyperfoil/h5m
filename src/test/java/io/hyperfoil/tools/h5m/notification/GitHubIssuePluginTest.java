package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubIssuePluginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GitHubIssuePlugin plugin = new GitHubIssuePlugin();

    // === Validation tests ===

    @Test
    public void validate_valid_config() {
        assertDoesNotThrow(() -> plugin.validate("{\"owner\": \"myorg\", \"repo\": \"perf\"}"));
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
    public void validate_rejects_missing_owner() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"repo\": \"perf\"}"));
    }

    @Test
    public void validate_rejects_missing_repo() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"owner\": \"myorg\"}"));
    }

    // === Token validation ===

    @Test
    public void send_rejects_missing_token() {
        ChangeNotification notification = createTestNotification(
            "{\"owner\": \"myorg\", \"repo\": \"perf\"}",
            null,
            null
        );
        assertThrows(IllegalArgumentException.class, () -> plugin.send(notification));
    }

    @Test
    public void send_rejects_empty_secrets() {
        ChangeNotification notification = createTestNotification(
            "{\"owner\": \"myorg\", \"repo\": \"perf\"}",
            "{}",
            null
        );
        assertThrows(IllegalArgumentException.class, () -> plugin.send(notification));
    }

    // === Method identity ===

    @Test
    public void method_returns_github_issue() {
        assertEquals(NotificationMethod.GITHUB_ISSUE, plugin.method());
    }

    // === Helpers ===

    private ChangeNotification createTestNotification(String configData, String secrets, String template) {
        ObjectNode detectionData = MAPPER.createObjectNode();
        detectionData.set("value", new DoubleNode(95.3));
        detectionData.set("bound", new DoubleNode(90.0));
        detectionData.put("direction", "above");

        ObjectNode fingerprint = MAPPER.createObjectNode();
        fingerprint.put("testName", "perf-test");

        ChangeDetail detail = new ChangeDetail(42L, detectionData, fingerprint);

        return new ChangeNotification(
            "test-folder", 1L, "threshold-node", "ft",
            List.of(detail), configData, secrets, template
        );
    }
}
