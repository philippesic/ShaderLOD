package com.pp.shaderlod.optimization.sourcecache;

import com.google.common.collect.ImmutableList;
import com.pp.shaderlod.packs.CapturedRendererInputs;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executes pinned Iris discovery/include loading at the actual observer seams; no GL/client. */
class SourceDiscoveryRegressionTest {
 @TempDir Path temporary;
 static final class ObservedOptions extends ShaderPackOptions implements IrisCompiledSource.OptionsView {
  final IrisCompiledSource.OptionsInput input;
  ObservedOptions(IncludeGraph raw){super(raw,Map.of());input=IrisCompiledSource.OptionsInput.capture(this,raw);}
  public IrisCompiledSource.OptionsInput shaderlod$makeupOptionsInput(){return input;}
 }
 private Path shaders()throws Exception {
  var dir=temporary.resolve("loaded/shaders");Files.createDirectories(dir.resolve("world0"));
  Files.writeString(dir.resolve("world0/gbuffers_terrain.vsh"),"#version 150\nvoid main(){}\n");
  Files.writeString(dir.resolve("world0/gbuffers_terrain.fsh"),"#version 150\nvoid main(){}\n");
  Files.writeString(dir.resolve("world0/gbuffers_water.fsh"),"#version 150\nvoid main(){}\n");
  Files.writeString(dir.resolve("shaders.properties"),"oldLighting=false\n");
  Files.writeString(dir.resolve("block.properties"),"block.1=minecraft:stone\n");return dir;
 }
 private static IrisCompiledSource.PackInput load(Path shaders,Runnable afterBegin,Runnable afterDiscovery)throws Exception {
  var owner=new Object();IrisCompiledSource.begin(owner,shaders);afterBegin.run();
  var roots=ImmutableList.<AbsolutePackPath>builder();
  for(String name:List.of("/","/world0")) {
   var directory=AbsolutePackPath.fromAbsolutePath(name);var found=ImmutableList.<AbsolutePackPath>builder();
   boolean present=ShaderPackSourceNames.findPresentSources(found,shaders,directory,ShaderPackSourceNames.POTENTIAL_STARTS);
   var consumed=found.build();roots.addAll(consumed);
   IrisCompiledSource.discovered(directory,ShaderPackSourceNames.POTENTIAL_STARTS,consumed,present);
  }
  for(String directory:List.of("world0","world-1","world1"))IrisCompiledSource.existence(shaders.resolve(directory),Files.exists(shaders.resolve(directory)));
  // No includes in this fixture. Populate the nodes Iris discovered without initializing
  // its client/platform logger, which the public IncludeGraph constructor touches.
  var nodes=com.google.common.collect.ImmutableMap.<AbsolutePackPath,FileNode>builder();
  for(var root:roots.build())nodes.put(root,new FileNode(root,ImmutableList.copyOf(Files.readString(root.resolved(shaders)).split("\\R"))));
  var constructor=IncludeGraph.class.getDeclaredConstructor(com.google.common.collect.ImmutableMap.class,com.google.common.collect.ImmutableMap.class);constructor.setAccessible(true);
  var raw=constructor.newInstance(nodes.build(),com.google.common.collect.ImmutableMap.of());assertTrue(raw.getFailures().isEmpty());
  var options=new ObservedOptions(raw);
  String properties=null;
  for(String name:List.of("dimension.properties","shaders.properties","item.properties","entity.properties","block.properties")) {
   var path=shaders.resolve(name);String text=Files.exists(path)?Files.readString(path,StandardCharsets.ISO_8859_1):null;
   IrisCompiledSource.propertyRead(name,text);if(name.equals("shaders.properties"))properties=text;
  }
  afterDiscovery.run();IrisCompiledSource.properties(owner,options,List.of(),properties,null);
  return IrisCompiledSource.finish(owner,options,p->null,null,List.of());
 }
 private static void zip(Path shaders,Path archive)throws Exception {
  try(var out=new ZipOutputStream(Files.newOutputStream(archive));var paths=Files.walk(shaders)) {
   for(var path:paths.filter(Files::isRegularFile).toList()) {
    out.putNextEntry(new ZipEntry("shaders/"+shaders.relativize(path).toString()));out.write(Files.readAllBytes(path));out.closeEntry();
   }
  }
 }
 private static CapturedRendererInputs snapshot(Path shaders,Map<String,String> options)throws Exception {
  var members=new TreeMap<String,byte[]>();try(var paths=Files.walk(shaders)) {
   for(var path:paths.filter(Files::isRegularFile).toList())members.put("shaders/"+shaders.relativize(path),Files.readAllBytes(path));
  }
  var constructor=CapturedRendererInputs.class.getDeclaredConstructor(String.class,Map.class,Map.class);constructor.setAccessible(true);
  return constructor.newInstance("bsl-shaders",options,members);
 }
 @Test void positiveCompleteGraphAndMissingRestoredRootUseRealPinnedDiscovery()throws Exception {
  var shaders=shaders();var archive=temporary.resolve("certified.zip");zip(shaders,archive);
  var complete=load(shaders,()->{},()->{});assertNotNull(complete);
  assertTrue(IrisSourceLifecycle.matchesArchive(archive,complete));
  assertTrue(IrisCompiledSource.matchesRaw(snapshot(shaders,complete.options().values()),complete,"bsl-shaders"));
  var water=shaders.resolve("world0/gbuffers_water.fsh");var bytes=Files.readAllBytes(water);Files.delete(water);
  var missing=load(shaders,()->{},()->{});assertNotNull(missing,"An unchanged absent optional program is a valid loading observation");
  assertFalse(missing.options().captured().rawGraph().getNodes().containsKey(AbsolutePackPath.fromAbsolutePath("/world0/gbuffers_water.fsh")));
  var absentArchive=temporary.resolve("absent.zip");zip(shaders,absentArchive);
  assertTrue(IrisSourceLifecycle.matchesArchive(absentArchive,missing),"Legitimate absent/fallback inputs qualify their own archive");
  Files.write(water,bytes);
  assertFalse(IrisSourceLifecycle.matchesArchive(archive,missing),"Restoring a certified optional root before createPipeline cannot certify the earlier fallback graph");
  assertFalse(IrisCompiledSource.matchesRaw(snapshot(shaders,missing.options().values()),missing,"bsl-shaders"),"Publication uses the complete observed input inventory, not per-node subset equality");
  var fresh=load(shaders,()->{},()->{});assertNotNull(fresh);assertTrue(IrisSourceLifecycle.matchesArchive(archive,fresh));
  var epoch=new SourceProofEpoch();Object pipeline=new Object();try(var build=epoch.begin()){assertTrue(build.validate("source",()->true));assertTrue(build.publish(pipeline,()->true));}
  Files.delete(water);assertTrue(epoch.current(pipeline,"source"),"Post-link changes do not change compiled input identity");
 }
 @Test void transientDiscoveryAbsenceCannotHideBehindMatchingBeforeAfterSnapshots()throws Exception {
  var shaders=shaders();var water=shaders.resolve("world0/gbuffers_water.fsh");var bytes=Files.readAllBytes(water);
  assertNull(load(shaders,()->{try{Files.delete(water);}catch(Exception e){throw new RuntimeException(e);}},
   ()->{try{Files.write(water,bytes);}catch(Exception e){throw new RuntimeException(e);}}));
 }
 @Test void compileAffectingPropertyAndDimensionFallbackDecisionsAreObserved()throws Exception {
  var shaders=shaders();var before=IrisInputSnapshot.capture(shaders);
  assertTrue(before.property("block.properties",Files.readString(shaders.resolve("block.properties"))));
  assertFalse(before.property("block.properties",null));
  assertFalse(before.property("item.properties","item.1=minecraft:stone\n"));
  assertTrue(before.exists("world0",true));assertFalse(before.exists("world0",false));
  assertTrue(before.exists("world-1",false));assertFalse(before.exists("world-1",true));
  Files.writeString(shaders.resolve("block.properties"),"block.1=minecraft:dirt\n");assertFalse(before.unchanged(shaders));
  var archive=temporary.resolve("changed.zip");zip(shaders,archive);assertFalse(before.matchesArchive(archive));
 }
}
