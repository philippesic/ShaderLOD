"""Focused real-Java consumers with both reviewed offline delivery overlays combined.

This prepares disposable source/resources, never edits or deploys sealed product inputs.
Renderer certificates, frame/world identities and effective loaded options are simulations;
actual source/recipe admission, configuration, transactions and file recovery are exercised.
"""
from __future__ import annotations
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
from prepare_complementary_prior_weight import prepare as prepare_weight
from build_supported_pack_adapters import build, CATALOG, RESOURCE_ROOT

def module(name,path):
    spec=importlib.util.spec_from_file_location(name,path)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
settings=module('reviewed_settings_cpu',ROOT/'tests/test_applied_optimization_status.py')
BASE=ROOT/'dev/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip'
SETTINGS_PATCH_SHA='2e55f9da31ae28261d8ae6aa7efd9f1df9c0333e25f001b5ef3051a53c59e1c5'
CURRENT_RECIPE_SHA='96bb4ac0ae6f93e779f1343eb71b5dd2d48ebdce97cfcbcc2025034ca6b06a9e'
NEW_RECIPE_SHA='dbca3989af41a8eb2807c22af7a225799fe32ff42c11086fcc8f419b68129c55'
OLD_DIGEST='920212803e992b80ba58da1e00a54bc45b4ad5ddac1edae1b392002c90ee2cc7'
NEW_DIGEST='98b06ff48620bd6584cabbd6882fee945863f397b07296b76c5fb1e4dc3e8f60'

GENERATOR=r'''
import java.nio.file.Path;
import java.util.Map;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
public class AuthenticOldSelection {
 public static void main(String[] args) throws Exception {
  var result=new PackSelectionService().apply(Path.of(args[0]),"Reimagined.zip",true,
      LodPreset.BALANCED,com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions(),false);
  if(!result.accepted())throw new AssertionError(result.detail());
  var nativeOptions=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
  var generatedOptions=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.generatedOptions();
  if(nativeOptions.size()!=398 || generatedOptions.size()!=402)throw new AssertionError("Wrong actual pinned option maps");
  var certificate=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(Path.of(args[0]),
      result.packFile(),"Reimagined.zip",generatedOptions,false);
  if(!certificate.eligible())throw new AssertionError("Old certificate baseline: "+certificate.reason());
  var admission=new com.google.gson.JsonObject();admission.addProperty("nativeOptions",nativeOptions.size());
  admission.addProperty("generatedOptions",generatedOptions.size());admission.addProperty("eligible",certificate.eligible());
  admission.addProperty("reason",certificate.reason());
  java.nio.file.Files.writeString(Path.of(args[0]).resolve("baseline-admission.json"),admission.toString());
  System.out.println(result.packFile());
 }
}
'''

HISTORICAL_PROBE=r'''
import java.nio.file.*;
import java.util.Map;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate;
import com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility;
public class HistoricalCertificateProbe {
 public static void main(String[] args)throws Exception {
  var directory=Path.of(args[0]);var nativeOptions=ReimaginedQuadEligibility.nativeOptions();
  var generatedOptions=ReimaginedSourceCertificate.generatedOptions();
  if(nativeOptions.size()!=398||generatedOptions.size()!=402)throw new AssertionError("Option maps unavailable");
  var original=ReimaginedSourceCertificate.validate(directory,"Reimagined.zip","Reimagined.zip",nativeOptions,false);
  if(!original.eligible())throw new AssertionError(original.reason());
  var selection=new PackSelectionService().apply(directory,"Reimagined.zip",true,LodPreset.BALANCED,nativeOptions,false);
  if(!selection.accepted())throw new AssertionError(selection.detail());
  var refusal=ReimaginedSourceCertificate.validate(directory,selection.packFile(),"Reimagined.zip",generatedOptions,false);
  if(refusal.eligible()||!refusal.reason().contains("Stale/tampered packaged recipe"))throw new AssertionError(refusal.reason());
  var receipt=new com.google.gson.JsonObject();receipt.addProperty("generatedPack",selection.packFile());
  receipt.addProperty("nativeOptions",nativeOptions.size());receipt.addProperty("generatedOptions",generatedOptions.size());
  receipt.addProperty("nativeAccepted",original.eligible());receipt.addProperty("generatedAccepted",refusal.eligible());
  receipt.addProperty("reason",refusal.reason());receipt.addProperty("oldCertificateRecipe",ReimaginedSourceCertificate.RECIPE_SHA);
  Files.writeString(directory.resolve("historical-admission.json"),receipt.toString());
  System.out.println(refusal.reason());
 }
}
'''

