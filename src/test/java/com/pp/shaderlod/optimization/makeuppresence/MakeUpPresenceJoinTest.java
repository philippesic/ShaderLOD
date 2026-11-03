package com.pp.shaderlod.optimization.makeuppresence;

import java.util.*;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MakeUpPresenceJoinTest {
    private static final class Attempt {
        final MakeUpPresenceJoin<Object> join = new MakeUpPresenceJoin<>();
        final Object source = new Object(), raw = new Object();
        final Map<PatchShaderType,String> text = new EnumMap<>(PatchShaderType.class);
        Attempt() {
            for (var stage : List.of(PatchShaderType.VERTEX, PatchShaderType.FRAGMENT, PatchShaderType.GEOMETRY,
                    PatchShaderType.TESS_CONTROL, PatchShaderType.TESS_EVAL)) text.put(stage, null);
            text.put(PatchShaderType.VERTEX, "vertex"); text.put(PatchShaderType.FRAGMENT, "fragment");
        }
        void transformed() {
            join.begin(source, raw); join.patched(source, raw, text, x -> true);
            assertSame(raw, join.transformed(source, text, x -> true));
        }
        Map<PatchShaderType,Object> shaders() {
            return new EnumMap<>(Map.of(PatchShaderType.VERTEX, new Object(), PatchShaderType.FRAGMENT, new Object()));
        }
    }

    @Test void actualMapAndShaderObjectsJoinOnlyOnce() {
        var a = new Attempt(); a.transformed(); var shaders = a.shaders();
        a.join.compiled(new EnumMap<>(a.text), shaders, x -> true);
        assertNull(a.join.linked(shaders, x -> true));
        a.join.compiled(a.text, shaders, x -> true);
        assertNull(a.join.linked(new EnumMap<>(shaders), x -> true));
        assertSame(a.raw, a.join.linked(shaders, x -> true));
        assertNull(a.join.linked(shaders, x -> true));
    }

    @Test void rejectedOrSpentPreparationRevokesTheSameCachedMapAndItsShaderProof() {
        for (boolean spent : List.of(false, true)) {
            var a = new Attempt(); a.transformed(); var oldShaders = a.shaders();
            a.join.compiled(a.text, oldShaders, x -> true);
            a.join.patched(a.source, spent ? a.raw : null, a.text, x -> true);
            assertNull(a.join.transformed(a.source, a.text, x -> true));
            assertNull(a.join.linked(oldShaders, x -> true));
            var fresh = a.shaders(); a.join.compiled(a.text, fresh, x -> true);
            assertNull(a.join.linked(fresh, x -> true));
            a.transformed(); a.join.compiled(a.text, fresh, x -> true);
            assertSame(a.raw, a.join.linked(fresh, x -> true));
        }
    }

    @Test void changedRequestReloadOrMutableOptionsRefuseEveryBoundary() {
        var a = new Attempt(); a.join.begin(a.source, a.raw);
        a.join.patched(a.source, a.raw, a.text, x -> false);
        assertNull(a.join.transformed(a.source, a.text, x -> true));
        a.join.begin(a.source, a.raw); a.join.patched(a.source, a.raw, a.text, x -> true);
        assertNull(a.join.transformed(a.source, a.text, x -> false));
        a.transformed(); var shaders = a.shaders(); a.join.compiled(a.text, shaders, x -> false);
        assertNull(a.join.linked(shaders, x -> true));
        a.transformed(); a.join.compiled(a.text, shaders, x -> true);
        assertNull(a.join.linked(shaders, x -> false));
        assertNull(a.join.linked(shaders, x -> true), "rejected link input was consumed");
    }

    @Test void failedCallOrClearCannotRetainEarlierProof() {
        var a = new Attempt(); a.transformed(); var shaders = a.shaders();
        a.join.compiled(a.text, shaders, x -> true); a.join.failed(a.source);
        assertNull(a.join.linked(shaders, x -> true));
        a.transformed(); a.join.compiled(a.text, shaders, x -> true); a.join.clear();
        assertNull(a.join.linked(shaders, x -> true));
    }

    @Test void foreignReturnAndMutatedTextOrObjectsDoNotLink() {
        var a = new Attempt(); a.join.begin(a.source, a.raw); a.join.patched(a.source, a.raw, a.text, x -> true);
        assertNull(a.join.transformed(a.source, new EnumMap<>(a.text), x -> true));
        a.transformed(); var shaders = a.shaders(); a.join.compiled(a.text, shaders, x -> true);
        shaders.put(PatchShaderType.FRAGMENT, new Object()); assertNull(a.join.linked(shaders, x -> true));
        a.transformed(); a.join.compiled(a.text, shaders, x -> true);
        a.text.put(PatchShaderType.FRAGMENT, "mutated"); assertNull(a.join.linked(shaders, x -> true));
        a.transformed(); a.text.put(PatchShaderType.GEOMETRY, "unexpected geometry");
        a.join.compiled(a.text, shaders, x -> true); assertNull(a.join.linked(shaders, x -> true));
    }
}
