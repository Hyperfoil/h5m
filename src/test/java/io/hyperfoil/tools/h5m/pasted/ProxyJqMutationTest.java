package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.*;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProxyJqObject and ProxyJqArray mutation support.
 * Legacy Horreum JS functions mutate input objects (e.g., adding fields
 * to array elements). These tests verify that mutations are visible in
 * the JS return value after conversion back to JqValue.
 */
public class ProxyJqMutationTest {

    private static Engine engine;

    @BeforeAll
    static void setup() {
        engine = Engine.newBuilder("js")
                .option("engine.WarnInterpreterOnly", "false")
                .build();
    }

    @AfterAll
    static void teardown() {
        if (engine != null) engine.close();
    }

    /**
     * Evaluate JS with a single binding and assert on the result inside the context scope.
     * The context must remain open while accessing the Value.
     */
    private void evalAndAssert(String js, String bindingName, JqValue input, Consumer<Value> assertions) {
        try (Context context = Context.newBuilder("js")
                .engine(engine)
                .allowAllAccess(true)
                .build()) {
            context.getBindings("js").putMember(bindingName, ProxyJq.wrap(input));
            Value result = context.eval("js", js);
            assertions.accept(result);
        }
    }

    /**
     * Evaluate JS, convert result to JqValue, and assert.
     */
    private void evalAndAssertJq(String js, String bindingName, JqValue input, Consumer<JqValue> assertions) {
        try (Context context = Context.newBuilder("js")
                .engine(engine)
                .allowAllAccess(true)
                .build()) {
            context.getBindings("js").putMember(bindingName, ProxyJq.wrap(input));
            Value result = context.eval("js", js);
            JqValue converted = Util.convertToJqValue(result);
            assertions.accept(converted);
        }
    }

    // --- ProxyJqObject.putMember ---

    @Test
    public void putMember_adds_new_field_to_object() {
        JqObject input = JqObject.of("name", "test");
        evalAndAssert("value.added = 42; value;", "value", input, result -> {
            assertTrue(result.hasMember("name"), "original field should be preserved");
            assertTrue(result.hasMember("added"), "new field should be visible");
            assertEquals(42, result.getMember("added").asInt());
        });
    }

    @Test
    public void putMember_overwrites_existing_field() {
        JqObject input = JqObject.builder()
                .put("x", 1L)
                .put("y", 2L)
                .build();
        evalAndAssert("value.x = 99; value;", "value", input, result -> {
            assertEquals(99, result.getMember("x").asInt());
            assertEquals(2, result.getMember("y").asInt());
        });
    }

    @Test
    public void putMember_result_converts_back_to_jqobject() {
        JqObject input = JqObject.of("key", "hello");
        evalAndAssertJq("value.extra = 'world'; value;", "value", input, converted -> {
            assertInstanceOf(JqObject.class, converted);
            JqObject obj = (JqObject) converted;
            assertEquals("hello", obj.get("key").asString(""));
            assertEquals("world", obj.get("extra").asString(""));
        });
    }

    // --- ProxyJqArray.set ---

    @Test
    public void set_modifies_array_element() {
        JqArray input = JqArray.of(JqNumber.of(10), JqNumber.of(20), JqNumber.of(30));
        evalAndAssert("arr[1] = 99; arr;", "arr", input, result -> {
            assertEquals(3, result.getArraySize());
            assertEquals(10, result.getArrayElement(0).asInt());
            assertEquals(99, result.getArrayElement(1).asInt());
            assertEquals(30, result.getArrayElement(2).asInt());
        });
    }

    @Test
    public void set_result_converts_back_to_jqarray() {
        JqArray input = JqArray.of(JqString.of("a"), JqString.of("b"));
        evalAndAssertJq("arr[0] = 'replaced'; arr;", "arr", input, converted -> {
            assertInstanceOf(JqArray.class, converted);
            JqArray arr = (JqArray) converted;
            assertEquals("replaced", arr.get(0).asString(""));
            assertEquals("b", arr.get(1).asString(""));
        });
    }

    // --- Nested mutation: object inside array ---

