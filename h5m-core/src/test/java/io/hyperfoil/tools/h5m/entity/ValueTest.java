package io.hyperfoil.tools.h5m.entity;

import io.hyperfoil.tools.h5m.entity.node.JqNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ValueTest {

    private Value value(String name, Value... sources) {
        Value v = new Value(null, new JqNode(name, "."));
        v.sources = new ArrayList<>(List.of(sources));
        return v;
    }

    @Test
    public void dependsOn_deep_chain() {
        Value first = value("v0");
        Value prev = first;
        for (int i = 1; i < 100; i++) {
            Value next = value("v" + i, prev);
            prev = next;
        }
        Value last = prev;
        assertTrue(last.dependsOn(first), "last value in a 100-deep chain should depend on first");
        assertFalse(first.dependsOn(last), "first value should not depend on last");
    }

    @Test
    public void dependsOn_diamond() {
        Value a = value("a");
        Value b = value("b", a);
        Value c = value("c", a);
        Value d = value("d", b, c);

        assertTrue(d.dependsOn(a), "d should depend on a through both b and c");
        assertTrue(d.dependsOn(b), "d should depend on b");
        assertTrue(d.dependsOn(c), "d should depend on c");
        assertFalse(a.dependsOn(d), "a should not depend on d");
        assertFalse(b.dependsOn(c), "b should not depend on c");
    }

    @Test
    public void dependsOn_null_source() {
        Value v = value("v1");
        assertFalse(v.dependsOn(null), "dependsOn(null) should return false");
    }

    @Test
    public void dependsOn_no_sources() {
        Value a = value("a");
        Value b = value("b");
        assertFalse(a.dependsOn(b), "value with no sources should not depend on anything");
    }
}
