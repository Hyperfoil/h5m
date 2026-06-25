package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;

/**
 * Factory that wraps JqValue into the appropriate GraalVM proxy type.
 * Replaces ProxyJackson — operates on JqValue instead of JsonNode.
 */
public class ProxyJq {

    public static Object wrap(JqValue value){
        if(value == null || value.isNull()){
            return null;
        }else if (value instanceof JqObject obj){
            return new ProxyJqObject(obj);
        }else if (value instanceof JqArray arr){
            return new ProxyJqArray(arr);
        }else{
            // Scalars (string, number, boolean) — return Java primitives
            // which GraalVM handles natively
            return Util.convertFromJq(value);
        }
    }
}
