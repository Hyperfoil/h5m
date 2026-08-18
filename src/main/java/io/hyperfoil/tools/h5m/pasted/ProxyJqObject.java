package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqNull;
import io.hyperfoil.tools.jjq.value.JqObject;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GraalVM ProxyObject backed by JqObject.
 * <p>
 * Supports all standard JavaScript object operations via copy-on-write:
 * {@link #putMember} and {@link #removeMember} store changes in an overlay
 * map, and child proxy objects/arrays are cached so that nested mutations
 * (e.g., {@code value["arr"][i].field = x}) are visible when the parent is
 * later accessed. {@link #getJqObject()} rebuilds the JqObject from the
 * overlay and cached children.
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

    private final JqObject original;
    // Overlay for mutations and cached child proxies.
    // Lazy-initialized on first mutation or child proxy creation.
    private Map<String, Object> overlay;
    // Keys removed via removeMember. Lazy-initialized.
    private Set<String> removed;

    public ProxyJqObject(JqObject node){
        this.original = node;
    }

    /**
     * Returns the current state as a JqObject, merging any mutations
     * from the overlay (including nested proxy mutations) with the original.
     */
    public JqObject getJqObject() {
        if (overlay == null && removed == null) {
            return original;
        }
        JqObject.Builder builder = JqObject.builder();
        // Start with original keys, applying overlay and removals
        for (String key : original.keys()) {
            if (removed != null && removed.contains(key)) {
                continue;
            }
            if (overlay != null && overlay.containsKey(key)) {
                builder.put(key, resolveOverlayValue(overlay.get(key)));
            } else {
                builder.put(key, original.get(key));
            }
        }
        // Add any keys that only exist in the overlay (new fields added by JS)
        if (overlay != null) {
            for (Map.Entry<String, Object> entry : overlay.entrySet()) {
                if (!original.has(entry.getKey())) {
                    builder.put(entry.getKey(), resolveOverlayValue(entry.getValue()));
                }
            }
        }
        return builder.build();
    }

    /**
     * Resolve an overlay value back to JqValue. If it's a cached child proxy,
     * recursively get its current state.
     */
    static JqValue resolveOverlayValue(Object value) {
        if (value instanceof ProxyJqObject proxyObj) {
            return proxyObj.getJqObject();
        } else if (value instanceof ProxyJqArray proxyArr) {
            return proxyArr.getJqArray();
        } else if (value instanceof JqValue jqValue) {
            return jqValue;
        } else {
            return JqNull.NULL;
        }
    }

    @Override
    public Object getMember(String key) {
        // Removed keys return null
        if (removed != null && removed.contains(key)) {
            return null;
        }
        // Check overlay first (cached child proxies and putMember values)
        if (overlay != null && overlay.containsKey(key)) {
            Object cached = overlay.get(key);
            if (cached instanceof ProxyJqObject || cached instanceof ProxyJqArray) {
                return cached;
            }
            // It's a JqValue from putMember — convert to proxy/primitive
            if (cached instanceof JqValue jqValue) {
                return wrapAndCache(key, jqValue);
            }
            return cached;
        }

        JqValue value = original.get(key);
        if (value == null || value.isNull()) {
            return null;
        }
        return wrapAndCache(key, value);
    }

    /**
     * Wrap a JqValue for GraalVM and cache it in the overlay if it's a
     * proxy type (object/array) so nested mutations are visible.
     */
    private Object wrapAndCache(String key, JqValue value) {
        Object wrapped = Util.convertFromJq(value);
        if (wrapped instanceof ProxyJqObject || wrapped instanceof ProxyJqArray) {
            if (overlay == null) {
                overlay = new LinkedHashMap<>();
            }
            overlay.put(key, wrapped);
        }
        return wrapped;
    }

    @Override
    public Object getMemberKeys() {
        if (overlay == null && removed == null) {
            return ProxyArray.fromList(new ArrayList<>(original.keys()));
        }
        // Merge original keys with overlay keys, excluding removed keys
        List<Object> keys = new ArrayList<>();
        for (String key : original.keys()) {
            if (removed != null && removed.contains(key)) {
                continue;
            }
            keys.add(key);
        }
        if (overlay != null) {
            for (String key : overlay.keySet()) {
                if (!original.has(key)) {
                    keys.add(key);
                }
            }
        }
        return ProxyArray.fromList(keys);
    }

    @Override
    public boolean hasMember(String key) {
        if (removed != null && removed.contains(key)) {
            return false;
        }
        return original.has(key) || (overlay != null && overlay.containsKey(key));
    }

    @Override
    public void putMember(String key, Value value) {
        if (overlay == null) {
            overlay = new LinkedHashMap<>();
        }
        // If this key was previously removed, un-remove it
        if (removed != null) {
            removed.remove(key);
        }
        JqValue jqValue = Util.convertToJqValue(value);
        overlay.put(key, jqValue != null ? jqValue : JqNull.NULL);
    }

    @Override
    public boolean removeMember(String key) {
        boolean existed = hasMember(key);
        if (existed) {
            if (removed == null) {
                removed = new HashSet<>();
            }
            removed.add(key);
            // Also remove from overlay if present
            if (overlay != null) {
                overlay.remove(key);
            }
        }
        return existed;
    }
}
