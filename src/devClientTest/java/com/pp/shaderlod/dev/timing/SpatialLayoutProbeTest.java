package com.pp.shaderlod.dev.timing;

import com.pp.shaderlod.ShaderLodEffectConfig;
import com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin;
import com.pp.shaderlod.integration.AdapterRuntimeBinding;
import com.pp.shaderlod.integration.PhotonSpatialReferenceAdapter;
import com.pp.shaderlod.integration.SourceBinding;
import com.pp.shaderlod.integration.SourceFingerprint;
import com.pp.shaderlod.lod.DistanceBands;
import com.pp.shaderlod.lod.EffectPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Standalone no-GL integration guards; actual mixin application and driver behavior remain runtime checks. */
public final class SpatialLayoutProbeTest {
    private static int assertions;
    public static void main(String[] args) {
        System.clearProperty(SpatialLayoutEvidence.PROPERTY);
        SpatialLayoutProbe.initialize();
        SpatialLayoutProbe.compiledProgram(null, null, null, null, null, null);
        SpatialLayoutProbe.enterRenderer(null, null, null, null);
        SpatialLayoutProbe.frameUpdated(null);
        SpatialLayoutProbe.beforeQuadDraw(); SpatialLayoutProbe.afterQuadDraw(); SpatialLayoutProbe.endCapture();
        check(SpatialLayoutProbe.report().get("status").getAsString().equals("UNKNOWN_CAPTURE_NOT_STARTED"));
        gates(); mixinGates(); sources(); roles(); typedPolicy();
        System.out.println("SpatialLayoutProbeTest: " + assertions + " assertions passed; no native context, build or client launched");
    }
    private static void gates() {
        var off = new Properties(); off.setProperty("shaderlod.dev.benchmark", "true");
        SpatialLayoutProbe.requireDiagnosticProperties(off);
        var valid = properties(); SpatialLayoutProbe.requireDiagnosticProperties(valid); check(true);
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming", "benchmark", "benchmarkOnly")) {
            var p = properties(); p.setProperty("shaderlod.dev." + key, "true"); rejects(() -> SpatialLayoutProbe.requireDiagnosticProperties(p));
        }
        for (String key : List.of("spatialLayoutOnly", "measureWorkload", "compare", "cpuLayerTiming", "gpuTiming", "cpuTiming")) {
            var p = properties(); p.remove("shaderlod.dev." + key); rejects(() -> SpatialLayoutProbe.requireDiagnosticProperties(p));
        }
    }
    private static Properties properties() {
        var p = new Properties();
        for (String key : List.of("spatialLayoutProbe", "spatialLayoutOnly", "measureWorkload")) p.setProperty("shaderlod.dev." + key, "true");
        p.setProperty("shaderlod.dev.compare", "on");
        for (String key : List.of("gpuTiming", "cpuTiming", "cpuLayerTiming")) p.setProperty("shaderlod.dev." + key, "false");
        return p;
    }
    private static void mixinGates() {
        var plugin = new DevEvidenceMixinPlugin();
        for (String name : List.of("SpatialPassAccessor", "SpatialCompositeMixin", "SpatialQuadMixin", "SpatialFrameMixin")) {
            String qualified = "com.pp.shaderlod.dev.mixin." + name;
            check(!plugin.shouldApplyMixin("irrelevant", qualified));
            System.setProperty(SpatialLayoutEvidence.PROPERTY, "true");
            check(plugin.shouldApplyMixin("irrelevant", qualified));
            System.clearProperty(SpatialLayoutEvidence.PROPERTY);
            check(!plugin.shouldApplyMixin("irrelevant", qualified));
        }
    }
    private static Map<String, String> acceptedOptions(SourceBinding b) {
        var options = new HashMap<String, String>(); b.requiredOptions().forEach((k, v) -> options.put(k, v.iterator().next()));
        options.put("UNRELATED_LOADED_OPTION", "preserved"); return options;
    }
    private static void sources() {
        var bindings = PhotonSpatialReferenceAdapter.sourceBindings();
        check(bindings.size() == 2);
        check(bindings.get(0).sourceSha256().equals("a8c004f5be0b341ed117220da0d2e2a0dadeefd62fd899ff64a3100c19a54ceb"));
        check(bindings.get(1).sourceSha256().equals("c2790b61bd4d096e24eedbf9ca800d35541ca81e8ec5efeecec122b764dbf6f0"));
        for (var binding : bindings) {
            var options = acceptedOptions(binding);
            var fp = new SourceFingerprint(binding.sourceSha256(), binding.memberSha256());
            check(SpatialLayoutProbe.selectBinding(fp, options).equals(binding));
            rejects(() -> SpatialLayoutProbe.selectBinding(new SourceFingerprint("f".repeat(64), binding.memberSha256()), options));
            for (String member : binding.memberSha256().keySet()) {
                var mutated = new HashMap<>(binding.memberSha256()); mutated.put(member, "f".repeat(64));
                rejects(() -> SpatialLayoutProbe.selectBinding(new SourceFingerprint(binding.sourceSha256(), mutated), options));
            }
            for (String option : binding.requiredOptions().keySet()) {
                var missing = new HashMap<>(options); missing.remove(option);
                rejects(() -> SpatialLayoutProbe.selectBinding(fp, missing));
                var changed = new HashMap<>(options); changed.put(option, "unsupported");
                rejects(() -> SpatialLayoutProbe.selectBinding(fp, changed));
            }
            var reordered = new java.util.LinkedHashMap<String, String>();
            options.entrySet().stream().sorted(Map.Entry.<String, String>comparingByKey().reversed()).forEach(e -> reordered.put(e.getKey(), e.getValue()));
            check(SpatialLayoutProbe.digestOptions(options).equals(SpatialLayoutProbe.digestOptions(reordered)));
            reordered.put("UNRELATED_LOADED_OPTION", "changed");
            check(!SpatialLayoutProbe.digestOptions(options).equals(SpatialLayoutProbe.digestOptions(reordered)));
        }
        check(SpatialLayoutProbe.available(new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE,
                PhotonSpatialReferenceAdapter.descriptor(), Set.of(PhotonSpatialReferenceAdapter.OPAQUE_SPATIAL_IRRADIANCE))));
        for (var reason : AdapterRuntimeBinding.Reason.values()) if (reason != AdapterRuntimeBinding.Reason.AVAILABLE)
            check(!SpatialLayoutProbe.available(AdapterRuntimeBinding.Snapshot.unavailable(reason)));
        check(!SpatialLayoutProbe.available(new AdapterRuntimeBinding.Snapshot(AdapterRuntimeBinding.Reason.AVAILABLE,
                PhotonSpatialReferenceAdapter.descriptor(), Set.of())));
    }
    private static void roles() {
        var binding = PhotonSpatialReferenceAdapter.sourceBindings().get(1);
        var contract = SpatialLayoutProbe.contract(binding, "a".repeat(64), 2976, 1672);
        check(contract.producer().outputs().get(1).logicalTarget() == 2);
        check(contract.producer().outputs().get(1).attachment() == 1);
        check(contract.producer().outputs().get(1).internalFormat() == GL30.GL_RGBA32F);
        check(contract.producer().outputs().get(0).internalFormat() == GL30.GL_R11F_G11F_B10F);
        check(contract.consumer().outputs().get(0).name().equals("fragment_color"));
        check(contract.consumer().outputs().get(1).name().equals("colortex3_clear"));
        check(contract.consumer().outputs().get(1).internalFormat() == GL11.GL_RGBA8);
        check(contract.consumer().inputs().stream().map(SpatialLayoutEvidence.Input::name).toList().equals(List.of("colortex0", "colortex2", "colortex3")));
        check(contract.tiles().size() == 22);
        check(contract.codec().maximumDistanceBlocks() == 16777216 && contract.codec().maximumEpoch() == 16777216);
        check(contract.maxRecords() == 16);
        for (int[] extent : new int[][]{{2,2}, {2975,1671}, {8192,8192}}) {
            var c = SpatialLayoutProbe.contract(binding, "a".repeat(64), extent[0], extent[1]);
            check(c.tiles().size() <= 32 && Set.copyOf(c.tiles()).size() == c.tiles().size());
            for (var tile : c.tiles()) check(tile.x() >= 0 && tile.y() >= 0 && tile.x()*2+1 < c.width() && tile.y()*2+1 < c.height());
        }
        check(SpatialLayoutProbe.stage("deferred4") == SpatialLayoutEvidence.Stage.PRODUCER);
        check(SpatialLayoutProbe.stage("deferred5") == SpatialLayoutEvidence.Stage.CONSUMER);
        for (String name : List.of("composite4", "deferred", "deferred04", "deferred4.fsh")) check(SpatialLayoutProbe.stage(name) == null);
        rejects(() -> SpatialLayoutProbe.contract(binding, "a".repeat(64), 8193, 1672));
    }
    private static void typedPolicy() {
        var policy = new EffectPolicy(true, false, new DistanceBands(32, 96, 8), 1, 0.25);
        for (var mode : List.of(ShaderLodEffectConfig.Mode.FULL, ShaderLodEffectConfig.Mode.ADAPTIVE)) {
            var control = new ShaderLodEffectConfig.Control(mode, policy);
            var valid = new SpatialLayoutProbe.TypedPolicy(2,1,2,1,1,mode.protocolValue(),32f,96f,8f,1f,0.25f,123);
            check(SpatialLayoutProbe.typedMatches(valid, control));
            check(!SpatialLayoutProbe.typedMatches(new SpatialLayoutProbe.TypedPolicy(2,1,2,1,0,mode.protocolValue(),32f,96f,8f,1f,0.25f,123), control));
            check(!SpatialLayoutProbe.typedMatches(new SpatialLayoutProbe.TypedPolicy(null,1,2,1,1,mode.protocolValue(),32f,96f,8f,1f,0.25f,123), control));
            check(!SpatialLayoutProbe.typedMatches(new SpatialLayoutProbe.TypedPolicy(2,1,2,1,1,mode.protocolValue(),32f,96f,8f,1f,Float.NaN,123), control));
            check(!SpatialLayoutProbe.typedMatches(new SpatialLayoutProbe.TypedPolicy(2,1,2,1,1,mode.protocolValue(),32f,96f,8f,1f,0.5f,123), control));
            check(!SpatialLayoutProbe.typedMatches(valid, null));
        }
    }
    private static void rejects(Runnable r) { try { r.run(); } catch (IllegalArgumentException expected) { assertions++; return; } throw new AssertionError("Expected rejection"); }
    private static void check(boolean value) { assertions++; if (!value) throw new AssertionError("Assertion " + assertions); }
}
