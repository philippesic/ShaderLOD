package com.pp.shaderlod.optimization;

import com.pp.shaderlod.optimization.reimaginedearlyalpha.*;
import com.pp.shaderlod.optimization.unboundearlyalpha.*;
import com.pp.shaderlod.optimization.sourcecache.LinkedProgramLocations;
import java.util.Map;
import java.util.function.BiPredicate;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComplementaryReceiverCompilationTest {
    private static final AlphaTest CUTOUT = new AlphaTest(AlphaTestFunction.GREATER,.1f);
    private static String protocol(String family) {
        return "uniform int shaderlodComplementaryReceiverPermission;\n"
            + "uniform int shaderlodOptShadowReceiverFootprint;\n"
            + "uniform int shaderlod"+family+"EarlyAlphaPermission;\n"
            + "uniform float shaderlod"+family+"EarlyAlphaReference;\n"
            + "uniform int shaderlodOptEarlyAlpha;\n"
            + "uniform float iris_currentAlphaTest;\n"
            + "bool shaderlod"+family+"NativeAlphaReject(float finalAlpha){ return false;}\n"
            + "// textureLod\n";
    }
    private static String nativeFragment(String family,AlphaTest alpha) {
        return protocol(family)+"void main(){vec4 color=vec4(1.0);if(shaderlod"+family+"NativeAlphaReject(color.a))discard;"
            +alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","")+"}";
    }
    private void passLaw(BiPredicate<SodiumPrograms.Pass,AlphaTest> accepts) {
        for(var pass:SodiumPrograms.Pass.values()) {
            assertEquals(pass==SodiumPrograms.Pass.TERRAIN,accepts.test(pass,AlphaTest.ALWAYS));
            assertEquals(pass==SodiumPrograms.Pass.TERRAIN_CUTOUT,accepts.test(pass,CUTOUT));
            for(float ref:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-.1f,1.1f})
                assertFalse(accepts.test(pass,new AlphaTest(AlphaTestFunction.GREATER,ref)));
            assertFalse(accepts.test(pass,new AlphaTest(AlphaTestFunction.LESS,.1f)));
            assertFalse(accepts.test(pass,null));
        }
        assertFalse(accepts.test(null,CUTOUT));
    }
    @Test void exactSolidAlwaysAndCutoutFiniteGreaterPassesOnly() {
        passLaw(ReimaginedReceiverCompiledPrograms::acceptsPass);
        passLaw(UnboundReceiverCompiledPrograms::acceptsPass);
    }
    private void transformedLaw(String family,BiPredicate<String,AlphaTest> accepts,
            BiPredicate<String,AlphaTest> earlyAlpha,java.util.function.BiFunction<String,AlphaTest,String> prepare) {
        String solid=nativeFragment(family,AlphaTest.ALWAYS),cutout=nativeFragment(family,CUTOUT);
        assertTrue(accepts.test(solid,AlphaTest.ALWAYS));
        assertFalse(earlyAlpha.test(solid,AlphaTest.ALWAYS),"receiver never widens early-alpha admission");
        assertTrue(accepts.test(cutout,CUTOUT),"native late discard is sufficient for receiver");
        assertFalse(earlyAlpha.test(cutout,CUTOUT),"unchanged stub is never alpha evidence");
        String rewritten=prepare.apply(cutout,CUTOUT);
        assertNotNull(rewritten);assertTrue(earlyAlpha.test(rewritten,CUTOUT));assertTrue(accepts.test(rewritten,CUTOUT));
        assertFalse(accepts.test(cutout.replace("iris_FragData0.a > iris_currentAlphaTest","iris_FragData0.a < iris_currentAlphaTest"),CUTOUT));
        assertFalse(accepts.test(cutout.replace("uniform float iris_currentAlphaTest;",""),CUTOUT));
        assertFalse(accepts.test(cutout.replace("return false;","return true;"),CUTOUT));
        assertFalse(accepts.test(cutout.replace("shaderlodComplementaryReceiverPermission","foreignPermission"),CUTOUT));
        assertFalse(accepts.test(solid.replace("return false;","return true;"),AlphaTest.ALWAYS));
        assertFalse(accepts.test(null,CUTOUT));
    }
    @Test void nativeAndKnownRewriteKeepLateDiscardWithoutWideningAlphaContract() {
        transformedLaw("Reimagined",ReimaginedReceiverCompiledPrograms::acceptsTransformed,
            ReimaginedAlphaInterface::transformed,ReimaginedAlphaInterface::prepare);
        transformedLaw("Unbound",UnboundReceiverCompiledPrograms::acceptsTransformed,
            UnboundAlphaInterface::transformed,UnboundAlphaInterface::prepare);
    }
    @Test void siblingFamilyCannotResetSharedReceiverUniformAtDrawBoundary() {
        String reimagined="shaderlodReimaginedTemporalShadowSamplingQualified shaderlodComplementaryReceiverPermission";
        String unbound=reimagined.replace("Reimagined","Unbound");
        assertTrue(ReimaginedReceiverCompiledPrograms.receiverBoundary(SodiumPrograms.Pass.TERRAIN,"gbuffers_terrain",reimagined));
        assertFalse(ReimaginedReceiverCompiledPrograms.receiverBoundary(SodiumPrograms.Pass.TERRAIN,"gbuffers_terrain",unbound));
        assertTrue(UnboundReceiverCompiledPrograms.receiverBoundary(SodiumPrograms.Pass.TERRAIN_CUTOUT,"gbuffers_terrain",unbound));
        assertFalse(UnboundReceiverCompiledPrograms.receiverBoundary(SodiumPrograms.Pass.TERRAIN,"gbuffers_terrain",reimagined));
        assertFalse(UnboundReceiverCompiledPrograms.receiverBoundary(SodiumPrograms.Pass.TERRAIN,"shadow",unbound));
    }
    private static final class Program extends GlProgram<Object> {
        Program(int id) { super(id,unused -> null); }
    }
    @Test void actualSodiumObjectInvalidationCannotLendLocationsToReusedName() {
        var store=new LinkedProgramLocations<GlProgram<?>,String>(GlProgram::handle);
        var program=new Program(7);store.put(7,program,"native",Map.of("permission",3));
        assertEquals(3,store.current(7).locations().get("permission"));
        program.invalidateHandle();assertThrows(IllegalStateException.class,program::handle);
        assertNull(store.current(7));store.observe(7,new Program(7));assertNull(store.current(7));
    }
    @Test void rejectedNativeRegistrationHasNoLegacyRescueAndExplicitLegacyDoesNotInherit() {
        ReimaginedReceiverCompiledPrograms.clear();UnboundReceiverCompiledPrograms.clear();
        var program=new Program(7);
        ReimaginedReceiverCompiledPrograms.linked(null,SodiumPrograms.Pass.TERRAIN,null,AlphaTest.ALWAYS,Map.of(),program,1,Map.of());
        assertTrue(ReimaginedReceiverCompiledPrograms.requiresNative(7,null));
        assertNull(ReimaginedReceiverCompiledPrograms.current(7,null,null,1));
        program.invalidateHandle();assertNull(ReimaginedReceiverCompiledPrograms.current(7,null,null,1));
        assertTrue(ReimaginedReceiverCompiledPrograms.requiresNative(7,null),"stale/rejected native remains blocked");
        ReimaginedReceiverCompiledPrograms.legacy(7);
        assertFalse(ReimaginedReceiverCompiledPrograms.requiresNative(7,null));assertNull(ReimaginedReceiverCompiledPrograms.locations(7));
        UnboundReceiverCompiledPrograms.linked(null,SodiumPrograms.Pass.TERRAIN,null,AlphaTest.ALWAYS,Map.of(),new Program(8),1,Map.of());
        assertTrue(UnboundReceiverCompiledPrograms.requiresNative(8,null));
        assertNull(UnboundReceiverCompiledPrograms.current(8,null,null,1));
        UnboundReceiverCompiledPrograms.clear();assertFalse(UnboundReceiverCompiledPrograms.requiresNative(8,null));
        ReimaginedReceiverCompiledPrograms.clear();
    }
    @Test void recipeBytesMustMatchCurrentPinnedPackagedResource() throws Exception {
        for(String family:new String[]{"reimagined","unbound"})try(var in=getClass().getResourceAsStream(
                "/assets/shaderlod/adapters/patches/adapters/complementary-"+family+".json")) {
            assertNotNull(in);byte[] bytes=in.readAllBytes();
            java.util.function.Predicate<byte[]> matches=family.equals("reimagined")?ReimaginedReceiverCompiledPrograms::recipeMatches:UnboundReceiverCompiledPrograms::recipeMatches;
            assertTrue(matches.test(bytes));bytes[bytes.length-1]^=1;assertFalse(matches.test(bytes));assertFalse(matches.test(null));
        }
    }
}