JAVA_TESTS=r'''
    private byte[] member(Path path,String name)throws Exception {
        try(var archive=new java.util.zip.ZipFile(path.toFile())) {
            try(var input=archive.getInputStream(archive.getEntry(name))) {return input.readAllBytes();}
        }
    }
    private Map<String,String> loadedOptions(String name)throws Exception {
        var values=new java.util.Properties();
        try(var input=Files.newInputStream(temporary.resolve(name+".txt"))) {values.load(input);}
        var result=new HashMap<String,String>();values.forEach((key,value)->result.put(key.toString(),value.toString()));
        return result;
    }
    private void assertSnapshot(Map<String,byte[]> expected)throws Exception {
        var actual=snapshot();assertEquals(expected.keySet(),actual.keySet());
        expected.forEach((name,bytes)->assertArrayEquals(bytes,actual.get(name),name));
    }
    @Test void actualBalancedApplyConsumesNewRecipeAndDescribesShaderControlRoute()throws Exception {
        var backend=reimagined(false);
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        assertNotEquals("Reimagined.zip",backend.selected);
        String sourceDigest=com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(
                com.pp.shaderlod.integration.PackSourceReader.read(temporary.resolve(backend.selected)).memberSha256());
        assertEquals("98b06ff48620bd6584cabbd6882fee945863f397b07296b76c5fb1e4dc3e8f60",sourceDigest);
        var marker=com.google.gson.JsonParser.parseString(new String(member(temporary.resolve(backend.selected),
                "shaderlod-selection.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("dbca3989af41a8eb2807c22af7a225799fe32ff42c11086fcc8f419b68129c55",marker.get("recipeSha256").getAsString());
        var body=new String(member(temporary.resolve(backend.selected),
                "shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl"),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("if (shaderlodReuseVLWeight && i > 0) currentWeight = shaderlodPriorVLWeight;"));
        var notifier=new net.irisshaders.iris.uniforms.FrameUpdateNotifier();
        var state=CoreUniformFrame.forNotifier(notifier,notifier::addListener,
                ()->new CoreUniformFrame.Core(ShaderLodConfig.snapshot(),ShaderLodConfig.PRESET.get()),
                ()->this,()->1);
        var observer=state.bridgeObservation();notifier.onNewFrame();
        observer.version();observer.enabled();observer.preset();
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                describe(backend.selected,loadedOptions(backend.selected),true,LodPreset.BALANCED,false,
                        CoreUniformFrame.appliedBridge(notifier).orElseThrow()).optimization());
    }
    @Test void publicPerformanceCaptureSelectsNativeAndQualityOffRestoreExactNativeBytes()throws Exception {
        var backend=reimagined(true);
        String chosen="# preserved native quality\r\nCUSTOM_QUALITY=high\r\nINFO=2\r\n";
        Files.writeString(temporary.resolve("Reimagined.zip.txt"),chosen);
        byte[] original=Files.readAllBytes(temporary.resolve("Reimagined.zip"));
        byte[] sidecar=Files.readAllBytes(temporary.resolve("Reimagined.zip.txt"));
        var oldAvailability=ShaderLodSettings.rendererAvailability;var oldNative=ShaderLodSettings.nativeSelectionFactory;
        var oldReload=ShaderLodSettings.applyReload;var oldFallback=ShaderLodSettings.applyFallback;
        try {
            ShaderLodSettings.rendererAvailability=()->{
                assertFalse(com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended());return true;};
            ShaderLodSettings.nativeSelectionFactory=available->{assertTrue(available);return new PackSelectionTransaction(backend);};
            ShaderLodSettings.applyReload=()->{};ShaderLodSettings.applyFallback=()->false;
            assertTrue(ShaderLodSettings.apply(true,LodPreset.PERFORMANCE));
        } finally {ShaderLodSettings.rendererAvailability=oldAvailability;ShaderLodSettings.nativeSelectionFactory=oldNative;
            ShaderLodSettings.applyReload=oldReload;ShaderLodSettings.applyFallback=oldFallback;}
        assertEquals("Reimagined.zip",backend.selected);
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.RENDERER,
                describe(backend.selected,Map.of(),true,LodPreset.PERFORMANCE,true,null).optimization());
        for(boolean quality:java.util.List.of(true,false)) {
            assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                    ()->new PackSelectionTransaction(backend),()->{},()->false));
            assertNotEquals("Reimagined.zip",backend.selected);
            assertTrue(ShaderLodSettings.apply(quality,LodPreset.QUALITY,
                    ()->new PackSelectionTransaction(backend),()->{},()->false));
            assertEquals("Reimagined.zip",backend.selected);
            assertArrayEquals(original,Files.readAllBytes(temporary.resolve("Reimagined.zip")));
            assertArrayEquals(sidecar,Files.readAllBytes(temporary.resolve("Reimagined.zip.txt")));
            assertEquals("Original shader active ("+(quality?"Quality":"Off")+")",
                    describe(backend.selected,Map.of(),quality,LodPreset.QUALITY,false,null).detail());
        }
    }
    @Test void failedApplyPreservesNewRecipeSelectionConfigQueueAndExactFiles()throws Exception {
        var backend=reimagined(false);
        Files.writeString(temporary.resolve("Reimagined.zip.txt"),"# native choice\r\nINFO=2\r\n");
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        String generated=backend.selected;var before=snapshot();var core=ShaderLodConfig.snapshot();
        backend.queue.put("INFO","3");var reloads=new AtomicInteger();
        assertFalse(ShaderLodSettings.apply(true,LodPreset.QUALITY,
                ()->new PackSelectionTransaction(backend),()->{
                    if(reloads.incrementAndGet()==1)throw new IOException("combined candidate rejected reload");
                    assertEquals(generated,backend.selected);assertEquals(core,ShaderLodConfig.snapshot());
                },()->false));
        assertEquals(2,reloads.get());assertEquals(generated,backend.selected);
        assertEquals(core,ShaderLodConfig.snapshot());assertEquals(LodPreset.BALANCED,ShaderLodConfig.PRESET.get());
        assertEquals(Map.of("INFO","3"),backend.queue);assertSnapshot(before);
        assertTrue(ShaderLodSettings.lastApplyFailure().startsWith("Apply failed:"));
    }
    @Test void oldGeneratedStateRefusesNewRecipeBeforeReloadOrMutation()throws Exception {
        Path baseline=Path.of(System.getProperty("combined.oldSelection"));
        try(var paths=Files.list(baseline)) {
            for(var source:paths.toList())Files.copy(source,temporary.resolve(source.getFileName()));
        }
        var oldReceipt=com.google.gson.JsonParser.parseString(Files.readString(baseline.resolve("baseline-receipt.json"))).getAsJsonObject();
        var backend=new Backend();backend.selected=oldReceipt.get("generatedPack").getAsString();
        var marker=com.google.gson.JsonParser.parseString(new String(member(temporary.resolve(backend.selected),
                "shaderlod-selection.json"),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("96bb4ac0ae6f93e779f1343eb71b5dd2d48ebdce97cfcbcc2025034ca6b06a9e",marker.get("recipeSha256").getAsString());
        var before=snapshot();var core=ShaderLodConfig.snapshot();var preset=ShaderLodConfig.PRESET.get();
        String generated=backend.selected;backend.queue.put("INFO","3");
        assertFalse(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->fail("Stale recipe refusal must not reload"),()->false));
        assertEquals(generated,backend.selected);assertEquals(core,ShaderLodConfig.snapshot());
        assertEquals(preset,ShaderLodConfig.PRESET.get());assertEquals(Map.of("INFO","3"),backend.queue);assertSnapshot(before);
        assertTrue(ShaderLodSettings.lastApplyFailure().startsWith("Apply refused:"));
        assertTrue(ShaderLodSettings.lastApplyFailure().contains("Adapter recipe changed"));
        var oldCertificate=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,generated,"Reimagined.zip",
                com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.generatedOptions(),false);
        assertFalse(oldCertificate.eligible());
        assertTrue(oldCertificate.reason().contains("Source/state field mismatch: recipeSha256"),oldCertificate.reason());
        var selection=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,generated);
        assertNotNull(selection.failure());
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.UNAVAILABLE,
                describe(generated,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1"),true,LodPreset.BALANCED,false,
                        bridge(true,LodPreset.BALANCED)).optimization());
    }

    @Test void r2BindingCertificateAdmitsFreshNewRecipeAndRetainsHistoricalRefusal()throws Exception {
        var nativeOptions=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
        var generatedOptions=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.generatedOptions();
        assertEquals(398,nativeOptions.size());assertEquals(402,generatedOptions.size());
        assertEquals("dbca3989af41a8eb2807c22af7a225799fe32ff42c11086fcc8f419b68129c55",
                com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.RECIPE_SHA);
        var backend=reimagined(false);
        var properties=new java.util.Properties();properties.putAll(nativeOptions);
        try(var output=Files.newOutputStream(temporary.resolve("Reimagined.zip.txt"))) {properties.store(output,null);}
        var originalAdmission=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,"Reimagined.zip","Reimagined.zip",nativeOptions,false);
        assertTrue(originalAdmission.eligible(),originalAdmission.reason());
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        var before=snapshot();
        var generatedAdmission=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false);
        assertTrue(generatedAdmission.eligible(),generatedAdmission.reason());assertTrue(generatedAdmission.generated());
        assertSnapshot(before);
        System.out.println("Actual r2 NF26.2 source certificate: native398 and fresh candidate/generated402 admitted: "+generatedAdmission.reason());
        var historical=com.google.gson.JsonParser.parseString(Files.readString(Path.of(System.getProperty("combined.historical"))
                .resolve("historical-admission.json"))).getAsJsonObject();
        assertTrue(historical.get("nativeAccepted").getAsBoolean());
        assertFalse(historical.get("generatedAccepted").getAsBoolean());
        assertTrue(historical.get("reason").getAsString().contains("Stale/tampered packaged recipe"));
        Path baseline=Path.of(System.getProperty("combined.oldSelection"));
        var admission=com.google.gson.JsonParser.parseString(Files.readString(baseline.resolve("baseline-admission.json"))).getAsJsonObject();
        assertTrue(admission.get("eligible").getAsBoolean());
        assertEquals(398,admission.get("nativeOptions").getAsInt());assertEquals(402,admission.get("generatedOptions").getAsInt());
    }

    private void rewriteMember(Path archive,String changed,byte[] replacement)throws Exception {
        Path temporaryArchive=Files.createTempFile(temporary,"negative-source-",".zip");
        try(var input=new java.util.zip.ZipFile(archive.toFile());
                var output=new java.util.zip.ZipOutputStream(Files.newOutputStream(temporaryArchive))) {
            var entries=input.entries();while(entries.hasMoreElements()) {
                var entry=entries.nextElement();if(entry.isDirectory())continue;
                output.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                if(entry.getName().equals(changed))output.write(replacement);
                else try(var bytes=input.getInputStream(entry)) {bytes.transferTo(output);}
                output.closeEntry();
            }
        }
        Files.move(temporaryArchive,archive,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    @Test void r2CertificatePreservesInactiveNativeReturnAndTamperOptionSourceGuards()throws Exception {
        var nativeOptions=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
        var generatedOptions=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.generatedOptions();
        var backend=reimagined(false);
        var values=new java.util.Properties();values.putAll(nativeOptions);
        try(var output=Files.newOutputStream(temporary.resolve("Reimagined.zip.txt"))) {values.store(output,null);}
        assertTrue(ShaderLodSettings.apply(true,LodPreset.BALANCED,
                ()->new PackSelectionTransaction(backend),()->{},()->false));
        Path archive=temporary.resolve(backend.selected);byte[] goodArchive=Files.readAllBytes(archive);
        var marker=com.google.gson.JsonParser.parseString(new String(member(archive,"shaderlod-selection.json"),
                java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        Path state=temporary.resolve(marker.get("stateFile").getAsString());byte[] goodState=Files.readAllBytes(state);
        var inactive=com.google.gson.JsonParser.parseString(new String(goodState,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        inactive.addProperty("active",false);Files.writeString(state,inactive.toString());
        var refused=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false);
        assertFalse(refused.eligible());assertTrue(refused.reason().contains("Inactive generated state"),refused.reason());
        var nativeReturn=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,true);
        assertTrue(nativeReturn.eligible(),nativeReturn.reason());
        Files.write(state,goodState);
        String volume="shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl";
        var tampered=new String(member(archive,volume),java.nio.charset.StandardCharsets.UTF_8)+"\n// altered source\n";
        rewriteMember(archive,volume,tampered.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        refused=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false);
        assertFalse(refused.eligible());assertTrue(refused.reason().contains("Generated member differs"),refused.reason());
        Files.write(archive,goodArchive);
        var alteredState=com.google.gson.JsonParser.parseString(new String(goodState,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        alteredState.addProperty("recipeSha256","0".repeat(64));Files.writeString(state,alteredState.toString());
        refused=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false);
        assertFalse(refused.eligible());assertTrue(refused.reason().contains("Source/state field mismatch: recipeSha256"),refused.reason());
        Files.write(state,goodState);
        var wrongOptions=new HashMap<>(generatedOptions);wrongOptions.put("TAA","false");
        refused=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",wrongOptions,false);
        assertFalse(refused.eligible());assertTrue(refused.reason().contains("Generated exact402 options refused"),refused.reason());
        Path original=temporary.resolve("Reimagined.zip");byte[] goodOriginal=Files.readAllBytes(original);
        Files.writeString(original,"tampered original",java.nio.file.StandardOpenOption.APPEND);
        refused=com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false);
        assertFalse(refused.eligible());assertTrue(refused.reason().contains("Original native398/source/API resources refused"),refused.reason());
        Files.write(original,goodOriginal);
        assertTrue(com.pp.shaderlod.integration.bindingcompat.ReimaginedSourceCertificate.validate(
                temporary,backend.selected,"Reimagined.zip",generatedOptions,false).eligible());
    }
    @Test void legacyManualSourceRemainsRecognizedWithoutClaimingNewCarryRecipe()throws Exception {
        Path manual=Path.of(System.getProperty("combined.oldManual"));
        Files.copy(manual,temporary.resolve("LegacyManual.zip"));
        var source=com.pp.shaderlod.integration.PackSourceReader.read(temporary.resolve("LegacyManual.zip"));
        assertEquals("920212803e992b80ba58da1e00a54bc45b4ad5ddac1edae1b392002c90ee2cc7",
                com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(source.memberSha256()));
        var recognition=com.pp.shaderlod.packs.SupportedPackCatalog.recognize(source.memberSha256()).orElseThrow();
        assertEquals(com.pp.shaderlod.packs.SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER,recognition.stage());
        var descriptor=com.pp.shaderlod.packs.PackSelectionService.inspectSelection(temporary,"LegacyManual.zip");
        assertNull(descriptor.failure());assertTrue(descriptor.shaderGraph());
        assertEquals("complementary-reimagined",descriptor.adapterId());
        assertFalse(new String(member(temporary.resolve("LegacyManual.zip"),
                "shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl"),java.nio.charset.StandardCharsets.UTF_8)
                .contains("shaderlodPriorVLWeight"));
        assertEquals(com.pp.shaderlod.packs.PackSelectionService.Optimization.SHADER,
                describe("LegacyManual.zip",Map.of(descriptor.budgetOption(),"1"),true,LodPreset.BALANCED,false,
                        bridge(true,LodPreset.BALANCED)).optimization());
    }
'''

