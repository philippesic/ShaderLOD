package com.pp.shaderlod.optimization.depthprime;

import java.util.*;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/** Driver-observed text and handles are captured before Iris deletes the shader wrappers. */
public final class DepthShaderInputs<K,S> {
    private record Entry<K,S>(Map<K,S> objects,Map<K,Integer> handles,Map<K,String> text) {}
    private final Map<Map<K,S>,Entry<K,S>> entries=new IdentityHashMap<>();
    private final ToIntFunction<S> handle;
    private final IntFunction<String> source;
    private final IntPredicate compiled;
    public DepthShaderInputs(ToIntFunction<S> handle,IntFunction<String> source,IntPredicate compiled) {
        this.handle=handle; this.source=source; this.compiled=compiled;
    }
    public void clear() { entries.clear(); }
    public boolean capture(Map<K,String> text,Map<K,S> shaders) {
        if (shaders==null) return false;
        entries.remove(shaders);
        if (text==null || shaders.isEmpty()) return false;
        var present=new HashSet<K>(); text.forEach((key,value)->{if(value!=null)present.add(key);});
        if (!present.equals(shaders.keySet())) return false;
        var handles=new HashMap<K,Integer>();
        try {
            for (var entry:shaders.entrySet()) {
                if (entry.getKey()==null || entry.getValue()==null) return false;
                int id=handle.applyAsInt(entry.getValue());
                if (id<=0 || !compiled.test(id) || !Objects.equals(text.get(entry.getKey()),source.apply(id))) return false;
                handles.put(entry.getKey(),id);
            }
        } catch (RuntimeException invalid) { return false; }
        if (new HashSet<>(handles.values()).size()!=handles.size()) return false;
        var actualText=new HashMap<K,String>(); handles.keySet().forEach(key->actualText.put(key,text.get(key)));
        entries.put(shaders,new Entry<>(Map.copyOf(shaders),Map.copyOf(handles),Map.copyOf(actualText)));
        return true;
    }
    /** Still attached shader objects survive deletion of Iris's Java wrappers until programs release them. */
    public boolean unchangedDriverInput(Map<K,S> shaders) {
        var entry=entries.get(shaders);
        if (entry==null || !entry.objects().equals(shaders)) return false;
        for (var stage:entry.handles().entrySet())
            if (!compiled.test(stage.getValue()) || !Objects.equals(entry.text().get(stage.getKey()),source.apply(stage.getValue()))) return false;
        return true;
    }
    /** Does not dereference a deleted GlShader wrapper at createShader RETURN. */
    public Map<K,Integer> current(Map<K,S> shaders) {
        var entry=entries.get(shaders);
        return entry!=null && entry.objects().equals(shaders)?entry.handles():null;
    }
}
