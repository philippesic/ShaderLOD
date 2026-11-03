package com.pp.shaderlod.optimization.sourcecache;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Exact map identity and immutable contents join transformed text to returned shader objects. */
public final class CompilationLinkEvidence<C> {
    private record Entry<C>(Map<?,?> contents, C certificate) {}
    private final Map<Map<?,?>,Entry<C>> transformed = new IdentityHashMap<>();
    private final Map<Map<?,?>,Entry<C>> shaders = new IdentityHashMap<>();
    public void clear() { transformed.clear(); shaders.clear(); }
    public void transformed(Map<?,?> text, C certificate) {
        if (text != null) {
            transformed.remove(text);
            if (certificate != null && !text.isEmpty() && text.keySet().stream().allMatch(Objects::nonNull))
                transformed.put(text,new Entry<>(Collections.unmodifiableMap(new HashMap<>(text)),certificate));
        }
    }
    public void compiled(Map<?,?> text, Map<?,?> shaderObjects) {
        if (shaderObjects == null) return;
        shaders.remove(shaderObjects);
        var entry = transformed.get(text);
        if (entry != null && entry.contents().equals(text)
                && nonNull(shaderObjects) && presentStages(text).equals(shaderObjects.keySet()))
            shaders.put(shaderObjects,new Entry<>(Map.copyOf(shaderObjects),entry.certificate()));
    }
    public C linked(Map<?,?> shaderObjects) {
        var entry = shaders.get(shaderObjects);
        return entry != null && entry.contents().equals(shaderObjects) ? entry.certificate() : null;
    }
    /** Pinned Iris keeps absent text stages as null, then omits them from compiled objects. */
    private static java.util.Set<Object> presentStages(Map<?,?> text) {
        var stages=new HashSet<Object>();
        text.forEach((key,value)->{if(value!=null)stages.add(key);});
        return stages;
    }
    private static boolean nonNull(Map<?,?> map) {
        return !map.isEmpty() && map.keySet().stream().allMatch(Objects::nonNull)
            && map.values().stream().allMatch(Objects::nonNull);
    }
}
