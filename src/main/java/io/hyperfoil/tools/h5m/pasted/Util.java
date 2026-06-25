package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqBoolean;
import io.hyperfoil.tools.jjq.value.JqNull;
import io.hyperfoil.tools.jjq.value.JqNumber;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqString;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.Proxy;

public class Util {

    /**
     * Convert a GraalVM polyglot Value to JqValue.
     */
    public static JqValue convertToJqValue(Value value) {
        if (value == null) {
            return null;
        } else if (value.isNull()) {
            if (value.toString().contains("undefined")) {
                return JqString.of(""); //no return is the same as returning a missing key from a ProxyObject?
            } else {
                return null;
            }
        } else if (value.isProxyObject()) {
            Proxy p = value.asProxyObject();
            if (p instanceof ProxyJqArray proxyArr) {
                return proxyArr.getJqArray();
            } else if (p instanceof ProxyJqObject proxyObj) {
                return proxyObj.getJqObject();
            } else {
                return JqString.of(p.toString());
            }
        } else if (value.isBoolean()) {
            return JqBoolean.of(value.asBoolean());
        } else if (value.isNumber()) {
            double v = value.asDouble();
            if (v == Math.rint(v)) {
                return JqNumber.of((long) v);
            } else {
                return JqNumber.of(v);
            }
        } else if (value.isString()) {
            return JqString.of(value.asString());
        } else if (value.hasArrayElements()) {
            return convertToJqArray(value);
        } else if (value.canExecute()) {
            return JqString.of(value.toString());
        } else if (value.hasMembers()) {
            return convertToJqObject(value);
        } else {
            return JqString.of("");
        }
    }

    /**
     * Convert a GraalVM polyglot Value with members to JqObject.
     */
    public static JqObject convertToJqObject(Value value) {
        JqObject.Builder builder = JqObject.builder();
        for (String key : value.getMemberKeys()) {
            Value element = value.getMember(key);
            if (element == null || element.isNull()) {
                builder.put(key, JqNull.NULL);
            } else if (element.isBoolean()) {
                builder.put(key, element.asBoolean());
            } else if (element.isNumber()) {
                double v = element.asDouble();
                if (v == Math.rint(v)) {
                    builder.put(key, element.asLong());
                } else {
                    builder.put(key, v);
                }
            } else if (element.isString()) {
                builder.put(key, element.asString());
            } else if (element.hasArrayElements()) {
                builder.put(key, convertToJqArray(element));
            } else if (element.hasMembers()) {
                builder.put(key, convertToJqObject(element));
            } else {
                builder.put(key, element.toString());
            }
        }
        return builder.build();
    }

    /**
     * Convert a GraalVM polyglot Value with array elements to JqArray.
     */
    public static JqArray convertToJqArray(Value value) {
        JqValue[] elements = new JqValue[(int) value.getArraySize()];
        for (int i = 0; i < elements.length; i++) {
            Value element = value.getArrayElement(i);
            if (element == null || element.isNull()) {
                elements[i] = JqNull.NULL;
            } else if (element.isBoolean()) {
                elements[i] = JqBoolean.of(element.asBoolean());
            } else if (element.isNumber()) {
                double v = element.asDouble();
                if (v == Math.rint(v)) {
                    elements[i] = JqNumber.of(element.asLong());
                } else {
                    elements[i] = JqNumber.of(v);
                }
            } else if (element.isString()) {
                elements[i] = JqString.of(element.asString());
            } else if (element.hasArrayElements()) {
                elements[i] = convertToJqArray(element);
            } else if (element.hasMembers()) {
                elements[i] = convertToJqObject(element);
            } else {
                elements[i] = JqString.of(element.toString());
            }
        }
        return JqArray.of(elements);
    }

    /**
     * Convert a JqValue to a Java object suitable for GraalVM proxy getMember/get return.
     * Scalars → Java primitives, objects → ProxyJqObject, arrays → ProxyJqArray.
     */
    public static Object convertFromJq(JqValue value) {
        if (value == null || value.isNull()) {
            return null;
        } else if (value instanceof JqObject obj) {
            return new ProxyJqObject(obj);
        } else if (value instanceof JqArray arr) {
            return new ProxyJqArray(arr);
        } else if (value instanceof JqBoolean bool) {
            return bool.booleanValue();
        } else if (value instanceof JqNumber num) {
            double v = num.doubleValue();
            if (v == Math.rint(v)) {
                return (long) v;
            } else {
                return v;
            }
        } else if (value instanceof JqString str) {
            return str.stringValue();
        } else {
            return value.toJsonString();
        }
    }

}
