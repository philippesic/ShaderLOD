package com.pp.shaderlod.optimization;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.pp.shaderlod.optimization.DepthTextureStorage.*;

/** Pure queried-value and actual binding-record checks; no GL context or rendered claim. */
class DepthTextureStorageTest {
    @Test void unsizedDepthRequiresKnownPrecisionAndComponentRepresentation(){
        for(int bits=-1;bits<=64;bits++) {
            assertEquals(bits==16||bits==24||bits==32,accepts(UNSIZED_DEPTH,bits,UNSIGNED_NORMALIZED),"normalized bits="+bits);
            assertEquals(bits==32,accepts(UNSIZED_DEPTH,bits,FLOAT),"float bits="+bits);
            for(int type:new int[]{0,-1,0x8F9C,0x1404,0x1405,0x1403,0x1402,0x140A})
                assertFalse(accepts(UNSIZED_DEPTH,bits,type),"unknown/signed/integer representation "+type);
        }
    }
    @Test void existingSizedDepthAndDepthStencilSetIsUnchanged(){
        for(int format:new int[]{0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD})
            assertTrue(accepts(format,0,0),"Sized formats do not require extra queries");
        for(int format:new int[]{0,-1,0x1908,0x8814,0x84F9})
            assertFalse(accepts(format,24,UNSIGNED_NORMALIZED),"Other formats are not depth admission");
    }
    @FunctionalInterface private interface BindingFactory {Object make(int format,int bits,int type);}
    private static List<BindingFactory> bindings(){return List.of(
        (f,b,t)->new com.pp.shaderlod.optimization.solasao.SolasAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f),
        (f,b,t)->new com.pp.shaderlod.optimization.astraao.AstraAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f),
        // Bliss uses this exact MakeUp binding inside its Inputs equality.
        (f,b,t)->new com.pp.shaderlod.optimization.makeupao.MakeUpAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f),
        (f,b,t)->new com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f),
        (f,b,t)->new com.pp.shaderlod.optimization.unboundao.UnboundAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f),
        (f,b,t)->new com.pp.shaderlod.optimization.sildurao.SildurAoGlProof.Binding(6,3840,2160,f,b,t,0,9728,9728,0,33071,33071,1f));}
    @Test void storageMutationInvalidatesEveryActualBindingEvenWhenBothRepresentationsAreAccepted(){
        for(var factory:bindings()) {
            var before=factory.make(UNSIZED_DEPTH,24,UNSIGNED_NORMALIZED);
            assertEquals(before,factory.make(UNSIZED_DEPTH,24,UNSIGNED_NORMALIZED));
            assertNotEquals(before,factory.make(UNSIZED_DEPTH,32,UNSIGNED_NORMALIZED));
            assertNotEquals(factory.make(UNSIZED_DEPTH,32,UNSIGNED_NORMALIZED),factory.make(UNSIZED_DEPTH,32,FLOAT));
            assertNotEquals(before,factory.make(0x81A6,0,0),"Raw format must not be canonicalized away");
            assertNotEquals(before,factory.make(UNSIZED_DEPTH,0,0),"Unavailable storage queries are not the old binding");
        }
    }
}
