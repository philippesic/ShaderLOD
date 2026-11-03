package com.pp.shaderlod.integration;

import com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedWorldPixelRuntime;
import com.pp.shaderlod.optimization.unboundspatial.UnboundWorldPixelRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** A missing render-thread scope must return without requiring an OpenGL context. */
final class WorldPixelInactiveDrawTest {
    @Test void reimaginedAbsentScopeNeedsNoGlContext() {
        assertFalse(ReimaginedWorldPixelRuntime.beforeNativeDraw());
        ReimaginedWorldPixelRuntime.finish();
        assertFalse(ReimaginedWorldPixelRuntime.beforeNativeDraw());
    }

    @Test void unboundAbsentScopeNeedsNoGlContext() {
        assertFalse(UnboundWorldPixelRuntime.beforeNativeDraw());
        UnboundWorldPixelRuntime.finish();
        assertFalse(UnboundWorldPixelRuntime.beforeNativeDraw());
    }
}
