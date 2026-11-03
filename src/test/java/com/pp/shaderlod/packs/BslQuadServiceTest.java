package com.pp.shaderlod.packs;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class BslQuadServiceTest {
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

 private final Path original=Path.of("dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");
 private void install()throws Exception {Files.copy(original,dir.resolve("Bsl.zip"));}
 private Map<String,byte[]> snapshot()throws Exception {var all=new TreeMap<String,byte[]>();try(var files=Files.list(dir)){for(var f:files.toList())all.put(f.getFileName().toString(),Files.readAllBytes(f));}return all;}
 private void unchanged(Map<String,byte[]> before)throws Exception {var after=snapshot();assertEquals(before.keySet(),after.keySet());before.forEach((k,v)->assertArrayEquals(v,after.get(k),k));}
 @Test void shaderPresetsPreserveUncertifiedNativePreferencesAndRecoverOriginal()throws Exception {
  install();String preferences="# My native preset\r\nTAA=false\r\nADVANCED_MATERIALS=true\r\nREFLECTION=1\r\n";Files.writeString(dir.resolve("Bsl.zip.txt"),preferences);byte[] bytes=Files.readAllBytes(original);
  for(var preset:LodPreset.values()){
   var before=snapshot();var service=new PackSelectionService();var on=service.apply(dir,"Bsl.zip",true,preset,Map.of("TAA","false","ADVANCED_MATERIALS","true","REFLECTION","1"),false);
   assertTrue(on.accepted(),on.detail());assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Bsl.zip")));assertEquals(preferences,Files.readString(dir.resolve("Bsl.zip.txt")));
   if(preset==LodPreset.QUALITY){assertEquals("Bsl.zip",on.packFile());unchanged(before);}
   else {assertNotEquals("Bsl.zip",on.packFile());assertEquals("false",options(on.packFile()).getProperty("TAA"));for(var value:Map.of("TAA","false","ADVANCED_MATERIALS","true","REFLECTION","1").entrySet())assertEquals(value.getValue(),options(on.packFile()).getProperty(value.getKey()));}
   var off=service.apply(dir,on.packFile(),false,preset,Map.of(),false);assertTrue(off.accepted(),off.detail());assertEquals("Bsl.zip",off.packFile());assertEquals(preferences,Files.readString(dir.resolve("Bsl.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Bsl.zip")));
  }
  var mismatch=new TreeMap<>(BslQuadEligibility.nativeOptions());mismatch.put("TAA","false");assertFalse(BslQuadEligibility.validate(dir.resolve("Bsl.zip"),mismatch).eligible(),"Uncertified preferences never certify the native CPU backend");
 }
 @Test void certifiedCpuOnlyPerformanceAndQualityKeepNativeWhileShaderRequestsNeedAdapter()throws Exception {
  install();assertTrue(BslQuadEligibility.validate(dir.resolve("Bsl.zip"),BslQuadEligibility.nativeOptions()).eligible());byte[] bytes=Files.readAllBytes(original);
  for(var preset:LodPreset.values()){
   var before=snapshot();var service=new PackSelectionService();var on=service.apply(dir,"Bsl.zip",true,preset,Map.of(),true);assertTrue(on.accepted(),on.detail());
   if(preset==LodPreset.PERFORMANCE||preset==LodPreset.QUALITY){assertEquals(PackSelectionService.Status.UNCHANGED,on.status());assertEquals("Bsl.zip",on.packFile());unchanged(before);}
   else assertNotEquals("Bsl.zip",on.packFile());
   var off=service.apply(dir,on.packFile(),false);assertTrue(off.accepted(),off.detail());assertEquals("Bsl.zip",off.packFile());assertFalse(Files.exists(dir.resolve("Bsl.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Bsl.zip")));
  }
  com.pp.shaderlod.optimization.OptimizationConfig.replace(cpuPair().with(com.pp.shaderlod.optimization.OptimizationId.AO_BUDGET,true));
  var shader=new PackSelectionService().apply(dir,"Bsl.zip",true,LodPreset.PERFORMANCE,Map.of(),true);assertTrue(shader.accepted(),shader.detail());assertNotEquals("Bsl.zip",shader.packFile(),"Explicit shader work cannot take CPU-only native bypass");
  var quality=new PackSelectionService().apply(dir,shader.packFile(),true,LodPreset.QUALITY,Map.of(),true);assertTrue(quality.accepted(),quality.detail());assertEquals("Bsl.zip",quality.packFile());assertFalse(Files.exists(dir.resolve("Bsl.zip.txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve("Bsl.zip")));
 }
 @Test void originalArchiveRetainsAllBytesAndNoOwnedMarker()throws Exception {
  install();byte[] originalBytes=Files.readAllBytes(original);
  new PackSelectionService().apply(dir,"Bsl.zip",true);
  assertArrayEquals(originalBytes,Files.readAllBytes(dir.resolve("Bsl.zip")));
  try(var z=new ZipFile(dir.resolve("Bsl.zip").toFile())){assertNull(z.getEntry("shaderlod-selection.json"));}
 }
 @Test void changedOriginalZipRefusesWithoutCreatingOwnedFiles()throws Exception {
  install();var wrong=new TreeMap<String,byte[]>();try(var z=new ZipFile(original.toFile())){for(var e:Collections.list(z.entries()))if(!e.isDirectory())wrong.put(e.getName(),z.getInputStream(e).readAllBytes());}
  wrong.put("shaders/world0/gbuffers_terrain.vsh",new byte[]{42});
  try(var z=new ZipOutputStream(Files.newOutputStream(dir.resolve("Bsl.zip")))){for(var e:wrong.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue());z.closeEntry();}}
  var before=snapshot();assertFalse(new PackSelectionService().apply(dir,"Bsl.zip",true).accepted());unchanged(before);
 }
}
