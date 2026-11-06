package com.pp.shaderlod.optimization.framecopy;

import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.LodSettings;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility;
import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.CompileOptionControl.NativeOption;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.OptimizationEditTransaction;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-only handoff tests; root runs after the shared checkout freeze. No client or GL. */
public class MakeUpDeadCopyAdmissionTest {
    @Test void linuxAdmissionIsBoundedToMeasuredAsahiTuple() {
        String renderer="Apple M1 (G13G B1)",driver="4.6 (Core Profile) Mesa 25.1.0-asahi20250221";
        assertTrue(MakeUpDeadCopyAdmission.platformQualified("Linux",renderer,driver));
        assertTrue(MakeUpDeadCopyAdmission.platformQualified("Mac OS X",null,null));
        assertFalse(MakeUpDeadCopyAdmission.platformQualified("Linux","Apple M2",driver));
        assertFalse(MakeUpDeadCopyAdmission.platformQualified("Linux",renderer,driver+"-foreign"));
        assertFalse(MakeUpDeadCopyAdmission.platformQualified("Linux",null,driver));
        assertFalse(MakeUpDeadCopyAdmission.platformQualified("Windows",renderer,driver));
        assertFalse(MakeUpDeadCopyAdmission.platformQualified(null,renderer,driver));
    }
    @Test void windowsAdmissionRequiresTheActualFiniteRendererDriverAndArchitecture() {
        String renderer="NVIDIA GeForce RTX 5080/PCIe/SSE2";
        for(String driver:Set.of("3.2.0 NVIDIA 617.42","4.6.0 NVIDIA 617.42")) {
            assertTrue(MakeUpDeadCopyAdmission.platformQualified("Windows 11","amd64",renderer,driver));
            assertFalse(MakeUpDeadCopyAdmission.platformQualified("Windows 11","aarch64",renderer,driver));
            assertFalse(MakeUpDeadCopyAdmission.platformQualified("Windows 11","amd64","NVIDIA GeForce RTX 2080 Ti/PCIe/SSE2",driver));
            assertFalse(MakeUpDeadCopyAdmission.platformQualified("Windows 11","amd64",renderer,driver+" unknown"));
        }
    }
    @Test void inactiveNewOptionPreservesExistingShadowProfiles() {
        var options=new TreeMap<>(com.pp.shaderlod.optimization.makeupao.MakeUpAoContract.generated("0"));
        assertEquals(114,options.size());
        assertTrue(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.coloredBudgetProfile(options));
        options.put("COLORED_SHADOW","false");
        assertTrue(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.profile(options));
        options.put("SHADERLOD_MAKEUP_DEAD_FRAME_COPY","1");
        assertFalse(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.profile(options));
        options.put("COLORED_SHADOW","true");
        assertFalse(com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime.coloredBudgetProfile(options));
    }
    @Test void onlyRecordedVersionMetadataLabelsNormalize() {
        for(String value:Set.of("1.8.12","1.8.12+mc1.21.1","1.8.12-snapshot+mc1.21.1-local"))
            assertEquals("1.8.12",MakeUpDeadCopyAdmission.canonicalIrisMetadata(value));
        for(String value:Set.of("1.8.13","1.8.12-snapshot+mc1.21.1-foreign","1.8.12+mc1.21.2"))
            assertEquals(value,MakeUpDeadCopyAdmission.canonicalIrisMetadata(value));
    }

