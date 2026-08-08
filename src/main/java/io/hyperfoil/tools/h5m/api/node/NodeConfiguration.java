package io.hyperfoil.tools.h5m.api.node;

import jakarta.json.JsonObject;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.io.StringReader;
import java.lang.reflect.Type;

@JsonbTypeDeserializer(NodeConfiguration.Deserializer.class)
@Schema(oneOf = {FixedThresholdConfig.class, RelativeDifferenceConfig.class, StdDevAnomalyConfig.class, EDivisiveConfig.class})
public sealed interface NodeConfiguration permits FixedThresholdConfig, RelativeDifferenceConfig, StdDevAnomalyConfig, EDivisiveConfig {

    class Deserializer implements JsonbDeserializer<NodeConfiguration> {
        @Override
        public NodeConfiguration deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            JsonObject json = parser.getObject();
            Class<? extends NodeConfiguration> type;
            if (json.containsKey("windowLen")) {
                type = EDivisiveConfig.class;
            } else if (json.containsKey("windowSize")) {
                type = StdDevAnomalyConfig.class;
            } else if (json.containsKey("threshold")) {
                type = RelativeDifferenceConfig.class;
            } else {
                type = FixedThresholdConfig.class;
            }
            return ctx.deserialize(type, jakarta.json.Json.createParser(new StringReader(json.toString())));
        }
    }
}
