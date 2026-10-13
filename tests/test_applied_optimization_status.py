"""Offline tests for the exact-preimage NF26.2 settings candidate, using actual Java APIs.

No Minecraft/Gradle/GPU launch. Iris/NeoForge/Minecraft JARs are compile/link inputs only.
Runtime certificate booleans and world identities in the tests are explicit simulations.
"""
from __future__ import annotations
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('applied_preparation', ROOT/'scripts/prepare_applied_optimization_status.py')
prep=importlib.util.module_from_spec(spec)
spec.loader.exec_module(prep)

JAVA_TESTS = r'''
    private com.pp.shaderlod.packs.PackSelectionService.AppliedStatus describe(String name,
            Map<String,String> options, boolean enabled, LodPreset preset, boolean renderer,
            CoreUniformFrame.Core bridge) {
        var source = com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary, name);
        return com.pp.shaderlod.packs.PackSelectionService.appliedStatus(source, options,
                true, false, enabled, preset, renderer, bridge);
    }
    private CoreUniformFrame.Core bridge(boolean enabled, LodPreset preset) {
        return new CoreUniformFrame.Core(new LodSettings(enabled,32,96,8,false),preset);
    }
    private Backend reimagined(boolean available) throws IOException {
        Files.copy(Path.of("dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"),
                temporary.resolve("Reimagined.zip"));
        var backend = new Backend(); backend.selected="Reimagined.zip";
        backend.nativeAvailable=available; return backend;
    }
    @Test void authenticPerformanceBalancedQualityOffRoutesDescribeAppliedSelection() throws Exception {
        var backend=reimagined(true); var before=snapshot();
        assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertEquals("Reimagined.zip",backend.selected);
        var status=describe(backend.selected,Map.of(),true,LodPreset.PERFORMANCE,true,null);
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.RENDERER,status.optimization());
        assertTrue(status.detail().contains("native shader"));
        var after=snapshot(); assertEquals(before.keySet(),after.keySet());
        before.forEach((name,bytes)->assertArrayEquals(bytes,after.get(name),name));
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertNotEquals("Reimagined.zip",backend.selected);
        var source=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,backend.selected);
        assertEquals("complementary-reimagined",source.adapterId()); assertTrue(source.shaderGraph());
        var options=Map.of(source.budgetOption(),"1");
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                describe(backend.selected,options,true,LodPreset.BALANCED,false,null).optimization());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                describe(backend.selected,options,true,LodPreset.BALANCED,false,bridge(true,LodPreset.BALANCED)).optimization());
        assertTrue(ShaderLodSettings.apply(true,LodPreset.QUALITY,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertEquals("Reimagined.zip",backend.selected);
        assertEquals("Original shader active (Quality)",
                describe(backend.selected,Map.of(),true,LodPreset.QUALITY,false,null).detail());
        assertTrue(ShaderLodSettings.apply(false,LodPreset.QUALITY,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertEquals("Original shader active (Off)",
                describe(backend.selected,Map.of(),false,LodPreset.QUALITY,false,null).detail());
    }
    @Test void unavailableRendererGeneratesShaderRouteAndOriginalFamilyIsNotShaderProof() throws Exception {
        var backend=reimagined(false);
        assertEquals("Original shader active (renderer unavailable)",
                describe(backend.selected,Map.of(),true,LodPreset.PERFORMANCE,false,null).detail());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                describe(backend.selected,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1"),
                        true,LodPreset.BALANCED,true,null).optimization());
        assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        var source=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,backend.selected);
        assertTrue(source.shaderGraph());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                describe(backend.selected,Map.of(source.budgetOption(),"1"),true,LodPreset.PERFORMANCE,
                        false,bridge(true,LodPreset.PERFORMANCE)).optimization());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.INACTIVE,
                describe(backend.selected,Map.of(source.budgetOption(),"0"),true,LodPreset.PERFORMANCE,
                        false,bridge(true,LodPreset.PERFORMANCE)).optimization());
    }
    @Test void configAndTransportDisagreementNeverLabelsPendingChangesApplied() throws Exception {
        var backend=reimagined(false);
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        var source=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,backend.selected);
        var options=Map.of(source.budgetOption(),"1");
        for(var committed:java.util.List.of(bridge(false,LodPreset.BALANCED),bridge(true,LodPreset.QUALITY)))
            assertEquals("Shader optimization awaiting frame update", describe(backend.selected,options,
                    committed.settings().enabled(),committed.preset(),false,bridge(true,LodPreset.BALANCED)).detail());
        assertEquals("Shader optimization awaiting frame update",describe(backend.selected,options,
                true,LodPreset.BALANCED,false,bridge(false,LodPreset.BALANCED)).detail());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.INACTIVE,
                describe(backend.selected,options,false,LodPreset.BALANCED,false,bridge(false,LodPreset.BALANCED)).optimization());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.INACTIVE,
                describe(backend.selected,options,true,LodPreset.QUALITY,false,bridge(true,LodPreset.QUALITY)).optimization());
    }
    @Test void notifierReplacementUnlinkedNoWorldAndWorldChangeFailClosed() {
        var world=new java.util.concurrent.atomic.AtomicReference<Object>(new Object());
        var core=new java.util.concurrent.atomic.AtomicReference<>(bridge(true,LodPreset.BALANCED));
        var counter=new AtomicInteger(1);
        var notifier=new net.irisshaders.iris.uniforms.FrameUpdateNotifier();
        var state=CoreUniformFrame.forNotifier(notifier,notifier::addListener,core::get,world::get,counter::get);
        assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        var first=state.bridgeObservation();first.version();first.enabled();first.preset();
        assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        notifier.onNewFrame();assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        first.version();first.enabled();first.preset();
        assertEquals(core.get(),CoreUniformFrame.appliedBridge(notifier).orElseThrow());
        var replacement=new net.irisshaders.iris.uniforms.FrameUpdateNotifier();
        var next=CoreUniformFrame.forNotifier(replacement,replacement::addListener,core::get,world::get,counter::get);
        replacement.onNewFrame(); assertTrue(CoreUniformFrame.appliedBridge(replacement).isEmpty());
        var second=next.bridgeObservation();second.version();second.enabled();second.preset();
        assertEquals(core.get(),CoreUniformFrame.appliedBridge(replacement).orElseThrow());
        world.set(new Object()); assertTrue(CoreUniformFrame.appliedBridge(replacement).isEmpty());
        counter.incrementAndGet(); replacement.onNewFrame();
        assertTrue(CoreUniformFrame.appliedBridge(replacement).isEmpty());
        second.version();second.enabled();second.preset();
        assertTrue(CoreUniformFrame.appliedBridge(replacement).isPresent());
        world.set(null); counter.incrementAndGet(); replacement.onNewFrame();
        assertTrue(CoreUniformFrame.appliedBridge(replacement).isEmpty());
    }
    @Test void sourceInspectionFallbackStaleSelectionAndUnknownAreExplicit() throws Exception {
        var backend=reimagined(false);
        var before=snapshot();
        var source=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,backend.selected);
        var after=snapshot();assertEquals(before.keySet(),after.keySet());
        before.forEach((name,bytes)->assertArrayEquals(bytes,after.get(name),name));
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.FAILED,
                com.pp.shaderlod.packs.PackSelectionService.appliedStatus(source,Map.of(),true,true,
                        true,LodPreset.PERFORMANCE,true,null).optimization());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                com.pp.shaderlod.packs.PackSelectionService.appliedStatus(source,Map.of(),false,false,
                        true,LodPreset.PERFORMANCE,true,null).optimization());
        assertNotNull(com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,"../outside.zip").failure());
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        var generated=temporary.resolve(backend.selected);
        Files.writeString(generated,"tampered",java.nio.file.StandardOpenOption.APPEND);
        assertNotNull(com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,backend.selected).failure());
    }

    @Test void differentProgramsOrFramesNeverCombineConsumptionEvidence() {
        var world=new Object();var counter=new AtomicInteger(1);
        var notifier=new net.irisshaders.iris.uniforms.FrameUpdateNotifier();
        var state=CoreUniformFrame.forNotifier(notifier,notifier::addListener,
                ()->bridge(true,LodPreset.BALANCED),()->world,counter::get);
        var a=state.bridgeObservation();var b=state.bridgeObservation();
        notifier.onNewFrame();
        a.version();a.enabled();b.preset();
        assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        counter.incrementAndGet();notifier.onNewFrame();a.preset();
        assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        a.version();a.enabled();assertTrue(CoreUniformFrame.appliedBridge(notifier).isPresent());
        counter.incrementAndGet();notifier.onNewFrame();
        assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        b.enabled();b.preset();assertTrue(CoreUniformFrame.appliedBridge(notifier).isEmpty());
        b.version();assertTrue(CoreUniformFrame.appliedBridge(notifier).isPresent());
    }
    @Test void missingBridgeOffQualityUnknownAndDiskReplacementStayUnverified() throws Exception {
        var backend=reimagined(false);
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        String generated=backend.selected;
        var source=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,generated);
        var options=Map.of(source.budgetOption(),"1");
        for(var config:java.util.List.of(bridge(false,LodPreset.BALANCED),bridge(true,LodPreset.QUALITY)))
            assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                    describe(generated,options,config.settings().enabled(),config.preset(),false,null).optimization());
        var unknown=new com.pp.shaderlod.packs.PackSelectionService.Selection(null,false,null,null);
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                com.pp.shaderlod.packs.PackSelectionService.appliedStatus(unknown,Map.of(),true,false,
                        false,LodPreset.QUALITY,false,null).optimization());
        Files.copy(temporary.resolve("Reimagined.zip"),temporary.resolve(generated),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertFalse(com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,generated).shaderGraph());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                describe(generated,options,true,LodPreset.BALANCED,false,bridge(true,LodPreset.BALANCED)).optimization());
    }

    @Test void screenSelectionDetailKeepsStableEmptyAndFallbackButReportsActualDivergence() {
        String empty="No shader pipeline active";
        assertEquals(empty,ShaderLodSettingsScreen.loadedSelectionDetail(empty,false,false,false,false));
        String changed="Loaded shader changed; reopen settings";
        assertEquals(changed,ShaderLodSettingsScreen.loadedSelectionDetail(empty,false,true,false,false));
        assertEquals(changed,ShaderLodSettingsScreen.loadedSelectionDetail(empty,false,false,true,false));
        assertEquals(changed,ShaderLodSettingsScreen.loadedSelectionDetail(empty,false,true,true,false));
        assertEquals(empty,ShaderLodSettingsScreen.loadedSelectionDetail(empty,true,true,true,false));
        String fallback="Shader reload failed; fallback active";
        assertEquals(fallback,ShaderLodSettingsScreen.loadedSelectionDetail(fallback,false,true,false,true));
        assertEquals(fallback,ShaderLodSettingsScreen.loadedSelectionDetail(fallback,false,false,false,true));
    }
    @Test void nineAuthenticOriginalAndGeneratedFamiliesInspectUsingRealRecipes() throws Exception {
        var originals=Map.ofEntries(
            Map.entry("photon","dev/upstream/photon_v1.3b.zip"),
            Map.entry("complementary-reimagined","dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"),
            Map.entry("complementary-unbound","dev/upstream/pack-expansion/ComplementaryUnbound_r5.9.3.zip"),
            Map.entry("makeup-ultra-fast-shaders","dev/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),
            Map.entry("bsl-shaders","dev/upstream/other-pack-research/BSL_v10.1.8.zip"),
            Map.entry("solas-shader","dev/upstream/pack-expansion/Solas Shader V3.7b.zip"),
            Map.entry("astralex","dev/upstream/pack-expansion/§r§lAstra§4§lLex§r§l_By_LexBoosT_§4§lV93.0§r§l.zip"),
            Map.entry("bliss-shader","dev/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"),
            Map.entry("sildurs-vibrant-shaders","dev/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip"));
        for(var fixture:originals.entrySet()) {
            String original=fixture.getKey()+".zip";
            Files.copy(Path.of(fixture.getValue()),temporary.resolve(original));
            var before=snapshot();
            var nativeSource=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,original);
            assertNull(nativeSource.failure(),fixture.getKey());assertEquals(fixture.getKey(),nativeSource.adapterId());
            assertFalse(nativeSource.shaderGraph());assertNotNull(nativeSource.budgetOption());
            var after=snapshot();assertEquals(before.keySet(),after.keySet());
            before.forEach((name,bytes)->assertArrayEquals(bytes,after.get(name),name));
            var service=new com.pp.shaderlod.packs.PackSelectionService();
            var generated=service.apply(temporary,original,true,LodPreset.BALANCED,Map.of(),false);
            assertTrue(generated.accepted(),fixture.getKey()+": "+generated.detail());
            var selection=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,generated.packFile());
            assertNull(selection.failure(),fixture.getKey());assertTrue(selection.shaderGraph(),fixture.getKey());
            assertEquals(fixture.getKey(),selection.adapterId());
            var loadedOptions=new HashMap<String,String>();
            var properties=new java.util.Properties();
            try(var input=Files.newInputStream(temporary.resolve(generated.packFile()+".txt"))) {properties.load(input);}
            properties.forEach((key,value)->loadedOptions.put(key.toString(),value.toString()));
            if(fixture.getKey().equals("photon"))loadedOptions.put("TAA","true");
            assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                    describe(generated.packFile(),loadedOptions,true,LodPreset.BALANCED,false,
                            bridge(true,LodPreset.BALANCED)).optimization(),fixture.getKey());
            assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                    describe(original,loadedOptions,false,LodPreset.QUALITY,false,null).optimization(),fixture.getKey());
            var off=service.apply(temporary,generated.packFile(),false,LodPreset.BALANCED,Map.of(),false);
            assertTrue(off.accepted(),fixture.getKey()+": "+off.detail());assertEquals(original,off.packFile());
        }
    }
    @Test void photonAANativeFallbackDoesNotClaimPixelsSaved() throws Exception {
        var backend=fixture();
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        var on=Map.of("SHADERLOD_LIVE_PIXEL_BUDGET","true","TAA","true","TAAU","true");
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                describe(backend.selected,on,true,LodPreset.BALANCED,false,bridge(true,LodPreset.BALANCED)).optimization());
        var aaOff=new HashMap<>(on);aaOff.put("TAA","false");
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.INACTIVE,
                describe(backend.selected,aaOff,true,LodPreset.BALANCED,false,bridge(true,LodPreset.BALANCED)).optimization());
    }
    @Test void defaultPublicApplyCapturesAvailabilityBeforeSuspensionAndKeepsPerformanceNative() throws Exception {
        var backend=reimagined(true);
        var oldAvailability=ShaderLodSettings.rendererAvailability;
        var oldNativeFactory=ShaderLodSettings.nativeSelectionFactory;
        var oldReload=ShaderLodSettings.applyReload;var oldFallback=ShaderLodSettings.applyFallback;
        var captures=new AtomicInteger();
        try {
            ShaderLodSettings.rendererAvailability=()->{
                assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                captures.incrementAndGet();return true;
            };
            ShaderLodSettings.nativeSelectionFactory=available->{
                assertTrue(available);assertTrue(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
                return new PackSelectionTransaction(backend);
            };
            ShaderLodSettings.applyReload=()->{};ShaderLodSettings.applyFallback=()->false;
            assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE));
            assertEquals(1,captures.get());assertEquals("Reimagined.zip",backend.selected);
            assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());
            assertTrue(ShaderLodSettings.lastApplyFailure().isEmpty());
        } finally {ShaderLodSettings.rendererAvailability=oldAvailability;
            ShaderLodSettings.nativeSelectionFactory=oldNativeFactory;
            ShaderLodSettings.applyReload=oldReload;ShaderLodSettings.applyFallback=oldFallback;}
    }
    @Test void refusalFailureRecoveryAndNextSuccessHaveDistinctPersistentFeedback() throws Exception {
        var backend=fixture();
        ShaderLodSettings.applyPreflight=()->com.pp.shaderlod.integration.RendererBackend.requireApplySupportedIdentity("Vulkan","com.mojang.blaze3d.vulkan.VulkanDevice");
        assertFalse(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->fail("No reload for refusal"),()->false));
        assertTrue(ShaderLodSettings.lastApplyFailure().startsWith("Apply refused:"));
        ShaderLodSettings.applyPreflight=()->{};
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertTrue(ShaderLodSettings.lastApplyFailure().isEmpty());
        String generated=backend.selected;var before=snapshot();var reloads=new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true,LodPreset.QUALITY,
                ()->new PackSelectionTransaction(backend),()->{
                    if(reloads.incrementAndGet()==1)throw new IOException("fixture reload failure");
                },()->false));
        assertTrue(ShaderLodSettings.lastApplyFailure().startsWith("Apply failed:"));
        assertEquals(generated,backend.selected);assertEquals(LodPreset.BALANCED,ShaderLodConfig.PRESET.get());
        var after=snapshot();assertEquals(before.keySet(),after.keySet());
        before.forEach((name,bytes)->assertArrayEquals(bytes,after.get(name),name));
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertTrue(ShaderLodSettings.lastApplyFailure().isEmpty());
    }
'''

