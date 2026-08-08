package io.hyperfoil.tools.h5m.cli;

import io.hyperfoil.tools.jjq.JqProgram;
import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqNull;
import io.hyperfoil.tools.jjq.value.JqNumber;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import io.hyperfoil.tools.jjq.value.JqValues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link JsToJqPatterns} JS→jq conversion.
 * Each test verifies both the pattern matching (returns non-null jq expression)
 * and the semantic correctness (jq produces the expected result).
 */
class JsToJqPatternsTest {

    // --- Pattern 1: Simple division ---

    @Test
    void simpleDivision() {
        String jq = JsToJqPatterns.tryConvert("time => time / 1000000");
        assertNotNull(jq);
        assertEquals(". / 1000000", jq);
        assertJqResult(jq, JqNumber.of(5000000), JqNumber.of(5.0));
    }

    @Test
    void simpleDivisionScientific() {
        String jq = JsToJqPatterns.tryConvert("v => v / 1e+6;");
        assertNotNull(jq);
        assertEquals(". / 1000000", jq);
    }

    @Test
    void simpleDivisionWithParens() {
        String jq = JsToJqPatterns.tryConvert("(v) => v / 1e+6");
        assertNotNull(jq);
    }

    // --- Pattern 2: Division + toFixed ---

    @Test
    void divisionToFixed() {
        String jq = JsToJqPatterns.tryConvert("value => parseFloat((value / 1000000).toFixed(2))");
        assertNotNull(jq);
        assertJqResult(jq, JqNumber.of(5123456), JqNumber.of(5.12));
    }

    // --- Pattern 3: Simple array mean ---

    @Test
    void arrayMeanSimple() {
        String jq = JsToJqPatterns.tryConvert("fd => fd.reduce((a,b) => a+b) / fd.length");
        assertNotNull(jq);
        assertEquals("add / length", jq);
        JqArray input = JqArray.of(JqNumber.of(10), JqNumber.of(20), JqNumber.of(30));
        assertJqResult(jq, input, JqNumber.of(20.0));
    }

    @Test
    void arrayMeanSimpleWithParens() {
        String jq = JsToJqPatterns.tryConvert("(start) => start.reduce((a,b) => a+b) / start.length");
        assertNotNull(jq);
        assertEquals("add / length", jq);
    }

    // --- Pattern 4: Array mean with null guard ---

    @Test
    void arrayMeanGuarded() {
        String jq = JsToJqPatterns.tryConvert(
                "value => { if(!value) { return 0 } else { return parseFloat(((value.reduce((a,b) => a+b, 0))/value.length).toFixed(3)) || null }; }");
        assertNotNull(jq);
        // Test with array input
        JqArray input = JqArray.of(JqNumber.of(10), JqNumber.of(20), JqNumber.of(30));
        assertJqResult(jq, input, JqNumber.of(20.0));
        // Test with null input
        assertJqResult(jq, JqNull.NULL, JqNumber.of(0));
    }

    @Test
    void arrayMeanGuardedWithMultiplier() {
        String jq = JsToJqPatterns.tryConvert(
                "value => { if(!value) { return 0 } else { return parseFloat(((value.reduce((a,b) => a+b, 0))/value.length*1000).toFixed(3))} };");
        assertNotNull(jq);
        JqArray input = JqArray.of(JqNumber.of(1.5), JqNumber.of(2.5));
        JqValue result = evalJq(jq, input);
        assertNotNull(result);
        // (1.5 + 2.5) / 2 * 1000 = 2000.0
        assertEquals(2000.0, result.asDouble(0), 0.001);
    }

    // --- Pattern 5: Conditional filter + array mean ---

    @Test
    void conditionalArrayMean() {
        String jq = JsToJqPatterns.tryConvert(
                "value => { if (value[\"workload\"] != \"autobench\") { return null } if(!value[\"results\"]) { return 0 } " +
                "else { return parseFloat(((value[\"results\"].reduce((a,b) => a+b, 0))/value[\"results\"].length).toFixed(3)) } }");
        assertNotNull(jq);
        // Test with matching workload
        JqValue input = JqValues.parse("{\"workload\":\"autobench\",\"results\":[10,20,30]}");
        assertJqResult(jq, input, JqNumber.of(20.0));
        // Test with non-matching workload
        JqValue nonMatch = JqValues.parse("{\"workload\":\"other\",\"results\":[10]}");
        assertJqResult(jq, nonMatch, JqNull.NULL);
    }

