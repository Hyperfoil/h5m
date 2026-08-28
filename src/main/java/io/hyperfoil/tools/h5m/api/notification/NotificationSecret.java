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
 * Secret configuration for a notification plugin (API tokens, auth headers).
 * <p>
 * Secrets are write-only: they are accepted on create/update but are never
 * serialized back from the server in read responses. The {@code ApiMapper}
 * never maps the entity's secret data into {@code NotificationChannel}, so
 * secrets cannot leak through the API by construction.
 * <p>
 * A discriminated union keyed on the same {@link NotificationMethod} {@code method} as
 * the configuration: {@code SLACK} and {@code GITHUB_ISSUE} carry a {@link TokenSecret},
 * {@code WEBHOOK} an {@link AuthHeaderSecret}. Email has no secret.
 */
@JsonbTypeDeserializer(NotificationSecret.Deserializer.class)
@Schema(
        description = "Plugin-specific secret; the 'method' property selects the variant (never returned from the server)",
        discriminatorProperty = "method",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "SLACK", schema = TokenSecret.class),
                @DiscriminatorMapping(value = "GITHUB_ISSUE", schema = TokenSecret.class),
                @DiscriminatorMapping(value = "WEBHOOK", schema = AuthHeaderSecret.class),
        },
        oneOf = { TokenSecret.class, AuthHeaderSecret.class })
public sealed interface NotificationSecret permits TokenSecret, AuthHeaderSecret {

    /** The notification method this secret is for; serialized as the {@code method} discriminator. */
    NotificationMethod method();

    class Deserializer implements JsonbDeserializer<NotificationSecret> {
        private static final JsonParserFactory PARSER_FACTORY = Json.createParserFactory(null);

        @Override
        public NotificationSecret deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            JsonObject obj = parser.getObject();
            if (!obj.containsKey("method")) {
                throw new IllegalArgumentException("Notification secret is missing the 'method' discriminator");
            }
            NotificationMethod method = NotificationMethod.valueOf(obj.getString("method"));
            Class<? extends NotificationSecret> type = switch (method) {
                case SLACK, GITHUB_ISSUE -> TokenSecret.class;
                case WEBHOOK -> AuthHeaderSecret.class;
                case EMAIL -> throw new IllegalArgumentException("Email notifications have no secret");
            };
            return ctx.deserialize(type, PARSER_FACTORY.createParser(obj));
        }
    }
}
