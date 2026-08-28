package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.GitHubIssueConfig;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubIssuePluginTest {

    private final GitHubIssuePlugin plugin = new GitHubIssuePlugin();

    // === Token validation ===

    @Test
    public void send_rejects_missing_token() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), GitHubIssueConfig.of("myorg", "perf", null, null), null, null));
    }

    @Test
    public void send_rejects_blank_token() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), GitHubIssueConfig.of("myorg", "perf", null, null), TokenSecret.github(""), null));
    }

    // === Config type ===

    @Test
    public void send_rejects_foreign_config() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), SlackConfig.of("#alerts"), TokenSecret.github("ghp_test"), null));
        assertTrue(ex.getMessage().contains("GitHubIssueConfig"), "Message should name the expected type: " + ex.getMessage());
    }

    @Test
    public void send_rejects_null_config() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), null, TokenSecret.github("ghp_test"), null));
    }

    // === Method identity ===

    @Test
    public void method_returns_github_issue() {
        assertEquals(NotificationMethod.GITHUB_ISSUE, plugin.method());
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