    private static final String KEY = CompileOptionControl.MAKEUP_DEAD_FRAME_COPY;
    private static final MakeUpDeadCopyAdmission.Context VALID = context("1.21.1", "neoforge", "1.8.12", true, true, "minecraft:overworld", false, false);
    private static MakeUpDeadCopyAdmission.Context context(String mc, String loader, String iris,
            boolean api, boolean source, String dimension, boolean dh, boolean voxy) {
        return new MakeUpDeadCopyAdmission.Context(mc, loader, iris, api, source, dimension, dh, voxy);
    }
    private static NativeOption binary(String value) { return new NativeOption("STRING", Set.of("0", "1"), "0", value); }
    private static Map<String, NativeOption> options() {
        assertEquals(100, MakeUpQuadEligibility.nativeOptions().size(), "Pinned native100 resource required");
        var result = new TreeMap<String, NativeOption>();
        MakeUpQuadEligibility.nativeOptions().forEach((key, value) -> result.put(key,
            new NativeOption(Set.of("true", "false").contains(value) ? "BOOLEAN" : "STRING", Set.of(value), value, value)));
        result.put(KEY, binary("0")); result.put("SHADERLOD_REFINEMENT_ADAPTER", binary("1"));
        return result;
    }
    @Test void exactNative100AndOnlyDeclaredModKeysMayNormalize() {
        var actual = options(); assertTrue(MakeUpDeadCopyAdmission.eligible(VALID, actual));
        actual.put(KEY, binary("1")); assertTrue(MakeUpDeadCopyAdmission.eligible(VALID, actual));
        for (var key : MakeUpQuadEligibility.nativeOptions().keySet()) {
            var missing = new TreeMap<>(actual); missing.remove(key);
            assertFalse(MakeUpDeadCopyAdmission.options(missing), "Missing native key " + key);
            var changed = new TreeMap<>(actual); changed.put(key, new NativeOption("STRING", Set.of("changed"), "changed", "changed"));
            assertFalse(MakeUpDeadCopyAdmission.options(changed), "Changed native key " + key);
        }
        actual.put("SHADERLOD_UNKNOWN_OPTION", binary("0")); assertFalse(MakeUpDeadCopyAdmission.options(actual));
    }
    @Test void unknownPlatformApiSourceDimensionAndDhAreRefused() {
        for (var denied : new MakeUpDeadCopyAdmission.Context[] {
            null, context("1.21.4", "neoforge", "1.8.12", true, true, "minecraft:overworld", false, false),
            context("1.21.1", "fabric", "1.8.12", true, true, "minecraft:overworld", false, false),
            context("1.21.1", "neoforge", "1.8.8", true, true, "minecraft:overworld", false, false),
            context("1.21.1", "neoforge", "1.8.12", false, true, "minecraft:overworld", false, false),
            context("1.21.1", "neoforge", "1.8.12", true, false, "minecraft:overworld", false, false),
            context("1.21.1", "neoforge", "1.8.12", true, true, "minecraft:the_nether", false, false),
            context("1.21.1", "neoforge", "1.8.12", true, true, "minecraft:overworld", true, false),
            context("1.21.1", "neoforge", "1.8.12", true, true, "minecraft:overworld", false, true)
        }) assertFalse(MakeUpDeadCopyAdmission.eligible(denied, options()));
    }
    @Test void malformedDeclarationsAndPeerGraphsCannotAuthorizeCopyRemoval() {
        for (var bad : new NativeOption[] {
            new NativeOption("BOOLEAN", Set.of("false", "true"), "false", "true"),
            new NativeOption("STRING", Set.of("0", "1"), "1", "1"),
            new NativeOption("STRING", Set.of("0", "1", "2"), "0", "0"), binary("2")
        }) { var actual = options(); actual.put(KEY, bad); assertFalse(MakeUpDeadCopyAdmission.options(actual)); }
        for (var key : Set.of(CompileOptionControl.MAKEUP_AO_HALF, CompileOptionControl.MAKEUP_DEPTH,
                CompileOptionControl.MAKEUP_EARLY_ALPHA, CompileOptionControl.MAKEUP_SPATIAL_REUSE,
                CompileOptionControl.MAKEUP_SPATIAL_RATE, CompileOptionControl.MAKEUP_WORLD_PIXEL)) {
            var actual = options(); actual.put(key, binary("0")); assertTrue(MakeUpDeadCopyAdmission.options(actual));
            actual.put(key, binary("1")); assertFalse(MakeUpDeadCopyAdmission.options(actual));
        }
    }
    private static String plan(boolean master, LodPreset preset, OptimizationSnapshot flags, String dimension,
            boolean dh, boolean voxy, boolean admitted) {
        return CompileOptionControl.plan(CompileOptionControl.Family.MAKEUP,
            new CoreUniformFrame.Core(new LodSettings(master, 32, 96, 8, false), preset), flags,
            Set.of(KEY), options(), dimension, dh, voxy,
            admitted ? Map.of(OptimizationId.DEAD_FRAME_COPY, true) : Map.of()).get(KEY);
    }
    @Test void ordinaryPlanNeedsRequestAdmissionPerformanceAndCurrentWorld() {
        var flags = OptimizationSnapshot.noneRequested().with(OptimizationId.DEAD_FRAME_COPY, true);
        assertEquals("1", plan(true, LodPreset.PERFORMANCE, flags, "minecraft:overworld", false, false, true));
        assertEquals("0", plan(true, LodPreset.PERFORMANCE, flags, "minecraft:overworld", false, false, false));
        assertEquals("0", plan(false, LodPreset.PERFORMANCE, flags, "minecraft:overworld", false, false, true));
        assertEquals("0", plan(true, LodPreset.QUALITY, flags, "minecraft:overworld", false, false, true));
        assertEquals("0", plan(true, LodPreset.PERFORMANCE, OptimizationSnapshot.noneRequested(), "minecraft:overworld", false, false, true));
        for (var dimension : Set.of("minecraft:the_nether", "minecraft:the_end", "unknown"))
            assertEquals("0", plan(true, LodPreset.PERFORMANCE, flags, dimension, false, false, true));
        assertEquals("0", plan(true, LodPreset.PERFORMANCE, flags, "minecraft:overworld", true, false, true));
        assertEquals("0", plan(true, LodPreset.PERFORMANCE, flags, "minecraft:overworld", false, true, true));
        for (var peer : Set.of(OptimizationId.AO_RENDER_SCALE, OptimizationId.DEPTH_PRIMING, OptimizationId.EARLY_ALPHA,
                OptimizationId.SPATIAL_LIGHTING_REUSE, OptimizationId.SPATIAL_LIGHTING_RATE, OptimizationId.PIXEL_SCALE))
            assertEquals("0", plan(true, LodPreset.PERFORMANCE, flags.with(peer, true), "minecraft:overworld", false, false, true));
    }
    @Test void stableDefaultOffCompileControl() {
        assertEquals(OptimizationId.DEAD_FRAME_COPY, OptimizationId.fromKey("dead_frame_copy"));
        assertFalse(OptimizationId.DEAD_FRAME_COPY.defaultEnabled());
        assertFalse(OptimizationSnapshot.defaults().enabled(OptimizationId.DEAD_FRAME_COPY));
        assertEquals(Set.of(KEY), CompileOptionControl.controlledOptions(OptimizationId.DEAD_FRAME_COPY));
        assertTrue(CompileOptionControl.declaredContract(KEY, binary("0")));
        assertFalse(OptimizationEditTransaction.liveUniformIds().contains(OptimizationId.DEAD_FRAME_COPY));
        assertFalse(OptimizationEditTransaction.cpuOnlyRequests(OptimizationSnapshot.noneRequested()
            .with(OptimizationId.DEAD_FRAME_COPY, true)));
    }
}
