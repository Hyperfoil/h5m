package io.hyperfoil.tools.h5m.api.notification;

import io.hyperfoil.tools.h5m.api.NotificationMethod;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.lang.reflect.Type;

/**
 * Plugin-specific configuration for a notification channel.
 * <p>
 * A discriminated union: every variant carries a {@code method} property holding its
 * {@link NotificationMethod}, which is the single source of truth for the channel's
 * method. The {@code method} discriminator lets OpenAPI clients generate a narrowed
 * tagged union.
 */
@JsonbTypeDeserializer(NotificationConfiguration.Deserializer.class)
@Schema(
        description = "Plugin-specific notification configuration; the 'method' property selects the variant",
        discriminatorProperty = "method",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "EMAIL", schema = EmailConfig.class),
                @DiscriminatorMapping(value = "GITHUB_ISSUE", schema = GitHubIssueConfig.class),
                @DiscriminatorMapping(value = "SLACK", schema = SlackConfig.class),
                @DiscriminatorMapping(value = "WEBHOOK", schema = WebhookConfig.class),
        },
        oneOf = { EmailConfig.class, GitHubIssueConfig.class, SlackConfig.class, WebhookConfig.class })
public sealed interface NotificationConfiguration
        permits EmailConfig, GitHubIssueConfig, SlackConfig, WebhookConfig {

    /** The notification method this configuration is for; serialized as the {@code method} discriminator. */
    NotificationMethod method();

    class Deserializer implements JsonbDeserializer<NotificationConfiguration> {
        private static final JsonParserFactory PARSER_FACTORY = Json.createParserFactory(null);

        @Override
        public NotificationConfiguration deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            JsonObject obj = parser.getObject();
            if (!obj.containsKey("method")) {
                throw new IllegalArgumentException("Notification configuration is missing the 'method' discriminator");
            }
            NotificationMethod method = NotificationMethod.valueOf(obj.getString("method"));
            Class<? extends NotificationConfiguration> type = switch (method) {
                case EMAIL -> EmailConfig.class;
                case GITHUB_ISSUE -> GitHubIssueConfig.class;
                case SLACK -> SlackConfig.class;
                case WEBHOOK -> WebhookConfig.class;
            };
            return ctx.deserialize(type, PARSER_FACTORY.createParser(obj));
        }
    }
}
