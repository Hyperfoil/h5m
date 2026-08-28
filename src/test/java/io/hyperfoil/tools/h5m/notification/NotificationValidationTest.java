package io.hyperfoil.tools.h5m.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import io.hyperfoil.tools.h5m.api.notification.AuthHeaderSecret;
import io.hyperfoil.tools.h5m.api.notification.EmailConfig;
import io.hyperfoil.tools.h5m.api.notification.GitHubIssueConfig;
import io.hyperfoil.tools.h5m.api.notification.NotificationConfiguration;
import io.hyperfoil.tools.h5m.api.notification.NotificationSecret;
import io.hyperfoil.tools.h5m.api.notification.SlackConfig;
import io.hyperfoil.tools.h5m.api.notification.TokenSecret;
import io.hyperfoil.tools.h5m.api.notification.WebhookConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Config/secret validation and discriminator dispatch.
 * <p>
 * Replaces the per-plugin {@code plugin.validate(String json)} tests dropped in the notification
 * consolidation: validation now lives in bean-validation constraints on the typed records (applied
 * by {@code @Valid} at the REST edge and by {@code AddNotification.validate} in the CLI), and the
 * JSON→type step is the {@code method} discriminator in the union deserializers.
 */
@QuarkusTest
public class NotificationValidationTest {

    private static final Jsonb JSONB = JsonbBuilder.create();

    @Inject
    Validator validator;

    private boolean valid(Object config) {
        return validator.validate(config).isEmpty();
    }

    // === Bean validation: webhook ===

    @Test
    public void webhook_accepts_url() {
        assertTrue(valid(WebhookConfig.of("https://hooks.example.com/endpoint")));
    }

    @Test
    public void webhook_rejects_missing_or_blank_url() {
        assertFalse(valid(WebhookConfig.of(null)));
        assertFalse(valid(WebhookConfig.of("")));
        assertFalse(valid(WebhookConfig.of("   ")));
    }

    // === Bean validation: slack ===

    @Test
    public void slack_accepts_channel() {
        assertTrue(valid(SlackConfig.of("#perf-alerts")));
    }

    @Test
    public void slack_rejects_missing_channel() {
        assertFalse(valid(SlackConfig.of(null)));
        assertFalse(valid(SlackConfig.of("")));
    }

    // === Bean validation: github issue ===

    @Test
    public void github_accepts_owner_and_repo() {
        assertTrue(valid(GitHubIssueConfig.of("myorg", "perf", null, null)));
    }

    @Test
    public void github_rejects_missing_owner() {
        assertFalse(valid(GitHubIssueConfig.of(null, "perf", null, null)));
    }

    @Test
    public void github_rejects_missing_repo() {
        assertFalse(valid(GitHubIssueConfig.of("myorg", null, null, null)));
    }

    // === Bean validation: email ===

    @Test
    public void email_accepts_recipients() {
        assertTrue(valid(EmailConfig.of(List.of("team@example.com"), null)));
        assertTrue(valid(EmailConfig.of(List.of("alice@example.com", "bob@example.com"), "Perf Alert")));
    }

    @Test
    public void email_rejects_no_recipients() {
        assertFalse(valid(EmailConfig.of(null, null)));
        assertFalse(valid(EmailConfig.of(List.of(), null)));
    }

    @Test
    public void email_rejects_malformed_address() {
        assertFalse(valid(EmailConfig.of(List.of("not-an-email"), null)));
    }

    // === Bean validation: secrets ===

    @Test
    public void token_secret_rejects_blank_token() {
        assertTrue(valid(TokenSecret.slack("xoxb-test")));
        assertFalse(valid(TokenSecret.slack("")));
        assertFalse(valid(TokenSecret.github("   ")));
    }

    @Test
    public void auth_header_secret_rejects_blank_header() {
        assertTrue(valid(AuthHeaderSecret.webHook("Bearer test")));
        assertFalse(valid(AuthHeaderSecret.webHook("")));
    }

    // === Discriminator dispatch: configuration ===

    @Test
    public void config_deserializes_to_the_variant_named_by_method() {
        assertInstanceOf(WebhookConfig.class, config("{\"method\":\"WEBHOOK\",\"url\":\"https://hooks.example.com\"}"));
        assertInstanceOf(SlackConfig.class, config("{\"method\":\"SLACK\",\"channel\":\"#alerts\"}"));
        assertInstanceOf(GitHubIssueConfig.class, config("{\"method\":\"GITHUB_ISSUE\",\"owner\":\"myorg\",\"repo\":\"perf\"}"));
        assertInstanceOf(EmailConfig.class, config("{\"method\":\"EMAIL\",\"to\":[\"team@example.com\"]}"));
    }

    @Test
    public void config_rejects_missing_method() {
        assertThrows(Exception.class, () -> config("{\"url\":\"https://hooks.example.com\"}"));
    }

    @Test
    public void config_rejects_unknown_method() {
        assertThrows(Exception.class, () -> config("{\"method\":\"CARRIER_PIGEON\",\"url\":\"https://hooks.example.com\"}"));
    }

    @Test
    public void config_round_trips_through_json() {
        NotificationConfiguration original = GitHubIssueConfig.of("myorg", "perf", "title", List.of("regression"));
        assertEquals(original, config(JSONB.toJson(original)));
    }

    // === Discriminator dispatch: secret ===

    @Test
    public void secret_deserializes_to_the_variant_named_by_method() {
        assertInstanceOf(TokenSecret.class, secret("{\"method\":\"SLACK\",\"token\":\"xoxb-test\"}"));
        assertInstanceOf(TokenSecret.class, secret("{\"method\":\"GITHUB_ISSUE\",\"token\":\"ghp_test\"}"));
        assertInstanceOf(AuthHeaderSecret.class, secret("{\"method\":\"WEBHOOK\",\"authHeader\":\"Bearer test\"}"));
    }

    @Test
    public void secret_rejects_email_which_has_none() {
        assertThrows(Exception.class, () -> secret("{\"method\":\"EMAIL\",\"token\":\"x\"}"));
    }

    @Test
    public void secret_rejects_missing_method() {
        assertThrows(Exception.class, () -> secret("{\"token\":\"xoxb-test\"}"));
    }

    // === Method is derived, not trusted from the client ===

    @Test
    public void single_method_variants_pin_their_own_method() {
        // A client-sent method on a single-method variant is overwritten by the record's compact constructor.
        assertEquals(NotificationMethod.WEBHOOK, config("{\"method\":\"WEBHOOK\",\"url\":\"https://x.example.com\"}").method());
        assertEquals(NotificationMethod.WEBHOOK, AuthHeaderSecret.webHook("Bearer test").method());
        assertEquals(NotificationMethod.SLACK, SlackConfig.of("#alerts").method());
    }

    @Test
    public void token_secret_keeps_the_method_it_was_built_with() {
        // TokenSecret is the one variant shared by two methods, so its method is real data.
        assertEquals(NotificationMethod.SLACK, TokenSecret.slack("xoxb-test").method());
        assertEquals(NotificationMethod.GITHUB_ISSUE, TokenSecret.github("ghp_test").method());
    }

    private NotificationConfiguration config(String json) {
        return JSONB.fromJson(json, NotificationConfiguration.class);
    }

    private NotificationSecret secret(String json) {
        return JSONB.fromJson(json, NotificationSecret.class);
    }
}
