package io.hyperfoil.tools.h5m.notification;

import com.sun.net.httpserver.HttpServer;
import io.hyperfoil.tools.h5m.event.ChangeDetail;
import io.hyperfoil.tools.jjq.value.*;
import io.hyperfoil.tools.h5m.event.ChangeNotification;
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

    // === Validation tests ===

    @Test
    public void validate_valid_json_config() {
        assertDoesNotThrow(() -> plugin.validate("{\"url\": \"https://hooks.example.com/endpoint\"}"));
    }

    @Test
    public void validate_plain_url() {
        assertDoesNotThrow(() -> plugin.validate("https://hooks.example.com/endpoint"));
    }

    @Test
    public void validate_accepts_https_url() {
        assertDoesNotThrow(() -> plugin.validate("https://hooks.example.com/endpoint"));
        assertDoesNotThrow(() -> plugin.validate("{\"url\": \"https://hooks.slack.com/services/T00/B00/xxx\"}"));
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
    public void validate_rejects_non_http_url() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("ftp://example.com"));
    }

    @Test
    public void validate_rejects_missing_url_field() {
        assertThrows(IllegalArgumentException.class, () -> plugin.validate("{\"channel\": \"#alerts\"}"));
    }

    // === Send tests ===

    @Test
    public void send_posts_json_to_url() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedContentType = new AtomicReference<>();

        HttpServer server = startMockServer(200, receivedBody, receivedContentType);
        int port = server.getAddress().getPort();

        try {
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                null,
                null
            );

            plugin.send(notification);

            assertNotNull(receivedBody.get(), "Server should have received a request");
            assertEquals("application/json", receivedContentType.get());

            JqValue payload = JqValues.parse(receivedBody.get());
            assertEquals("test-folder", payload.getField("folder").asString(""));
            assertEquals("threshold-node", payload.getField("nodeName").asString(""));
            assertEquals("ft", payload.getField("nodeType").asString(""));
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
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                "{\"authHeader\": \"Bearer test-token-123\"}",
                null
            );

            plugin.send(notification);

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
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                null,
                "Regression in *{folderName}* by {nodeName}: {changeCount} change(s). cc @perf-team"
            );

            plugin.send(notification);

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
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                null,
                null
            );

            plugin.send(notification);

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
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                null,
                null
            );

            RuntimeException ex = assertThrows(RuntimeException.class, () -> plugin.send(notification));
            assertTrue(ex.getMessage().contains("500"), "Exception should mention HTTP status code");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void send_accepts_plain_url_config() throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();

        HttpServer server = startMockServer(200, receivedBody, new AtomicReference<>());
        int port = server.getAddress().getPort();

        try {
            // Config with URL field (configData is always JqObject now)
            ChangeNotification notification = createTestNotification(
                "{\"url\": \"http://localhost:" + port + "/webhook\"}",
                null,
                null
            );

            plugin.send(notification);
            assertNotNull(receivedBody.get(), "Server should have received a request");
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void method_returns_webhook() {
        assertEquals(NotificationMethod.WEBHOOK, plugin.method());
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
