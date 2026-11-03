package com.pp.shaderlod.tuning;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.packs.PackSelectionService;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TuningApplyPinsTest {
    @TempDir static Path directory;
    private static Map<String,String> artifact,nativeValues,generatedValues;
    private static Path original,generated;
    @BeforeAll static void actualArtifactAndPublicSourceProducer() throws Exception {
        // Actual loaded measurement classes and directory bytes, not a configured substitute provider.
        var origin=Path.of(MeasurementPins.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        artifact=ModArtifactIdentity.capture(new ModArtifactIdentity.Origins("actual test class-directory origin",
            "complete effective loaded measurement module view; no claim about hidden physical inputs",List.of(origin),List.of(origin),false),
            MeasurementPins.class,RuntimeMeasurement.class,OpenGlElapsedBackend.class);
        assertEquals("true",artifact.get("mod_artifact_valid"),artifact.toString());
        original=directory.resolve("Original.zip");Files.copy(Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"),original);
        var pair=TuningSourceIdentity.capture(directory,"Original.zip");pair.requireCurrent(directory,"Original.zip");
        nativeValues=resourceOptions("makeupquad/native100.json");
        var result=new PackSelectionService().apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,nativeValues,false);
        assertTrue(result.accepted(),result.detail());assertTrue(result.generatedArchive());generated=directory.resolve(result.packFile());
        pair.requireCurrent(directory,result.packFile());
        try(var input=TuningApplyPinsTest.class.getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")) {
            var map=new TreeMap<String,String>();JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("makeup-ultra-fast-shaders").entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));generatedValues=Map.copyOf(map);
        }
    }
    private static Map<String,String> resourceOptions(String member) throws Exception {
        try(var input=TuningApplyPinsTest.class.getResourceAsStream("/assets/shaderlod/"+member)) {
            var map=new TreeMap<String,String>();JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e->map.put(e.getKey(),e.getValue().getAsString()));return Map.copyOf(map);
        }
    }
    private Map<String,String> pins(String graph) throws Exception {
        Path path=graph.equals("original")?original:generated;var source=PackSourceReader.read(path);var pins=new HashMap<>(artifact);
        pins.put("source_path",path.toAbsolutePath().toString());pins.put("source_sha256",source.sourceSha256());pins.put("source_family",MeasurementPins.sourceFamily(source));
        // Preserved actual device-field examples; these shape/invariance checks perform no GL queries.
        pins.put("resolution","Dimensions[width=1920, height=1200]");pins.put("gpu_vendor","Apple");pins.put("gpu_renderer","Apple M3 Max");pins.put("gl_version","4.1 Metal - 91.7");
        return pins;
    }
    @Test void verifiedOriginalGeneratedOriginalTransitionsRetainExactGraphPins() throws Exception {
        var p=new TuningApplyPins();p.verify(false,TuningApplyPins.Graph.ORIGINAL,pins("original"),nativeValues);
        p.verify(true,TuningApplyPins.Graph.ORIGINAL,pins("original"),nativeValues);
        p.verify(true,TuningApplyPins.Graph.GENERATED,pins("generated"),generatedValues);
        p.verify(true,TuningApplyPins.Graph.ORIGINAL,pins("original"),nativeValues);
        var changed=new HashMap<>(pins("generated"));changed.put("source_sha256",pins("original").get("source_sha256"));
        assertThrows(IllegalStateException.class,()->p.verify(true,TuningApplyPins.Graph.GENERATED,changed,generatedValues));
    }
    @Test void firstTransitionCannotHideNativeAaViewportDeviceOrArtifactChanges() throws Exception {
        for(String change:List.of("AA_TYPE","resolution","gpu_renderer","mod_artifact_sha256")) {
            var p=new TuningApplyPins();p.verify(false,TuningApplyPins.Graph.ORIGINAL,pins("original"),nativeValues);
            var changed=pins("generated");var values=new HashMap<>(generatedValues);
            if(change.equals("AA_TYPE"))values.put(change,"0");
            else changed.put(change,change.equals("resolution")?"Dimensions[width=1280, height=720]":change.equals("mod_artifact_sha256")?pins("original").get("source_sha256"):"Different actual renderer");
            assertThrows(IllegalStateException.class,()->p.verify(true,TuningApplyPins.Graph.GENERATED,changed,values),change);
        }
    }
    @Test void missingNativeOptionOrInitialGeneratedGraphCannotBeAccepted() throws Exception {
        var p=new TuningApplyPins();var initialGenerated=pins("generated");assertThrows(IllegalStateException.class,()->p.verify(true,TuningApplyPins.Graph.GENERATED,initialGenerated,generatedValues));
        p.verify(false,TuningApplyPins.Graph.ORIGINAL,pins("original"),nativeValues);
        var valid=pins("generated");assertThrows(IllegalStateException.class,()->p.verify(true,TuningApplyPins.Graph.GENERATED,valid,Map.of()));
    }
    @Test void invalidMissingAndPlaceholderIdentitiesFailBeforePinning() throws Exception {
        var valid=pins("original");assertDoesNotThrow(()->TuningApplyPins.requireValid(valid));
        assertEquals("false",valid.get("mod_artifact_complete_physical_inputs")); // Explicit effective-module view remains legitimate.
        for(String key:List.of("mod_artifact_valid","mod_artifact_sha256","mod_artifact_manifest_sha256","mod_artifact_backend_anchors","mod_artifact_stability",
                "source_sha256","source_path","resolution","gpu_vendor","gpu_renderer","gl_version")) {
            var missing=new HashMap<>(valid);missing.remove(key);assertThrows(IllegalStateException.class,()->TuningApplyPins.requireValid(missing),key);
        }
        for(var edit:Map.of("mod_artifact_valid","false","mod_artifact_sha256","unavailable","source_sha256","not-a-digest",
                "mod_artifact_files","0","resolution","Dimensions[width=0, height=1200]","gpu_renderer","unknown","source_path","relative.zip").entrySet()) {
            var invalid=new HashMap<>(valid);invalid.put(edit.getKey(),edit.getValue());assertThrows(IllegalStateException.class,()->TuningApplyPins.requireValid(invalid),edit.getKey());
        }
        var p=new TuningApplyPins();var invalid=new HashMap<>(valid);invalid.put("mod_artifact_valid","false");
        assertThrows(IllegalStateException.class,()->p.verify(false,TuningApplyPins.Graph.ORIGINAL,invalid,nativeValues));
        var generatedAfterRefusal=pins("generated");
        assertThrows(IllegalStateException.class,()->p.verify(true,TuningApplyPins.Graph.GENERATED,generatedAfterRefusal,generatedValues)); // No original owner was acquired.
        assertDoesNotThrow(()->p.verify(false,TuningApplyPins.Graph.ORIGINAL,valid,nativeValues));
    }
}
