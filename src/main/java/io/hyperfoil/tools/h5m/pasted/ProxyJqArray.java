package io.hyperfoil.tools.h5m.pasted;

import io.hyperfoil.tools.jjq.value.JqArray;
import io.hyperfoil.tools.jjq.value.JqNull;
import io.hyperfoil.tools.jjq.value.JqValue;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;

import java.util.ArrayList;
import java.util.List;

/**
 * GraalVM ProxyArray backed by JqArray.
 * <p>
 * Supports all standard JavaScript array operations. On first mutation
 * ({@link #set} or {@link #remove}), the backing switches from the
 * immutable {@link JqArray} to a mutable {@link ArrayList} that supports
 * index-based set (including array growth), element removal with index
 * shifting, and nested proxy caching for mutation propagation.
 * <p>
 * {@link #getJqArray()} rebuilds an immutable JqArray from the current
 * mutable state, resolving any cached child proxies recursively.
 */
public class ProxyJqArray implements ProxyArray {

    private final JqArray original;
    // Mutable backing list, lazily created on first mutation.
    // Contains a mix of JqValue (for scalars/unaccessed elements) and
    // ProxyJqObject/ProxyJqArray (for cached child proxies).
    private List<Object> mutable;

    public ProxyJqArray(JqArray node){
        this.original = node;
    }

    /**
     * Returns the current state as a JqArray, resolving any cached
     * child proxies and mutable-list changes.
     */
    public JqArray getJqArray() {
        if (mutable == null) {
            return original;
        }
        JqValue[] elements = new JqValue[mutable.size()];
        for (int i = 0; i < elements.length; i++) {
            elements[i] = ProxyJqObject.resolveOverlayValue(mutable.get(i));
        }
        return JqArray.of(elements);
    }

    /**
     * Lazily initialize the mutable list by copying from the original.
     */
    private List<Object> ensureMutable() {
        if (mutable == null) {
            mutable = new ArrayList<>(original.length());
            for (int i = 0; i < original.length(); i++) {
                mutable.add(original.get(i));
            }
        }
        return mutable;
    }

    @Override
    public Object get(long index){
        int idx = (int) index;
        if (mutable != null) {
            if (idx < 0 || idx >= mutable.size()) {
                return null;
            }
            Object cached = mutable.get(idx);
            if (cached instanceof ProxyJqObject || cached instanceof ProxyJqArray) {
                return cached;
            }
            if (cached instanceof JqValue jqValue) {
                if (jqValue.isNull()) {
                    return null;
                }
                return wrapAndCache(idx, jqValue);
            }
            return cached;
        }

        JqValue value = original.get(idx);
        if (value == null || value.isNull()) {
            return null;
        }
        return wrapAndCache(idx, value);
    }

    /**
     * Wrap a JqValue for GraalVM and cache it in the mutable list if it's a
     * proxy type (object/array) so nested mutations are visible.
     */
    private Object wrapAndCache(int index, JqValue value) {
        Object wrapped = Util.convertFromJq(value);
        if (wrapped instanceof ProxyJqObject || wrapped instanceof ProxyJqArray) {
            ensureMutable().set(index, wrapped);
        }
        return wrapped;
    }

    @Override
    public void set(long index, Value value) {
        int idx = (int) index;
        List<Object> list = ensureMutable();
        JqValue jqValue = Util.convertToJqValue(value);
        Object toStore = jqValue != null ? jqValue : JqNull.NULL;
        // Support array growth: fill gaps with null if index is beyond current size
        while (idx >= list.size()) {
            list.add(JqNull.NULL);
        }
        list.set(idx, toStore);
    }

    @Override
    public boolean remove(long index){
        int idx = (int) index;
        List<Object> list = ensureMutable();
        if (idx < 0 || idx >= list.size()) {
            return false;
        }
        list.remove(idx);
        return true;
    }

    @Override
    public long getSize(){
        return mutable != null ? mutable.size() : original.length();
    }
}
