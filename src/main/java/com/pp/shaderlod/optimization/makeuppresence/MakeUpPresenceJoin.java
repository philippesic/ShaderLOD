package com.pp.shaderlod.optimization.makeuppresence;

import com.pp.shaderlod.optimization.sourcecache.CompilationLinkEvidence;
import java.util.*;
import java.util.function.Predicate;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;

/** MakeUp's single-use call/return join; the existing helper checks text and shader-object maps. */
final class MakeUpPresenceJoin<C> {
    private record Returned<C>(Object source, C receipt, Map<?, ?> text, Map<?, ?> contents) {}
    private final Map<Object, C> pending = new IdentityHashMap<>();
    private final Map<Object, Returned<C>> calls = new IdentityHashMap<>();
    private final Map<Map<?, ?>, Returned<C>> returned = new IdentityHashMap<>();
    private final Map<Map<?, ?>, Returned<C>> compiled = new IdentityHashMap<>();
    private final CompilationLinkEvidence<Returned<C>> link = new CompilationLinkEvidence<>();

    void begin(Object source, C receipt) {
        pending.remove(source);
        calls.remove(source);
        if (receipt != null) pending.put(source, receipt);
    }

    void patched(Object source, C receipt, Map<?, ?> output, Predicate<C> current) {
        C expected = pending.remove(source);
        calls.remove(source);
        revoke(output); // Repaired prototype rule: rejection also replaces cached-map evidence.
        if (receipt == null || expected != receipt || !stages(output) || !current.test(receipt)) return;
        calls.put(source, new Returned<>(source, receipt, output,
            Collections.unmodifiableMap(new HashMap<>(output))));
    }

    C transformed(Object source, Map<?, ?> output, Predicate<C> current) {
        var call = calls.remove(source);
        pending.remove(source);
        revoke(output);
        if (call == null || call.text != output || !call.contents.equals(output) || !current.test(call.receipt)) return null;
        returned.put(output, call);
        link.transformed(output, call);
        return call.receipt;
    }

    void compiled(Map<?, ?> text, Map<?, ?> shaders, Predicate<C> current) {
        if (shaders == null) return;
        compiled.remove(shaders);
        var proof = returned.get(text);
        if (proof == null || !current.test(proof.receipt)) { link.compiled(null, shaders); return; }
        link.compiled(text, shaders);
        if (link.linked(shaders) == proof) compiled.put(shaders, proof);
    }

    C linked(Map<?, ?> shaders, Predicate<C> current) {
        var proof = compiled.remove(shaders); // A second attempted link cannot reuse this input.
        if (proof == null || returned.get(proof.text) != proof || link.linked(shaders) != proof
                || !proof.contents.equals(proof.text) || !current.test(proof.receipt)) return null;
        return proof.receipt;
    }

    void failed(Object source) {
        pending.remove(source);
        calls.remove(source);
        for (var text : new ArrayList<>(returned.keySet()))
            if (returned.get(text).source == source) revoke(text);
    }

    private void revoke(Map<?, ?> output) {
        if (output == null) return;
        returned.remove(output);
        compiled.values().removeIf(proof -> proof.text == output);
        link.transformed(output, null);
    }

    void clear() { pending.clear(); calls.clear(); returned.clear(); compiled.clear(); link.clear(); }

    boolean retains(C receipt) { return returned.values().stream().anyMatch(proof -> proof.receipt == receipt); }

    private static boolean stages(Map<?, ?> text) {
        return text != null && text.keySet().equals(Set.of(PatchShaderType.VERTEX, PatchShaderType.FRAGMENT,
            PatchShaderType.GEOMETRY, PatchShaderType.TESS_CONTROL, PatchShaderType.TESS_EVAL))
            && text.get(PatchShaderType.VERTEX) instanceof String && text.get(PatchShaderType.FRAGMENT) instanceof String
            && text.get(PatchShaderType.GEOMETRY) == null && text.get(PatchShaderType.TESS_CONTROL) == null
            && text.get(PatchShaderType.TESS_EVAL) == null;
    }
}
