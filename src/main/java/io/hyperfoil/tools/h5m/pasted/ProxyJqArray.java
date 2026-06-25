package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

/**
 * GraalVM ProxyArray backed by JqArray.
 * Replaces ProxyJacksonArray — eliminates the JqValue→JsonNode conversion
 * for JavaScript interop.
 */
public class ProxyJqArray implements ProxyArray {

    private final JqArray node;

    public ProxyJqArray(JqArray node){
        this.node = node;
    }

    public JqArray getJqArray(){ return node; }

    @Override
    public Object get(long index){
        JqValue value = node.get((int) index);
        if (value == null || value.isNull()) {
            return null;
        }
        return Util.convertFromJq(value);
    }

    @Override
    public void set(long index, Value value) {
        // JqArray is immutable — set is a GraalVM contract requirement
        // but h5m JS functions don't mutate input arrays
        throw new UnsupportedOperationException("JqArray is immutable");
    }

    @Override
    public boolean remove(long index){
        throw new UnsupportedOperationException("JqArray is immutable");
    }

    @Override
    public long getSize(){ return node.length(); }
}