    @Test
    public void nested_mutation_object_in_array() {
        // This is the PCP Time Series pattern:
        // value["pcp_time_series"][i].elapsed_time = computed_value
        JqArray timeSeries = JqArray.of(
                JqObject.of("Time", "2024-01-01T00:00:00"),
                JqObject.of("Time", "2024-01-01T00:01:00")
        );
        JqObject input = JqObject.builder()
                .put("uuid", "test-uuid")
                .put("pcp_time_series", timeSeries)
                .build();

        String js = """
                (function(value) {
                    for (var i = 0; i < value["pcp_time_series"].length; i++) {
                        value["pcp_time_series"][i].elapsed_time = i * 60000;
                        value["pcp_time_series"][i].uuid = value["uuid"];
                    }
                    return value["pcp_time_series"];
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasArrayElements(), "should return an array");
            assertEquals(2, result.getArraySize());

            Value first = result.getArrayElement(0);
            assertTrue(first.hasMember("elapsed_time"),
                    "elapsed_time should be set on first element");
            assertEquals(0, first.getMember("elapsed_time").asInt());
            assertTrue(first.hasMember("uuid"),
                    "uuid should be set on first element");
            assertEquals("test-uuid", first.getMember("uuid").asString());

            Value second = result.getArrayElement(1);
            assertEquals(60000, second.getMember("elapsed_time").asInt());
            assertEquals("test-uuid", second.getMember("uuid").asString());
        });
    }

    @Test
    public void nested_mutation_converts_back_to_jqvalue() {
        // Same as above but verify the full round-trip back to JqValue
        JqArray timeSeries = JqArray.of(
                JqObject.of("Time", "2024-01-01T00:00:00"),
                JqObject.of("Time", "2024-01-01T00:01:00")
        );
        JqObject input = JqObject.builder()
                .put("uuid", "test-uuid")
                .put("pcp_time_series", timeSeries)
                .build();

        String js = """
                (function(value) {
                    for (var i = 0; i < value["pcp_time_series"].length; i++) {
                        value["pcp_time_series"][i].elapsed_time = i * 60000;
                        value["pcp_time_series"][i].uuid = value["uuid"];
                    }
                    return value["pcp_time_series"];
                })(value)
                """;

        evalAndAssertJq(js, "value", input, converted -> {
            assertInstanceOf(JqArray.class, converted);
            JqArray arr = (JqArray) converted;
            assertEquals(2, arr.length());

            JqObject first = (JqObject) arr.get(0);
            assertEquals(0L, first.get("elapsed_time").asLong(0));
            assertEquals("test-uuid", first.get("uuid").asString(""));
            assertEquals("2024-01-01T00:00:00", first.get("Time").asString(""));

            JqObject second = (JqObject) arr.get(1);
            assertEquals(60000L, second.get("elapsed_time").asLong(0));
        });
    }

    // --- Nested mutation: Autobench Results pattern ---

    @Test
    public void nested_mutation_autobench_results_pattern() {
        // Autobench Results:
        // value["results"][section].test_name = section;
        // value["results"][section].uuid = value["metadata"]["uuid"];
        JqValue inputValue = JqValues.parse("""
                {
                    "metadata": {"uuid": "abc-123"},
                    "results": {
                        "workload_a": {"score": 100},
                        "workload_b": {"score": 200}
                    }
                }
                """);
        assertInstanceOf(JqObject.class, inputValue);
        JqObject input = (JqObject) inputValue;

        String js = """
                (function(value) {
                    const workloads = [];
                    for (const section of Object.keys(value["results"])) {
                        value["results"][section].test_name = section;
                        value["results"][section].uuid = value["metadata"]["uuid"];
                        workloads.push(value["results"][section]);
                    }
                    return workloads;
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasArrayElements(), "should return an array");
            assertEquals(2, result.getArraySize());

            Value first = result.getArrayElement(0);
            assertTrue(first.hasMember("test_name"), "test_name should be set");
            assertTrue(first.hasMember("uuid"), "uuid should be set");
            assertTrue(first.hasMember("score"), "original score should be preserved");
        });
    }

    // --- Object.keys() on ProxyJqObject ---

    @Test
    public void object_keys_includes_added_fields() {
        JqObject input = JqObject.of("a", 1L);

        String js = """
                (function(value) {
                    value.b = 2;
                    return Object.keys(value);
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasArrayElements());
            boolean hasA = false, hasB = false;
            for (int i = 0; i < result.getArraySize(); i++) {
                String key = result.getArrayElement(i).asString();
                if ("a".equals(key)) hasA = true;
                if ("b".equals(key)) hasB = true;
            }
            assertTrue(hasA, "should include original key 'a'");
            assertTrue(hasB, "should include added key 'b'");
        });
    }

    // --- ProxyJqObject.removeMember ---

    @Test
    public void removeMember_deletes_field() {
        JqObject input = JqObject.builder()
                .put("keep", "yes")
                .put("drop", "no")
                .build();

        String js = """
                (function(value) {
                    delete value.drop;
                    return value;
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasMember("keep"), "keep should remain");
            assertFalse(result.hasMember("drop"), "drop should be deleted");
        });
    }

    @Test
    public void removeMember_converts_back_without_removed_key() {
        JqObject input = JqObject.builder()
                .put("a", 1L)
                .put("b", 2L)
                .put("c", 3L)
                .build();

        evalAndAssertJq("delete value.b; value;", "value", input, converted -> {
            assertInstanceOf(JqObject.class, converted);
            JqObject obj = (JqObject) converted;
            assertTrue(obj.has("a"));
            assertFalse(obj.has("b"), "b should not be present after delete");
            assertTrue(obj.has("c"));
        });
    }

    @Test
    public void removeMember_then_re_add() {
        JqObject input = JqObject.of("key", "original");

        String js = """
                (function(value) {
                    delete value.key;
                    value.key = "replaced";
                    return value;
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasMember("key"));
            assertEquals("replaced", result.getMember("key").asString());
        });
    }

    @Test
    public void object_keys_excludes_removed_fields() {
        JqObject input = JqObject.builder()
                .put("a", 1L)
                .put("b", 2L)
                .put("c", 3L)
                .build();

        String js = """
                (function(value) {
                    delete value.b;
                    return Object.keys(value);
                })(value)
                """;

        evalAndAssert(js, "value", input, result -> {
            assertTrue(result.hasArrayElements());
            assertEquals(2, result.getArraySize());
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < result.getArraySize(); i++) {
                keys.add(result.getArrayElement(i).asString());
            }
            assertTrue(keys.contains("a"));
            assertFalse(keys.contains("b"));
            assertTrue(keys.contains("c"));
        });
    }

    // --- ProxyJqArray.remove ---

    @Test
    public void array_splice_removes_element() {
        JqArray input = JqArray.of(JqNumber.of(10), JqNumber.of(20), JqNumber.of(30));

        String js = """
                (function(arr) {
                    arr.splice(1, 1);
                    return arr;
                })(arr)
                """;

        evalAndAssert(js, "arr", input, result -> {
            assertEquals(2, result.getArraySize());
            assertEquals(10, result.getArrayElement(0).asInt());
            assertEquals(30, result.getArrayElement(1).asInt());
        });
    }

    @Test
    public void array_remove_converts_back() {
        JqArray input = JqArray.of(JqString.of("a"), JqString.of("b"), JqString.of("c"));

        evalAndAssertJq("arr.splice(0, 1); arr;", "arr", input, converted -> {
            assertInstanceOf(JqArray.class, converted);
            JqArray arr = (JqArray) converted;
            assertEquals(2, arr.length());
            assertEquals("b", arr.get(0).asString(""));
            assertEquals("c", arr.get(1).asString(""));
        });
    }

    // --- ProxyJqArray.set beyond bounds (array growth) ---

    @Test
    public void array_set_beyond_bounds_grows_array() {
        JqArray input = JqArray.of(JqNumber.of(1), JqNumber.of(2), JqNumber.of(3));

        String js = """
                (function(arr) {
                    arr[5] = 99;
                    return arr;
                })(arr)
                """;

        evalAndAssert(js, "arr", input, result -> {
            assertEquals(6, result.getArraySize());
            assertEquals(1, result.getArrayElement(0).asInt());
            assertEquals(2, result.getArrayElement(1).asInt());
            assertEquals(3, result.getArrayElement(2).asInt());
            assertTrue(result.getArrayElement(3).isNull(), "gap should be null");
            assertTrue(result.getArrayElement(4).isNull(), "gap should be null");
            assertEquals(99, result.getArrayElement(5).asInt());
        });
    }

    @Test
    public void array_push_grows_array() {
        JqArray input = JqArray.of(JqNumber.of(1));

        String js = """
                (function(arr) {
                    arr.push(2);
                    arr.push(3);
                    return arr;
                })(arr)
                """;

        evalAndAssert(js, "arr", input, result -> {
            assertEquals(3, result.getArraySize());
            assertEquals(1, result.getArrayElement(0).asInt());
            assertEquals(2, result.getArrayElement(1).asInt());
            assertEquals(3, result.getArrayElement(2).asInt());
        });
    }

    @Test
    public void array_growth_converts_back() {
        JqArray input = JqArray.of(JqNumber.of(1));

        evalAndAssertJq("arr[3] = 99; arr;", "arr", input, converted -> {
            assertInstanceOf(JqArray.class, converted);
            JqArray arr = (JqArray) converted;
            assertEquals(4, arr.length());
            assertEquals(1L, arr.get(0).asLong(0));
            assertTrue(arr.get(1).isNull());
            assertTrue(arr.get(2).isNull());
            assertEquals(99L, arr.get(3).asLong(0));
        });
    }

    // --- Creating new objects in JS (no mutation needed) ---

    @Test
    public void js_map_creates_new_objects_without_mutation() {
        // This pattern doesn't mutate — it creates new objects via object literal
        JqArray input = JqArray.of(
                JqObject.builder().put("x", 1L).put("y", 2L).build(),
                JqObject.builder().put("x", 3L).put("y", 4L).build()
        );

        String js = """
                (function(arr) {
                    var result = [];
                    for (var i = 0; i < arr.length; i++) {
                        result.push({x: arr[i].x, y: arr[i].y, sum: arr[i].x + arr[i].y});
                    }
                    return result;
                })(arr)
                """;

        evalAndAssert(js, "arr", input, result -> {
            assertEquals(2, result.getArraySize());
            assertEquals(3, result.getArrayElement(0).getMember("sum").asInt());
            assertEquals(7, result.getArrayElement(1).getMember("sum").asInt());
        });
    }
}
