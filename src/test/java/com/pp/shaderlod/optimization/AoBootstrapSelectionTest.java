package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.makeupao.MakeUpAoApiPins;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Run alone in a fresh test JVM: Minecraft's version is deliberately not initialized. */
class AoBootstrapSelectionTest {
    @Test void selectsPinnedTupleBeforeMinecraftInitialization() {
        assertTrue(MakeUpAoApiPins.exact("neoforge1.21.1"), MakeUpAoApiPins::status);
        assertFalse(MakeUpAoApiPins.exact("fabric1.21.1"));
        assertFalse(MakeUpAoApiPins.exact("neoforge1.21.11"));
        assertTrue(com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoApiPins.exact());
        assertTrue(com.pp.shaderlod.optimization.unboundao.UnboundAoApiPins.exact());
        assertTrue(com.pp.shaderlod.optimization.sildurao.SildurAoApiPins.exact());
        assertTrue(com.pp.shaderlod.optimization.astraao.AstraAoApiPins.exact());
        assertTrue(com.pp.shaderlod.optimization.solasao.SolasAoApiPins.exact());
    }
}