LAUNCHER = r'''
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
public final class OfflineTestLauncher {
    public static void main(String[] args) {
        var request=LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass("com.pp.shaderlod.AppliedOptimizationSettingsTest"),
                DiscoverySelectors.selectClass("com.pp.shaderlod.CoreUniformFrameTest"),
                DiscoverySelectors.selectClass("com.pp.shaderlod.ApplyBackendRefusalTest")).build();
        var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);launcher.execute(request);
        var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));
        summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
        if(summary.getTestsFailedCount()>0 || summary.getTestsAbortedCount()>0 || summary.getTestsSkippedCount()>0
            || summary.getTestsSucceededCount()!=43) System.exit(1);
    }
}
'''


def run_java_consumers(output: Path) -> dict:
    cp=json.loads((ROOT/'dev/logs/concept-transfer-r1/reimagined-neoforge-main/checks-classpath.json').read_text())
    cp.append(str(ROOT/'ports/26.2/runtime/neoforge-libraries/net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar'))
    junit_groups=['org.junit.jupiter/junit-jupiter-api/5.14.1','org.junit.jupiter/junit-jupiter-engine/5.14.1',
        'org.junit.platform/junit-platform-launcher/1.14.1','org.junit.platform/junit-platform-engine/1.14.1',
        'org.junit.platform/junit-platform-commons/1.14.1','org.opentest4j/opentest4j',
        'org.apiguardian/apiguardian-api']
    cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
    for group in junit_groups:
        jars=sorted((cache/group).rglob('*.jar'))
        if not jars: raise RuntimeError(f'Missing local JUnit dependency: {group}')
        cp.extend(map(str,jars))
    classes=output/'classes';classes.mkdir()
    test_source=(ROOT/'ports/26.2/neoforge/src/test/java/com/pp/shaderlod/ShaderLodSettingsSelectionTest.java').read_text()
    test_source=test_source.replace('class ShaderLodSettingsSelectionTest {','class AppliedOptimizationSettingsTest {')
    test_source=test_source.replace('        String selected = "Photon.zip";','        String selected = "Photon.zip";\n        boolean nativeAvailable;\n        public boolean nativeRendererAvailable() { return nativeAvailable; }')
    test_file=output/'AppliedOptimizationSettingsTest.java';test_file.write_text(test_source.rstrip()[:-1]+JAVA_TESTS+'\n}\n')
    launcher_file=output/'OfflineTestLauncher.java';launcher_file.write_text(LAUNCHER)
    source=list((output/'source').rglob('*.java'))
    source += [ROOT/'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/ShaderLodConfig.java',
        ROOT/'src/main/java/com/pp/shaderlod/LodSettings.java', ROOT/'src/main/java/com/pp/shaderlod/LodPreset.java',
        ROOT/'src/main/java/com/pp/shaderlod/packs/SupportedPackCatalog.java',
        ROOT/'src/main/java/com/pp/shaderlod/integration/PackSelectionTransaction.java',
        ROOT/'src/test/java/com/pp/shaderlod/CoreUniformFrameTest.java',
        ROOT/'ports/26.2/neoforge/src/test/java/com/pp/shaderlod/port/ApplyBackendRefusalTest.java',test_file,launcher_file]
    jdk=ROOT/'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin'
    compile_cmd=[str(jdk/'javac'),'-J-XX:ActiveProcessorCount=2','-proc:none','-cp',os.pathsep.join(cp),'-d',str(classes),*map(str,source)]
    (output/'compile-command.json').write_text(json.dumps(compile_cmd,indent=2)+'\n')
    result=subprocess.run(compile_cmd,cwd=ROOT,capture_output=True,text=True,timeout=60)
    (output/'compile.log').write_text(result.stdout+result.stderr)
    if result.returncode: raise RuntimeError(result.stdout+result.stderr)
    run_cp=[str(classes),str(ROOT/'src/main/resources'),*cp]
    run_cmd=[str(jdk/'java'),'-XX:ActiveProcessorCount=2','-Djava.awt.headless=true','-cp',os.pathsep.join(run_cp),'OfflineTestLauncher']
    (output/'java-command.json').write_text(json.dumps(run_cmd,indent=2)+'\n')
    result=subprocess.run(run_cmd,cwd=ROOT,capture_output=True,text=True,timeout=60)
    (output/'java-tests.log').write_text(result.stdout+result.stderr)
    if result.returncode: raise RuntimeError(result.stdout+result.stderr)
    return {'javac_exit':0,'java_exit':0,'stub_classes':0,'output':str(output),'summary':result.stdout}


