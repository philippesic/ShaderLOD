package com.pp.shaderlod.optimization.bsl;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual normal producer and pinned Iris preprocessor; no fabricated GLSL330 source. */
final class BslPomCachePreprocessTest {
    private boolean previousScopedSelections;
    @org.junit.jupiter.api.BeforeEach void rememberSelectionMode() throws Exception {
        var field=PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);previousScopedSelections=field.getBoolean(null);
    }
    @org.junit.jupiter.api.AfterEach void restoreSelectionMode() throws Exception {
        var field=PackSelectionService.class.getDeclaredField("recipeScopedSelections");field.setAccessible(true);field.setBoolean(null,previousScopedSelections);
    }

 @TempDir Path directory;
 @Test void normalNative120PbrConsumerSurvivesPreprocessingOnlyForThePinnedAdapter() throws Exception {
  Files.copy(Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip"),directory.resolve("Original.zip"));
  var options=BslPrefixProfile.nativeOptions();assertEquals(384,options.size());assertEquals("true",options.get("ADVANCED_MATERIALS"));
  PackSelectionService.enableRecipeScopedSelections();var service=new PackSelectionService();
  var result=service.apply(directory,"Original.zip",true,LodPreset.PERFORMANCE,options,false,options,options.keySet());
  assertTrue(result.accepted(),result.detail());
  try(var zip=new ZipFile(directory.resolve(result.packFile()).toFile())) {
   String kernel=new String(zip.getInputStream(zip.getEntry("shaders/lib/surface/parallax.glsl")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   String root=new String(zip.getInputStream(zip.getEntry("shaders/world0/gbuffers_terrain.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   var version=java.util.regex.Pattern.compile("(?m)^#version[^\\r\\n]*").matcher(root);assertTrue(version.find());assertTrue(version.group().contains("120"));
   // This check owns conditional activation only. Included helpers are independently assembled by the normal producer;
   // their bodies and actual linked GPU execution belong to the separate full shader/driver check.
   kernel=kernel.replaceAll("(?m)^\\s*#include[^\\r\\n]*","");
   String nativeProfile="\n#define SHADERLOD_BSL_TERRAIN\n#define ADVANCED_MATERIALS\n#define PARALLAX\n#define MATERIAL_FORMAT 1\n#define PARALLAX_QUALITY 128\n#define MC_VERSION 12101\n#define IS_IRIS\n";
   nativeProfile += "#define PARALLAX_DEPTH "+options.get("PARALLAX_DEPTH")+"\n";
   for(String mode:List.of("enabled","off","unsupported","dh","voxy","curvature","nonterrain","otherQuality","otherFormat")) {
    String profile=nativeProfile;
    if(mode.equals("nonterrain"))profile=profile.replace("#define SHADERLOD_BSL_TERRAIN","");
    if(mode.equals("otherQuality"))profile=profile.replace("PARALLAX_QUALITY 128","PARALLAX_QUALITY 64");
    if(mode.equals("otherFormat"))profile=profile.replace("MATERIAL_FORMAT 1","MATERIAL_FORMAT 2");
    String extra=switch(mode){case "dh"->"#define DISTANT_HORIZONS\n";case "voxy"->"#define VOXY\n";case "curvature"->"#define WORLD_CURVATURE\n";default->"";};
    String source=version.group()+profile+extra+"#define SHADERLOD_REFINEMENT_ADAPTER "+(mode.equals("off")?"0":"1")+"\n#define IRIS_VERSION "+(mode.equals("unsupported")?"19999":"10812")+"\n"+kernel;
    String actual=JcppProcessor.glslPreprocessSource(source,List.of());
    assertEquals(mode.equals("enabled"),actual.contains("bool shaderlodInitialReuse"),mode);
    assertTrue(actual.contains("currentStep <= depth"),"native recurrence retained: "+mode);
    assertFalse(actual.contains("shaderlodBslPomCacheKey"),"rounded-cell cache removed");
    assertFalse(actual.contains("previousKey"),"no coordinate-key reuse");
    if(mode.equals("enabled")) {
     assertEquals(BslPrefixProfile.functions().get("GetParallaxCoord"),
       com.pp.shaderlod.bslprefix.BslPrefixSourceR8.hash(com.pp.shaderlod.bslprefix.BslPrefixSourceR8.function(actual,"GetParallaxCoord")),
       "prefix qualification must pin the actual current native-derived POM body");
     assertTrue(actual.contains("shaderlodBslInitialNormal = ReadNormal(coord)"));
     assertTrue(actual.contains("shaderlodInitialReuse ? shaderlodBslInitialNormal.a : ReadNormal(coord).a"));
     assertTrue(actual.indexOf("shaderlodBslInitialNormal = ReadNormal(coord)") < actual.indexOf("normalCheck < -1.999"));
     assertTrue(actual.indexOf("normalCheck < -1.999") < actual.indexOf("bool shaderlodInitialReuse"));
    } else {
     assertFalse(actual.contains("shaderlodBslInitialNormal"));
    }
   }
  }
  try(var nativeZip=new ZipFile(directory.resolve("Original.zip").toFile());var generatedZip=new ZipFile(directory.resolve(result.packFile()).toFile())) {
   String nativeKernel=new String(nativeZip.getInputStream(nativeZip.getEntry("shaders/lib/surface/parallax.glsl")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   String generatedKernel=new String(generatedZip.getInputStream(generatedZip.getEntry("shaders/lib/surface/parallax.glsl")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   assertEquals(nativeKernel.substring(nativeKernel.indexOf("float GetParallaxShadow")),generatedKernel.substring(generatedKernel.indexOf("float GetParallaxShadow")),"native shadow bytes retained");
  }
 }
}