    // --- Pattern 6: Array max ---

    @Test
    void arrayMax() {
        String jq = JsToJqPatterns.tryConvert("fd => Math.max(...fd)");
        assertNotNull(jq);
        assertEquals("max", jq);
        JqArray input = JqArray.of(JqNumber.of(3), JqNumber.of(7), JqNumber.of(1));
        assertJqResult(jq, input, JqNumber.of(7));
    }

    // --- Pattern 7: Array min ---

    @Test
    void arrayMin() {
        String jq = JsToJqPatterns.tryConvert("fd => Math.min(...fd)");
        assertNotNull(jq);
        assertEquals("min", jq);
        JqArray input = JqArray.of(JqNumber.of(3), JqNumber.of(7), JqNumber.of(1));
        assertJqResult(jq, input, JqNumber.of(1));
    }

    // --- Pattern 8: Null guard ---

    @Test
    void nullGuard() {
        String jq = JsToJqPatterns.tryConvert(
                "value => { if (value == null) { return \"N/A\" } else { return value } }");
        assertNotNull(jq);
        assertJqResult(jq, JqNull.NULL, JqString.of("N/A"));
        assertJqResult(jq, JqNumber.of(42), JqNumber.of(42));
    }

    // --- Pattern 9: parseInt ---

    @Test
    void parseInt() {
        String jq = JsToJqPatterns.tryConvert("value => Number.parseInt(value)");
        assertNotNull(jq);
        assertJqResult(jq, JqString.of("42"), JqNumber.of(42));
    }

    @Test
    void parseIntWithoutNumber() {
        String jq = JsToJqPatterns.tryConvert("value => parseInt(value)");
        assertNotNull(jq);
    }

    // --- Pattern 10: Array max of field with rounding ---

    @Test
    void arrayMaxFieldRounded() {
        String jq = JsToJqPatterns.tryConvert(
                "(value) => Math.round(value.reduce((curMax, result) => Math.max(curMax, result[\"throughput\"]), 0))");
        assertNotNull(jq);
        JqValue input = JqValues.parse("[{\"throughput\":100.7},{\"throughput\":200.3},{\"throughput\":150.5}]");
        assertJqResult(jq, input, JqNumber.of(200));
    }

    // --- Non-convertible patterns ---

    @Test
    void complexTransformerNotConverted() {
        assertNull(JsToJqPatterns.tryConvert(
                "({stressng_sample_uuid, fio_sample_uuid}) => { var sngmap = stressng_sample_uuid.map((value, i) => ({uuid: value})); return [...sngmap]; }"));
    }

    @Test
    void dateManipulationNotConverted() {
        assertNull(JsToJqPatterns.tryConvert(
                "value => { var test_date = new Date(value[\"start_time\"]); return test_date.toDateString(); }"));
    }

    @Test
    void objectKeysNotConverted() {
        assertNull(JsToJqPatterns.tryConvert(
                "value => { const workloads = []; for (const section of Object.keys(value[\"results\"])) { workloads.push(value[\"results\"][section]); }; return workloads; }"));
    }

    @Test
    void generatorFunctionNotConverted() {
        assertNull(JsToJqPatterns.tryConvert(
                "function* dataset({foo, bar, biz}){ yield foo; yield bar; yield biz; }"));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(JsToJqPatterns.tryConvert(null));
        assertNull(JsToJqPatterns.tryConvert(""));
        assertNull(JsToJqPatterns.tryConvert("   "));
    }

    // --- Helpers ---

    private static JqValue evalJq(String jqExpression, JqValue input) {
        JqProgram program = JqProgram.compile(jqExpression);
        JqValue result = program.apply(input);
        // If the result is an array from multiple outputs, take the first
        if (result instanceof JqArray arr && arr.length() > 0) {
            return arr.get(0);
        }
        return result;
    }

    private static void assertJqResult(String jqExpression, JqValue input, JqValue expected) {
        JqValue actual = evalJq(jqExpression, input);
        if (expected instanceof JqNumber e && actual instanceof JqNumber a) {
            assertEquals(e.asDouble(0), a.asDouble(0), 0.001,
                    "jq expression: " + jqExpression);
        } else {
            assertEquals(expected, actual, "jq expression: " + jqExpression);
        }
    }
}