LAUNCHER=r'''
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.engine.discovery.DiscoverySelectors;
public class CombinedCpuLauncher {
 public static void main(String[] args) {
  var request=LauncherDiscoveryRequestBuilder.request().selectors(
      DiscoverySelectors.selectClass("com.pp.shaderlod.CombinedOfflineDeliveryTest")).build();
  var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);launcher.execute(request);
  var summary=listener.getSummary();summary.printTo(new java.io.PrintWriter(System.out,true));
  summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
  if(summary.getTestsSucceededCount()!=7 || summary.getTestsFailedCount()!=0 || summary.getTestsSkippedCount()!=0
       || summary.getTestsAbortedCount()!=0)System.exit(1);
 }
}
'''

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()

def command(cmd,directory,label):
    (directory/(label+'-command.json')).write_text(json.dumps(cmd,indent=2)+'\n')
    result=subprocess.run(cmd,cwd=ROOT,text=True,capture_output=True,timeout=60)
    (directory/(label+'.stdout')).write_text(result.stdout)
    (directory/(label+'.stderr')).write_text(result.stderr)
    if result.returncode:raise RuntimeError(f'{label} failed {result.returncode}:\n{result.stdout}\n{result.stderr}')
    return result

def classpath():
    cp=json.loads((ROOT/'dev/logs/concept-transfer-r1/reimagined-neoforge-main/checks-classpath.json').read_text())
    cp.append(str(ROOT/'ports/26.2/runtime/neoforge-libraries/net.caffeinemc.sodium-neoforge-0.9.2+mc26.2-mod.jar'))
    cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
    for group in ('org.junit.jupiter/junit-jupiter-api/5.14.1','org.junit.jupiter/junit-jupiter-engine/5.14.1',
            'org.junit.platform/junit-platform-launcher/1.14.1','org.junit.platform/junit-platform-engine/1.14.1',
            'org.junit.platform/junit-platform-commons/1.14.1','org.opentest4j/opentest4j','org.apiguardian/apiguardian-api'):
        jars=sorted((cache/group).rglob('*.jar'))
        if not jars:raise RuntimeError('Missing actual JUnit dependency: '+group)
        cp.extend(map(str,jars))
    return cp

