package io.hyperfoil.tools.h5m.api.node;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParserFactory;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.lang.reflect.Type;

@JsonbTypeDeserializer(NodeConfiguration.Deserializer.class)
@Schema(oneOf = {FixedThresholdConfig.class, RelativeDifferenceConfig.class, StdDevAnomalyConfig.class, EDivisiveConfig.class})
public sealed interface NodeConfiguration permits FixedThresholdConfig, RelativeDifferenceConfig, StdDevAnomalyConfig, EDivisiveConfig {

    class Deserializer implements JsonbDeserializer<NodeConfiguration> {
        private static final JsonParserFactory PARSER_FACTORY = Json.createParserFactory(null);

        @Override
        public NodeConfiguration deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            JsonObject obj = parser.getObject();
            Class<? extends NodeConfiguration> type;
            if (obj.containsKey("windowLen")) {
                type = EDivisiveConfig.class;
            } else if (obj.containsKey("windowSize")) {
                type = StdDevAnomalyConfig.class;
            } else if (obj.containsKey("threshold")) {
                type = RelativeDifferenceConfig.class;
            } else {
                type = FixedThresholdConfig.class;
            }
            return ctx.deserialize(type, PARSER_FACTORY.createParser(obj));
        }
    }
}
