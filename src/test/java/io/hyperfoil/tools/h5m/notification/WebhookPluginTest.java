package io.hyperfoil.tools.h5m.notification;

import com.sun.net.httpserver.HttpServer;
import io.hyperfoil.tools.h5m.api.Change;
import io.hyperfoil.tools.h5m.api.NodeType;
import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.AuthHeaderSecret;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.event.ChangeEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class WebhookPluginTest {

    @Inject
    WebhookPlugin plugin;

    // === Send tests ===

    @Test
    public void send_posts_json_to_url() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();

        HttpServer server = startMockServer(200, receivedBody, receivedContentType);
        int port = server.getAddress().getPort();

        try {
            plugin.send(testEvent(), WebhookConfig.of("http://localhost:" + port + "/webhook"), null, null);

            assertNotNull(receivedBody.get(), "Server should have received a request");
            assertEquals("application/json", receivedContentType.get());

            JqValue payload = JqValues.parse(receivedBody.get());
            assertEquals("test-folder", payload.getField("folder").asString(""));
            assertEquals("threshold-node", payload.getField("nodeName").asString(""));
            assertEquals("FIXED_THRESHOLD", payload.getField("nodeType").asString(""));
            assertEquals(1, (int) payload.getField("changeCount").asLong(0));
            assertTrue(payload.has("text"), "Payload should contain a text field");
            assertTrue(payload.has("changes"), "Payload should contain changes array");
            assertEquals(1, payload.getField("changes").length());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_includes_auth_header() throws Exception {
        AtomicReference<String> receivedAuth = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try {
            plugin.send(testEvent(),
                WebhookConfig.of("http://localhost:" + port + "/webhook"),
                AuthHeaderSecret.webHook("Bearer test-token-123"),
                null);

            assertEquals("Bearer test-token-123", receivedAuth.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_applies_custom_template() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();

        HttpServer server = startMockServer(200, receivedBody, new AtomicReference<>());
        int port = server.getAddress().getPort();

        try {
            plugin.send(testEvent(),
                WebhookConfig.of("http://localhost:" + port + "/webhook"),
                null,
                "Regression in *{folderName}* by {nodeName}: {changeCount} change(s). cc @perf-team");

            JqValue payload = JqValues.parse(receivedBody.get());
            assertEquals(
                "Regression in *test-folder* by threshold-node: 1 change(s). cc @perf-team",
                payload.getField("text").asString("")
            );
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_uses_default_message_when_no_template() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();

        HttpServer server = startMockServer(200, receivedBody, new AtomicReference<>());
        int port = server.getAddress().getPort();

        try {
            plugin.send(testEvent(), WebhookConfig.of("http://localhost:" + port + "/webhook"), null, null);

            JqValue payload = JqValues.parse(receivedBody.get());
            String text = payload.getField("text").asString("");
            assertTrue(text.contains("test-folder"), "Default message should contain folder name");
            assertTrue(text.contains("threshold-node"), "Default message should contain node name");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_throws_on_http_error() throws Exception {
        HttpServer server = startMockServer(500, new AtomicReference<>(), new AtomicReference<>());
        int port = server.getAddress().getPort();

        try {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> plugin.send(testEvent(), WebhookConfig.of("http://localhost:" + port + "/webhook"), null, null));
            assertTrue(ex.getMessage().contains("500"), "Exception should mention HTTP status code");
        } finally {
            server.stop(0);
        }
    }

    // === Config type ===

    @Test
    public void send_rejects_foreign_config() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), SlackConfig.of("#alerts"), null, null));
        assertTrue(ex.getMessage().contains("WebhookConfig"), "Message should name the expected type: " + ex.getMessage());
    }

    @Test
    public void send_rejects_null_config() {
        assertThrows(IllegalArgumentException.class, () -> plugin.send(testEvent(), null, null, null));
    }

    @Test
    public void method_returns_webhook() {
        assertEquals(NotificationMethod.WEBHOOK, plugin.method());
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

    private HttpServer startMockServer(int responseCode,
                                        AtomicReference<String> receivedBody,
                                        AtomicReference<String> receivedContentType) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(responseCode, -1);
            exchange.close();
        });
        server.start();
        return server;
    }
}