class OfflineDeliveryIntegrationTests(unittest.TestCase):
    def test_both_reviewed_overlays_through_real_selection_settings_consumers(self):
        receivers=ROOT/'dev/logs/offline-delivery-sprint-20261012/combined-consumer'
        receivers.mkdir(parents=True,exist_ok=True)
        receiver=Path(tempfile.mkdtemp(prefix='receiver-',dir=receivers))
        before={p:sha(ROOT/p) for p in settings.prep.PINS}
        before.update({p:sha(ROOT/p) for p in (CATALOG,'patches/adapters/complementary-reimagined.json',
            RESOURCE_ROOT+'patches/adapters/complementary-reimagined.json',
            'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/ReimaginedSourceCertificate.java',
            'src/main/java/com/pp/shaderlod/integration/reimaginedquad/ReimaginedQuadEligibility.java',
            'src/main/resources/assets/shaderlod/reimaginedquad/native398.json',
            'src/main/resources/assets/shaderlod/reimaginedquad/source-proof.json',
            'src/main/resources/assets/shaderlod/reimaginedquad/api-proof.json')})
        settings_output=receiver/'settings';settings.prep.prepare(settings_output)
        self.assertEqual(sha(settings_output/'integration.patch'),SETTINGS_PATCH_SHA)
        weight_output=receiver/'weight';weight_receipt=prepare_weight(BASE,weight_output)
        self.assertEqual(weight_receipt['current_adapter_digest'],OLD_DIGEST)
        self.assertEqual(weight_receipt['candidate_digest'],NEW_DIGEST)
        certificate_path='ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/ReimaginedSourceCertificate.java'
        self.assertEqual(weight_receipt['overlay'][certificate_path]['after'],
            '1238ecceeeadf239a099e49929a3b660708b6894fa9e80266da6dbd1c35045aa')
        self.assertEqual(weight_receipt['overlay']['patches/adapters/complementary-reimagined.json']['before'],CURRENT_RECIPE_SHA)
        self.assertEqual(weight_receipt['overlay']['patches/adapters/complementary-reimagined.json']['after'],NEW_RECIPE_SHA)
        merged=receiver/'merged';shutil.copytree(settings_output/'source',merged)
        shutil.copytree(ROOT/'src/main/resources',merged/'src/main/resources')
        for name in weight_receipt['overlay']:
            target=merged/name;target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(weight_output/'overlay'/name,target)
        self.assertEqual(sha(merged/RESOURCE_ROOT/'patches/adapters/complementary-reimagined.json'),NEW_RECIPE_SHA)
        cp=classpath();jdk=ROOT/'dev/runtime/jdk25/jdk-25.0.2.jdk/Contents/Home/bin'
        baseline=receiver/'old-selection';baseline.mkdir()
        shutil.copy2(BASE,baseline/'Reimagined.zip')
        native_values=json.loads((ROOT/'src/main/resources/assets/shaderlod/reimaginedquad/native398.json').read_bytes())
        self.assertEqual(len(native_values),398)
        (baseline/'Reimagined.zip.txt').write_text(''.join(key+'='+value+'\n' for key,value in sorted(native_values.items())))
        old_manual=receiver/'old-manual.zip'
        build(BASE,old_manual,ROOT/'patches/adapters/complementary-reimagined.json')
        generator=receiver/'AuthenticOldSelection.java';generator.write_text(GENERATOR)
        old_classes=receiver/'old-classes';old_classes.mkdir()
        original_sources=[ROOT/'src/main/java/com/pp/shaderlod/packs/PackSelectionService.java',
            ROOT/CATALOG,ROOT/'src/main/java/com/pp/shaderlod/LodPreset.java',
            ROOT/'src/main/java/com/pp/shaderlod/integration/reimaginedquad/ReimaginedQuadEligibility.java',
            ROOT/'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/integration/bindingcompat/ReimaginedSourceCertificate.java',generator]
        command([str(jdk/'javac'),'-J-XX:ActiveProcessorCount=2','-proc:none','-cp',os.pathsep.join(cp),
            '-d',str(old_classes),*map(str,original_sources)],receiver,'old-source-compile')
        generated=command([str(jdk/'java'),'-XX:ActiveProcessorCount=2','-Djava.awt.headless=true','-cp',
            os.pathsep.join([str(old_classes),str(ROOT/'src/main/resources'),*cp]),'AuthenticOldSelection',str(baseline)],
            receiver,'old-service-generate').stdout.strip()
        self.assertTrue(generated.startswith('ShaderLOD-complementary-reimagined-'))
        (baseline/'baseline-receipt.json').write_text(json.dumps({'generatedPack':generated,'recipeSha256':CURRENT_RECIPE_SHA,
            'oldSourceDigest':OLD_DIGEST,'generator':'actual unchanged PackSelectionService'},indent=2)+'\n')
        historical=receiver/'historical-certificate-new-resources';historical.mkdir()
        shutil.copy2(BASE,historical/'Reimagined.zip')
        shutil.copy2(baseline/'Reimagined.zip.txt',historical/'Reimagined.zip.txt')
        historical_probe=receiver/'HistoricalCertificateProbe.java';historical_probe.write_text(HISTORICAL_PROBE)
        command([str(jdk/'javac'),'-J-XX:ActiveProcessorCount=2','-proc:none','-cp',os.pathsep.join([str(old_classes),*cp]),
            '-d',str(old_classes),str(historical_probe)],receiver,'historical-probe-compile')
        command([str(jdk/'java'),'-XX:ActiveProcessorCount=2','-Djava.awt.headless=true','-cp',
            os.pathsep.join([str(old_classes),str(merged/'src/main/resources'),*cp]),'HistoricalCertificateProbe',str(historical)],
            receiver,'historical-certificate-new-resources')
        fixture=(ROOT/'ports/26.2/neoforge/src/test/java/com/pp/shaderlod/ShaderLodSettingsSelectionTest.java').read_text()
        fixture=fixture[:fixture.index('    @Test')]
        fixture=fixture.replace('class ShaderLodSettingsSelectionTest {','class CombinedOfflineDeliveryTest {')
        fixture=fixture.replace('        String selected = "Photon.zip";',
            '        String selected = "Photon.zip";\n        boolean nativeAvailable;\n        public boolean nativeRendererAvailable() { return nativeAvailable; }')
        helpers=settings.JAVA_TESTS[:settings.JAVA_TESTS.index('    @Test')]
        java_test=receiver/'CombinedOfflineDeliveryTest.java';java_test.write_text(fixture+helpers+JAVA_TESTS+'\n}\n')
        launcher=receiver/'CombinedCpuLauncher.java';launcher.write_text(LAUNCHER)
        classes=receiver/'classes';classes.mkdir()
        sources=list(merged.rglob('*.java'))
        sources += [ROOT/'ports/26.2/neoforge/src/main/java/com/pp/shaderlod/ShaderLodConfig.java',
            ROOT/'src/main/java/com/pp/shaderlod/LodSettings.java',ROOT/'src/main/java/com/pp/shaderlod/LodPreset.java',
            ROOT/'src/main/java/com/pp/shaderlod/integration/PackSelectionTransaction.java',
            ROOT/'src/main/java/com/pp/shaderlod/integration/reimaginedquad/ReimaginedQuadEligibility.java',
            java_test,launcher]
        command([str(jdk/'javac'),'-J-XX:ActiveProcessorCount=2','-proc:none','-cp',os.pathsep.join(cp),
            '-d',str(classes),*map(str,sources)],receiver,'combined-source-compile')
        result=command([str(jdk/'java'),'-XX:ActiveProcessorCount=2','-Djava.awt.headless=true',
            '-Dcombined.oldSelection='+str(baseline),'-Dcombined.oldManual='+str(old_manual),
            '-Dcombined.historical='+str(historical),'-cp',
            os.pathsep.join([str(classes),str(merged/'src/main/resources'),*cp]),'CombinedCpuLauncher'],receiver,'combined-consumers')
        self.assertIn('7 tests successful',result.stdout)
        self.assertEqual(before,{p:sha(ROOT/p) for p in before})
        receipt={'source_only':True,'sealed_inputs_unchanged':before,'settings_patch_sha256':SETTINGS_PATCH_SHA,
            'old_recipe_sha256':CURRENT_RECIPE_SHA,'new_recipe_sha256':NEW_RECIPE_SHA,
            'old_source_digest':OLD_DIGEST,'candidate_source_digest':NEW_DIGEST,
            'jvm_tests_found':7,'jvm_tests_passed':7,'jvm_tests_skipped':0,'stub_classes':0,
            'simulation':'renderer certificate,world/frame identity,effective options; no Minecraft/GPU/driver/UI/FPS claim',
            'old_state_refused_without_reload_or_mutation':True,'legacy_manual_recognition_retained':True,
            'actual_old_source_certificate_baseline_accepted':True,
            'actual_native398_certificate_with_new_resources_accepted':True,
            'historical_certificate_with_new_resources_refusal':'Stale/tampered packaged recipe',
            'actual_r2_fresh_candidate_generated402_certificate_admitted':True,
            'r2_certificate_negative_guards':'Inactivegeneratedfalse refused/nativereturntrue admitted; altered shader/state/options/original refused; old96bbstate remains typed refusal',
            'remaining_boundary':'NF26.2 exact certificate constant overlay closes source admission join in isolated combined CPU consumer only. Product source/JAR/runtime adoption still unapplied/unverified; fresh state and future build/runtime checks required; no guard relaxed.',
            'source_hashes':{str(p.relative_to(receiver)):sha(p) for p in sources if p.is_relative_to(receiver)},
            'classpath_receipt_sha256':sha(ROOT/'dev/logs/concept-transfer-r1/reimagined-neoforge-main/checks-classpath.json'),
            'test_sha256':sha(__file__),'summary':result.stdout,'receiver':str(receiver)}
        (receiver/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        print('Combined CPU evidence:',receiver)

if __name__=='__main__':unittest.main()
