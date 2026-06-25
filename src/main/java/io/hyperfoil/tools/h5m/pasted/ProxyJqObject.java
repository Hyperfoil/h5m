package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.List;

/**
 * GraalVM ProxyObject backed by JqObject.
 * Replaces ProxyJacksonObject — eliminates the JqValue→JsonNode conversion
 * for JavaScript interop.
 */
public class ProxyJqObject implements ProxyObject {

    public static class InstanceCheck implements ProxyExecutable {

        @Override
        public Object execute(Value...args){
            if(args.length<1){
                return false;
            }else{
                Value obj = args[0];
                return obj.isProxyObject() && obj.asProxyObject() instanceof ProxyJqObject || obj.hasMembers();
            }
        }
    }

    private final JqObject node;

    public ProxyJqObject(JqObject node){
        this.node = node;
    }

    public JqObject getJqObject(){ return node; }

    @Override
    public Object getMember(String key) {
        JqValue value = node.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return Util.convertFromJq(value);
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromList(new ArrayList<>(node.keys()));
    }

    @Override
    public boolean hasMember(String key) {
        return node.has(key);
    }

    @Override
    public void putMember(String key, Value value) {
        // JqObject is immutable — putMember is a GraalVM contract requirement
        // but h5m JS functions don't mutate input objects
        throw new UnsupportedOperationException("JqObject is immutable");
    }

    @Override
    public boolean removeMember(String key) {
        throw new UnsupportedOperationException("JqObject is immutable");
    }
}
