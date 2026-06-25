package io.hyperfoil.tools.h5m.provided;

import io.hyperfoil.tools.jjq.value.*;
import io.quarkus.jsonb.JsonbConfigCustomizer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

import java.lang.reflect.Type;
import java.math.BigDecimal;

/**
 * JSON-B serializer/deserializer for JqValue.
 * Handles JqValue fields nested inside records (e.g., Value.data)
 * when quarkus-rest-jsonb is the REST serialization layer.
 *
 * Both directions operate by direct tree-walking without string
 * intermediaries — no toJsonString()/parse() round-trip.
 */
@ApplicationScoped
public class JqValueJsonbAdapter implements JsonbConfigCustomizer {

    @Override
    public void customize(JsonbConfig config) {
        config.withSerializers(new JqValueSerializer());
        config.withDeserializers(new JqValueDeserializer());
    }

    private static class JqValueSerializer implements JsonbSerializer<JqValue> {
        @Override
        public void serialize(JqValue value, JsonGenerator gen, SerializationContext ctx) {
            if (value == null) {
                gen.writeNull();
                return;
            }
            writeJqValue(value, gen);
        }

        private static void writeJqValue(JqValue value, JsonGenerator gen) {
            switch (value) {
                case JqNull ignored -> gen.writeNull();
                case JqBoolean b -> gen.write(b.booleanValue());
                case JqNumber n -> {
                    if (n.isIntegral()) gen.write(n.longValue());
                    else gen.write(n.doubleValue());
                }
                case JqString s -> gen.write(s.stringValue());
                case JqArray arr -> {
                    gen.writeStartArray();
                    for (JqValue element : arr) {
                        writeJqValue(element, gen);
                    }
                    gen.writeEnd();
                }
                case JqObject obj -> {
                    gen.writeStartObject();
                    obj.forEach((key, val) -> writeJqValueNamed(key, val, gen));
                    gen.writeEnd();
                }
            }
        }

        private static void writeJqValueNamed(String key, JqValue value, JsonGenerator gen) {
            switch (value) {
                case JqNull ignored -> gen.writeNull(key);
                case JqBoolean b -> gen.write(key, b.booleanValue());
                case JqNumber n -> {
                    if (n.isIntegral()) gen.write(key, n.longValue());
                    else gen.write(key, n.doubleValue());
                }
                case JqString s -> gen.write(key, s.stringValue());
                case JqArray arr -> {
                    gen.writeStartArray(key);
                    for (JqValue element : arr) {
                        writeJqValue(element, gen);
                    }
                    gen.writeEnd();
                }
                case JqObject obj -> {
                    gen.writeStartObject(key);
                    obj.forEach((k, v) -> writeJqValueNamed(k, v, gen));
                    gen.writeEnd();
                }
            }
        }
    }

    private static class JqValueDeserializer implements JsonbDeserializer<JqValue> {
        @Override
        public JqValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            return fromJakartaValue(parser.getValue());
        }

        private static JqValue fromJakartaValue(JsonValue value) {
            return switch (value.getValueType()) {
                case NULL -> JqNull.NULL;
                case TRUE -> JqBoolean.TRUE;
                case FALSE -> JqBoolean.FALSE;
                case STRING -> JqString.of(((JsonString) value).getString());
                case NUMBER -> {
                    JsonNumber num = (JsonNumber) value;
                    if (num.isIntegral()) {
                        yield JqNumber.of(num.longValue());
                    }
                    yield JqNumber.of(num.bigDecimalValue());
                }
                case ARRAY -> {
                    JsonArray arr = value.asJsonArray();
                    JqValue[] elements = new JqValue[arr.size()];
                    for (int i = 0; i < arr.size(); i++) {
                        elements[i] = fromJakartaValue(arr.get(i));
                    }
                    yield JqArray.of(elements);
                }
                case OBJECT -> {
                    JsonObject obj = value.asJsonObject();
                    JqObject.Builder builder = JqObject.builder(obj.size());
                    for (var entry : obj.entrySet()) {
                        builder.put(entry.getKey(), fromJakartaValue(entry.getValue()));
                    }
                    yield builder.build();
                }
            };
        }
    }
}
