package com.pp.shaderlod.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedReceiverCompiledPrograms;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundReceiverCompiledPrograms;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.shaderpack.ShaderPack;
import org.junit.jupiter.api.Test;

/** Native rejection tombstones and legacy routing must survive collection changes. */
class ComplementaryReceiverRegistrationTest {
    @Test void reimaginedRegistrationLifecycle() throws Exception {
        verify(ReimaginedReceiverCompiledPrograms.class);
    }

    @Test void unboundRegistrationLifecycle() throws Exception {
        verify(UnboundReceiverCompiledPrograms.class);
    }

    @SuppressWarnings("unchecked")
    private static void verify(Class<?> type) throws Exception {
        var nativeField = type.getDeclaredField("NATIVE");
        var boundaryField = type.getDeclaredField("BOUNDARIES");
        var legacyField = type.getDeclaredField("LEGACY");
        nativeField.setAccessible(true);
        boundaryField.setAccessible(true);
        legacyField.setAccessible(true);
        var natives = (Map<Integer,Object>) nativeField.get(null);
        var boundaries = (Set<Integer>) boundaryField.get(null);
        var legacy = (Set<Integer>) legacyField.get(null);
        var clear = type.getMethod("clear");
        var registered = type.getMethod("nativeRegistration", int.class);
        var boundary = type.getMethod("ownsBoundary", int.class);
        var requires = type.getMethod("requiresNative", int.class, ShaderPack.class);
        Object firstPipeline = new Object(), secondPipeline = new Object();
        int first = 100_001, second = 100_002;
        clear.invoke(null);
        try {
            assertEquals(false, registered.invoke(null, first));
            natives.put(first, firstPipeline);
            natives.put(second, secondPipeline);
            boundaries.add(first);
            boundaries.add(second);
            assertEquals(true, registered.invoke(null, first));
            assertEquals(true, requires.invoke(null, first, null));
            assertEquals(true, boundary.invoke(null, first));

            type.getMethod("destroy", Object.class).invoke(null, firstPipeline);
            assertEquals(false, registered.invoke(null, first));
            assertEquals(false, boundary.invoke(null, first));
            assertEquals(true, registered.invoke(null, second));
            assertEquals(true, boundary.invoke(null, second));

            type.getMethod("legacy", int.class).invoke(null, second);
            assertEquals(false, registered.invoke(null, second));
            assertEquals(false, boundary.invoke(null, second));
            assertEquals(false, requires.invoke(null, second, null));
            assertTrue(legacy.contains(second));
            type.getMethod("forget", int.class).invoke(null, second);
            assertFalse(legacy.contains(second));

            // A retained rejected registration is present even without a pipeline value.
            natives.put(first, null);
            assertEquals(true, registered.invoke(null, first));
            assertEquals(true, requires.invoke(null, first, null));
            type.getMethod("forget", int.class).invoke(null, first);
            assertEquals(false, registered.invoke(null, first));
            clear.invoke(null);
            assertTrue(natives.isEmpty());
            assertTrue(boundaries.isEmpty());
            assertTrue(legacy.isEmpty());
        } finally {
            clear.invoke(null);
        }
    }
}
