package com.pp.shaderlod.packs;
import com.pp.shaderlod.LodPreset;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class QuadTransferServiceTest {
 @TempDir Path dir;
 private Object savedFlags;
 @TempDir Path flagsDirectory;
 @BeforeEach void isolateFlags()throws Exception {var save=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("state");save.setAccessible(true);savedFlags=save.invoke(null);com.pp.shaderlod.optimization.OptimizationConfig.load(flagsDirectory.resolve("requests.properties"));com.pp.shaderlod.optimization.OptimizationConfig.replace(cpuPair());}
 @AfterEach void restoreFlags()throws Exception {var restore=com.pp.shaderlod.optimization.OptimizationConfig.class.getDeclaredMethod("restoreState",savedFlags.getClass());restore.setAccessible(true);restore.invoke(null,savedFlags);}
 private static com.pp.shaderlod.optimization.OptimizationSnapshot cpuPair(){return com.pp.shaderlod.optimization.OptimizationSnapshot.noneRequested().with(com.pp.shaderlod.optimization.OptimizationId.QUAD_COMMAND_COMPACTION,true).with(com.pp.shaderlod.optimization.OptimizationId.SHADOW_ADMISSION_CACHE,true);}
 private String previous;
 @BeforeEach void remember(){previous=System.getProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH);System.clearProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH);}
 @AfterEach void restore(){if(previous==null)System.clearProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH);else System.setProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH,previous);}
 private Path original(String name,String path)throws Exception {Path zip=dir.resolve(name);Files.copy(Path.of(path),zip);return zip;}
 private Map<String,byte[]> snapshot()throws Exception {var all=new TreeMap<String,byte[]>();try(var files=Files.list(dir)){for(var f:files.toList())all.put(f.getFileName().toString(),Files.readAllBytes(f));}return all;}
 private void unchanged(Map<String,byte[]> before)throws Exception {var after=snapshot();assertEquals(before.keySet(),after.keySet());before.forEach((k,v)->assertArrayEquals(v,after.get(k),k));}
 @Test void photonShaderGraphAndCertifiedCpuOnlyNativeRouteRemainDistinct()throws Exception {
  original("Photon.zip","dependencies/upstream/photon_v1.3b.zip");
  Files.writeString(dir.resolve("Photon.zip.txt"),"TAA=true\nTAAU=false\nTAAU_RENDER_SCALE=0.75\n");
  var generated=new PackSelectionService().apply(dir,"Photon.zip",true);assertTrue(generated.accepted(),generated.detail());assertNotEquals("Photon.zip",generated.packFile());
  assertTrue(Files.readString(dir.resolve(generated.packFile()+".txt")).contains("SHADERLOD_LIVE_PIXEL_BUDGET=true"));
  System.setProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH,"true");
  var propertyOnly=new PackSelectionService().apply(dir,generated.packFile(),true);assertTrue(propertyOnly.accepted(),propertyOnly.detail());assertEquals(generated.packFile(),propertyOnly.packFile(),"Diagnostic property cannot invent actual renderer availability");
  var restored=new PackSelectionService().apply(dir,generated.packFile(),true,LodPreset.PERFORMANCE,Map.of(),true);assertTrue(restored.accepted(),restored.detail());assertEquals("Photon.zip",restored.packFile());
  String nativeOptions=Files.readString(dir.resolve("Photon.zip.txt"));assertTrue(nativeOptions.contains("TAAU=false"));assertTrue(nativeOptions.contains("TAAU_RENDER_SCALE=0.75"));assertFalse(nativeOptions.contains("SHADERLOD_LIVE_PIXEL_BUDGET"));
 }
 @Test void uncertifiedPreferencesKeepNativeAaThroughGeneratedPresetsAndQualityRecovery()throws Exception {
  System.setProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH,"true");
  for(var input:Map.of("Photon.zip","dependencies/upstream/photon_v1.3b.zip","Reimagined.zip","dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip").entrySet()){
   original(input.getKey(),input.getValue());String preferences="# user preset\r\nTAA=false\r\nTAAU=false\r\nTAAU_RENDER_SCALE=0.67\r\nRP_MODE=2\r\n";Files.writeString(dir.resolve(input.getKey()+".txt"),preferences);byte[] bytes=Files.readAllBytes(dir.resolve(input.getKey()));
   for(var preset:LodPreset.values()){
    var before=snapshot();var service=new PackSelectionService();var result=service.apply(dir,input.getKey(),true,preset,Map.of("TAA","false"),false);assertTrue(result.accepted(),result.detail());
    if(preset==LodPreset.QUALITY){assertEquals(input.getKey(),result.packFile());unchanged(before);}else {assertNotEquals(input.getKey(),result.packFile());var opts=new Properties();try(var in=Files.newInputStream(dir.resolve(result.packFile()+".txt"))){opts.load(in);}assertEquals("false",opts.getProperty("TAA"));assertEquals("false",opts.getProperty("TAAU"));assertEquals("0.67",opts.getProperty("TAAU_RENDER_SCALE"));assertEquals("2",opts.getProperty("RP_MODE"));}
    var off=service.apply(dir,result.packFile(),false);assertTrue(off.accepted(),off.detail());assertEquals(input.getKey(),off.packFile());assertEquals(preferences,Files.readString(dir.resolve(input.getKey()+".txt")));assertArrayEquals(bytes,Files.readAllBytes(dir.resolve(input.getKey())));
   }
  }
 }
 @Test void explicitRendererWithAbsentSidecarCreatesNoAdapterStateOrOwnedOptions()throws Exception {
  System.setProperty(PackSelectionService.PHOTON_QUAD_ORIGINAL_GRAPH,"true");
  original("Photon.zip","dependencies/upstream/photon_v1.3b.zip");var before=snapshot();
  assertEquals(PackSelectionService.Status.UNCHANGED,new PackSelectionService().apply(dir,"Photon.zip",true,LodPreset.PERFORMANCE,Map.of(),true).status());unchanged(before);
 }
}