class AppliedOptimizationStatusTests(unittest.TestCase):
    def test_exact_preimages_prepare_and_retain_sealed_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for path in prep.PINS:
                target=root/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/path).read_bytes())
            before={p:(root/p).read_bytes() for p in prep.PINS}
            output=root/'dev/logs/offline-delivery-sprint-20261012/test-candidate'
            receipt=prep.prepare(output,root)
            self.assertEqual(receipt['preimages'],prep.PINS)
            self.assertIn('Renderer optimization applied', (output/'integration.patch').read_text())
            self.assertEqual(before,{p:(root/p).read_bytes() for p in prep.PINS})
            with self.assertRaisesRegex(ValueError,'existing output'): prep.prepare(output,root)

    def test_source_drift_refuses_before_receiver_exists(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            for path in prep.PINS:
                target=root/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/path).read_bytes())
            path=next(iter(prep.PINS));(root/path).write_bytes((root/path).read_bytes()+b'\n')
            output=root/'dev/logs/offline-delivery-sprint-20261012/refused'
            with self.assertRaisesRegex(ValueError,'preimage drift'):prep.prepare(output,root)
            self.assertFalse(output.exists())

    def test_output_boundary_and_anchor_ambiguity_refuse(self):
        with self.assertRaisesRegex(ValueError,'fresh child'):prep.prepare(ROOT/'src/main/java/no-output')
        with self.assertRaisesRegex(ValueError,'one exact'):prep.replace_once('x x','x','y')

    def test_real_java_consumers_and_applied_transitions(self):
        # A fresh retained receiver permits independent review of exact source/commands/raw output.
        base=ROOT/'dev/logs/offline-delivery-sprint-20261012/settings'
        base.mkdir(parents=True,exist_ok=True)
        parent=Path(tempfile.mkdtemp(prefix='verification-receiver-',dir=base))
        output=parent/'candidate'
        prep.prepare(output)
        result=run_java_consumers(output)
        (output/'verification.json').write_text(json.dumps(result,indent=2)+'\n')
        self.assertEqual(result['java_exit'],0)
        self.assertIn('tests successful',result['summary'])

if __name__=='__main__':unittest.main()
