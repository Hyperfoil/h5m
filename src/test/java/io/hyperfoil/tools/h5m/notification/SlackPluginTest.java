package io.hyperfoil.tools.h5m.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class SlackPluginTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MOCK_PORT = 19876;

    @Inject
    SlackPlugin plugin;

    final AtomicReference<String> lastReceivedBody = new AtomicReference<>();
    final AtomicReference<String> lastReceivedAuth = new AtomicReference<>();
    private HttpServer mockServer;
    private volatile String mockResponse = "{\"ok\": true}";

    @BeforeEach
    void startMock() throws IOException {
        lastReceivedBody.set(null);
        lastReceivedAuth.set(null);
        mockResponse = "{\"ok\": true}";
        mockServer = HttpServer.create(new InetSocketAddress(MOCK_PORT), 0);
        mockServer.createContext("/api/chat.postMessage", exchange -> {
            lastReceivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastReceivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = mockResponse.getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        mockServer.start();
    }

    @AfterEach
    void stopMock() {
        if (mockServer != null) mockServer.stop(0);
    }

    // === Validation tests ===

    @Test
    public void validate_valid_config() {
        assertDoesNotThrow(() -> plugin.validate("{\"channel\": \"#perf-alerts\"}"));
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
    public void validate_rejects_missing_channel() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"token\": \"xoxb-123\"}"));
    }

    // === Token validation ===

    @Test
    public void send_rejects_missing_token() {
        ChangeNotification notification = createTestNotification(
            "{\"channel\": \"#alerts\"}", null, null
        );
        assertThrows(IllegalArgumentException.class, () -> plugin.send(notification));
    }

    @Test
    public void send_rejects_empty_secrets() {
        ChangeNotification notification = createTestNotification(
            "{\"channel\": \"#alerts\"}", "{}", null
        );
        assertThrows(IllegalArgumentException.class, () -> plugin.send(notification));
    }

    // === Send tests (using mock server on port 19876) ===

    @Test
    public void send_posts_block_kit_message() throws Exception {
        plugin.send(createTestNotification(
            "{\"channel\": \"#perf-alerts\"}",
            "{\"token\": \"xoxb-test-token\"}",
            null
        ));

        assertNotNull(lastReceivedBody.get(), "Server should have received a request");
        assertEquals("Bearer xoxb-test-token", lastReceivedAuth.get());

        JsonNode payload = MAPPER.readTree(lastReceivedBody.get());
        assertEquals("#perf-alerts", payload.get("channel").asText());
        assertTrue(payload.has("blocks"));
        assertTrue(payload.has("text"));
        assertEquals("header", payload.get("blocks").get(0).get("type").asText());
        assertTrue(payload.get("blocks").get(0).get("text").get("text").asText().contains("test-folder"));
    }

    @Test
    public void send_with_custom_template() throws Exception {
        plugin.send(createTestNotification(
            "{\"channel\": \"#alerts\"}",
            "{\"token\": \"xoxb-test\"}",
            "Regression in *{folderName}* by `{nodeName}`: {changeCount} change(s). cc @perf-team"
        ));

        JsonNode payload = MAPPER.readTree(lastReceivedBody.get());
        String sectionText = payload.get("blocks").get(1).get("text").get("text").asText();
        assertEquals(
            "Regression in *test-folder* by `threshold-node`: 1 change(s). cc @perf-team",
            sectionText
        );
    }

    @Test
    public void send_handles_slack_api_error() {
        mockResponse = "{\"ok\": false, \"error\": \"channel_not_found\"}";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            plugin.send(createTestNotification(
                "{\"channel\": \"#nonexistent\"}",
                "{\"token\": \"xoxb-test\"}",
                null
            ))
        );
        assertTrue(ex.getMessage().contains("channel_not_found"));
    }

    @Test
    public void method_returns_slack() {
        assertEquals(NotificationMethod.SLACK, plugin.method());
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
