package com.pp.shaderlod.packs;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SildurQuadServiceTest {
 @TempDir Path dir;
 private Object savedFlags;
 @TempDir Path flagsDirectory;
 @BeforeEach void isolateFlags()throws Exception {
  var save=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("state");save.setAccessible(true);savedFlags=save.invoke(null);
  com.pp.shaderlod.optimization.OptimizationConfig.load(flagsDirectory.resolve("requests.properties"));
  com.pp.shaderlod.optimization.OptimizationConfig.replace(cpuPair());
 }
 @AfterEach void restoreFlags()throws Exception {
  var restore=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("restoreState",savedFlags.getClass());restore.setAccessible(true);restore.invoke(null,savedFlags);
 }
 private static com.pp.shaderlod.optimization.OptimizationSnapshot cpuPair(){return com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested().with(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,true).with(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,true);}
 private Properties options(String pack)throws Exception {var p=new Properties();try(var in=Files.newInputStream(dir.resolve(pack+".txt"))){p.load(in);}return p;}

 private final Path original=Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip");
 private void install()throws Exception {Files.copy(original,dir.resolve("Sildur.zip"));}
 private Map<String,byte[]> snapshot()throws Exception {var all=new TreeMap<String,byte[]>();try(var files=Files.list(dir)){for(var f:files.toList())all.put(f.getFileName().toString(),Files.readAllBytes(f));}return all;}
 private void unchanged(Map<String,byte[]> before)throws Exception {var after=snapshot();assertEquals(before.keySet(),after.keySet());before.forEach((k,v)->assertArrayEquals(v,after.get(k),k));}
 @Test void shaderPresetsPreserveUncertifiedNativePreferencesAndRecoverOriginal()throws Exception {
  install();String preferences="# My native preset\r\nTAA=false\r\nClouds=2\r\nnMap=1\r\nVolumetric_Lighting=false\r\n";Files.writeString(dir.resolve("Sildur.zip.txt"),preferences);byte[] bytes=Files.readAllBytes(original);
  for(var preset:LodPreset.values()){
   var before=snapshot();var service=new PackSelectionService();var on=service.apply(dir,"Sildur.zip",true,preset,Map.of("TAA","false","Clouds","2","nMap","1","Volumetric_Lighting","false"),false);
   assertTrue(on.accepted(),on.detail());assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Sildur.zip")));assertEquals(preferences,Files.readString(dir.resolve("Sildur.zip.txt")));
   if(preset==LodPreset.QUALITY){assertEquals("Sildur.zip",on.packFile());unchanged(before);}
   else {assertNotEquals("Sildur.zip",on.packFile());assertEquals("false",options(on.packFile()).getProperty("TAA"));for(var value:Map.of("TAA","false","Clouds","2","nMap","1","Volumetric_Lighting","false").entrySet())assertEquals(value.getValue(),options(on.packFile()).getProperty(value.getKey()));}
   var off=service.apply(dir,on.packFile(),false,preset,Map.of(),false);assertTrue(off.accepted(),off.detail());assertEquals("Sildur.zip",off.packFile());assertEquals(preferences,Files.readString(dir.resolve("Sildur.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Sildur.zip")));
  }
  var mismatch=new TreeMap<>(SildurQuadEligibility.nativeOptions());mismatch.put("TAA","false");assertFalse(SildurQuadEligibility.validate(dir.resolve("Sildur.zip"),mismatch).eligible(),"Uncertified preferences never certify the native CPU backend");
 }
 @Test void certifiedCpuOnlyPerformanceAndQualityKeepNativeWhileShaderRequestsNeedAdapter()throws Exception {
  install();assertTrue(SildurQuadEligibility.validate(dir.resolve("Sildur.zip"),SildurQuadEligibility.nativeOptions()).eligible());byte[] bytes=Files.readAllBytes(original);
  for(var preset:LodPreset.values()){
   var before=snapshot();var service=new PackSelectionService();var on=service.apply(dir,"Sildur.zip",true,preset,Map.of(),true);assertTrue(on.accepted(),on.detail());
   if(preset==LodPreset.PERFORMANCE||preset==LodPreset.QUALITY){assertEquals(PackSelectionService.Status.UNCHANGED,on.status());assertEquals("Sildur.zip",on.packFile());unchanged(before);}
   else assertNotEquals("Sildur.zip",on.packFile());
   var off=service.apply(dir,on.packFile(),false);assertTrue(off.accepted(),off.detail());assertEquals("Sildur.zip",off.packFile());assertFalse(Files.exists(dir.resolve("Sildur.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Sildur.zip")));
  }
  com.pp.shaderlod.optimization.OptimizationConfig.replace(cpuPair().with(com.pp.shaderlod.optimization.OptimizationId.AO_BUDGET,true));
  var shader=new PackSelectionService().apply(dir,"Sildur.zip",true,LodPreset.PERFORMANCE,Map.of(),true);assertTrue(shader.accepted(),shader.detail());assertNotEquals("Sildur.zip",shader.packFile(),"Explicit shader work cannot take CPU-only native bypass");
  var quality=new PackSelectionService().apply(dir,shader.packFile(),true,LodPreset.QUALITY,Map.of(),true);assertTrue(quality.accepted(),quality.detail());assertEquals("Sildur.zip",quality.packFile());assertFalse(Files.exists(dir.resolve("Sildur.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Sildur.zip")));
 }
 @Test void originalSettingsRetainNativeDisabledVlAndNoOwnedMarker()throws Exception {
  install();byte[] settings;try(var z=new ZipFile(original.toFile())){settings=z.getInputStream(z.getEntry("shaders/shaders.settings")).readAllBytes();}
  assertTrue(new String(settings,java.nio.charset.StandardCharsets.UTF_8).contains("//#define Volumetric_Lighting"));
  new PackSelectionService().apply(dir,"Sildur.zip",true);
  try(var z=new ZipFile(dir.resolve("Sildur.zip").toFile())){assertArrayEquals(settings,z.getInputStream(z.getEntry("shaders/shaders.settings")).readAllBytes());assertNull(z.getEntry("shaderlod-selection.json"));}
 }
 @Test void changedOriginalZipRefusesWithoutCreatingOwnedFiles()throws Exception {
  install();var wrong=new TreeMap<String,byte[]>();try(var z=new ZipFile(original.toFile())){for(var e:Collections.list(z.entries()))if(!e.isDirectory())wrong.put(e.getName(),z.getInputStream(e).readAllBytes());}
  wrong.put("shaders/gbuffers_textured.vsh",new byte[]{42});
  try(var z=new ZipOutputStream(Files.newOutputStream(dir.resolve("Sildur.zip")))){for(var e:wrong.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}
  var before=snapshot();assertFalse(new PackSelectionService().apply(dir,"Sildur.zip",true).accepted());unchanged(before);
 }
}
