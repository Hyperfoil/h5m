package io.hyperfoil.tools.h5m.notification;

import com.sun.net.httpserver.HttpServer;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
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

    // === Token validation ===

    @Test
    public void send_rejects_missing_token() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), SlackConfig.of("#alerts"), null, null));
    }

    @Test
    public void send_rejects_blank_token() {
        assertThrows(IllegalArgumentException.class,
            () -> plugin.send(testEvent(), SlackConfig.of("#alerts"), TokenSecret.slack(""), null));
    }

    // === Send tests (using mock server on port 19876) ===

    @Test
    public void send_posts_block_kit_message() throws Exception {
        plugin.send(testEvent(), SlackConfig.of("#perf-alerts"), TokenSecret.slack("xoxb-test-token"), null);

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
        plugin.send(testEvent(), SlackConfig.of("#alerts"), TokenSecret.slack("xoxb-test"),
            "Regression in *{folderName}* by `{nodeName}`: {changeCount} change(s). cc @perf-team");

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
            plugin.send(testEvent(), SlackConfig.of("#nonexistent"), TokenSecret.slack("xoxb-test"), null)
        );
        assertTrue(ex.getMessage().contains("channel_not_found"));
    }

    // === Config type ===

    @Test
    public void send_rejects_foreign_config() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), WebhookConfig.of("https://hook.example.com"), TokenSecret.slack("xoxb-test"), null));
        assertTrue(ex.getMessage().contains("SlackConfig"), "Message should name the expected type: " + ex.getMessage());
    }

    @Test
    public void send_rejects_null_config() {
        assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), null, TokenSecret.slack("xoxb-test"), null));
    }

    @Test
    public void method_returns_slack() {
        assertEquals(NotificationMethod.SLACK, plugin.method());
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
