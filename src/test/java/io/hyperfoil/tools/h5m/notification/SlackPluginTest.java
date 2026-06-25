package io.hyperfoil.tools.h5m.notification;

import com.sun.net.httpserver.HttpServer;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.jjq.value.*;
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

        JqValue payload = JqValues.parse(lastReceivedBody.get());
        assertEquals("#perf-alerts", payload.getField("channel").asString(""));
        assertTrue(payload.has("blocks"));
        assertTrue(payload.has("text"));
        assertEquals("header", payload.getField("blocks").getElement(0).getField("type").asString(""));
        assertTrue(payload.getField("blocks").getElement(0).getField("text").getField("text").asString("").contains("test-folder"));
    }

    @Test
    public void send_with_custom_template() throws Exception {
        plugin.send(createTestNotification(
            "{\"channel\": \"#alerts\"}",
            "{\"token\": \"xoxb-test\"}",
            "Regression in *{folderName}* by `{nodeName}`: {changeCount} change(s). cc @perf-team"
        ));

        JqValue payload = JqValues.parse(lastReceivedBody.get());
        String sectionText = payload.getField("blocks").getElement(1).getField("text").getField("text").asString("");
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
        JqValue detectionData = JqObject.builder()
                .put("value", 95.3)
                .put("bound", 90.0)
                .put("direction", "above")
                .build();

        JqValue fingerprint = JqObject.builder()
                .put("testName", "perf-test")
                .build();

        ChangeDetail detail = new ChangeDetail(42L, detectionData, fingerprint);

        return new ChangeNotification(
            "test-folder", 1L, "threshold-node", "ft",
            List.of(detail), parseObj(configData), parseObj(secrets), template
        );
    }

    private static io.hyperfoil.tools.jjq.value.JqObject parseObj(String json) {
        if (json == null || json.isBlank()) return io.hyperfoil.tools.jjq.value.JqObject.EMPTY;
        io.hyperfoil.tools.jjq.value.JqValue v = JqValues.parse(json);
        return v instanceof io.hyperfoil.tools.jjq.value.JqObject obj ? obj : io.hyperfoil.tools.jjq.value.JqObject.EMPTY;
    }
}
