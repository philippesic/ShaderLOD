package com.pp.shaderlod.optimization.sourcecache;

import com.google.common.collect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LoadedSourceInputsTest {
 @TempDir Path directory;
 private static IncludeGraph graph(String text)throws Exception {
  var path=AbsolutePackPath.fromAbsolutePath("/gbuffers_terrain.fsh");
  var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
  return constructor.newInstance(ImmutableMap.of(path,new FileNode(path,ImmutableList.copyOf(text.split("\\R")))),ImmutableMap.of());
 }
 private IrisCompiledSource.PackInput input(String text,String property)throws Exception {
  var raw=graph(text);var options=new ShaderPackOptions(raw,Map.of());
  var shaders=directory.resolve("observed/shaders");Files.createDirectories(shaders);
  Files.writeString(shaders.resolve("gbuffers_terrain.fsh"),text);Files.writeString(shaders.resolve("shaders.properties"),property,StandardCharsets.ISO_8859_1);
  return new IrisCompiledSource.PackInput(IrisCompiledSource.OptionsInput.capture(options,raw),p->null,null,List.of(),List.of(),Map.of("shaders.properties",property),IrisInputSnapshot.capture(shaders));
 }
 private static void zip(Path archive,String text,String property)throws Exception {
  try(var out=new ZipOutputStream(Files.newOutputStream(archive))){
   for(var entry:Map.of("shaders/gbuffers_terrain.fsh",text.getBytes(StandardCharsets.UTF_8),"shaders/shaders.properties",property.getBytes(StandardCharsets.ISO_8859_1)).entrySet()){
    var member=new ZipEntry(entry.getKey());var crc=new CRC32();crc.update(entry.getValue());member.setMethod(ZipEntry.STORED);member.setSize(entry.getValue().length);member.setCrc(crc.getValue());member.setTime(0);out.putNextEntry(member);out.write(entry.getValue());out.closeEntry();
   }
  }
 }
 @Test void sameStatReplacementCannotQualifyOldObservedInputsAndNewLoadUsesNewBytes()throws Exception {
  String before="#version 150\nvoid main(){int value=1;}\n",after=before.replace("value=1","value=2");
  var archive=directory.resolve("selected.zip");zip(archive,before,"#é\n");var observed=input(before,"#é\n");
  assertTrue(IrisSourceLifecycle.matchesArchive(archive,observed));
  var lifetime=new SourceProofEpoch();var linkedOwner=new Object();try(var load=lifetime.begin()){assertTrue(load.validate("source",()->true));assertTrue(load.publish(linkedOwner,()->true));}
  long size=Files.size(archive);var time=Files.getLastModifiedTime(archive);
  zip(archive,after,"#é\n");Files.setLastModifiedTime(archive,time);assertEquals(size,Files.size(archive));assertEquals(time,Files.getLastModifiedTime(archive));
  assertTrue(lifetime.current(linkedOwner,"source"),"Disk replacement cannot change the immutable inputs of an already linked owner");
  assertFalse(IrisSourceLifecycle.matchesArchive(archive,observed),"Same-stat changed bytes are checked against what Iris actually loaded");
  assertTrue(IrisSourceLifecycle.matchesArchive(archive,input(after,"#é\n")),"A fresh loading observation qualifies its own bytes");
  zip(archive,after,"#à\n");Files.setLastModifiedTime(archive,time);assertFalse(IrisSourceLifecycle.matchesArchive(archive,input(after,"#é\n")),"Actual properties are part of the consumed graph");
 }
 @Test void constructorSnapshotReusesInventoryAndRejectsLiveOverrideChangesUntilFreshCapture()throws Exception {
  var raw=graph("#define QUALITY 2 // [1 2 3]\nvoid main(){int q=QUALITY;}\n");var options=new ShaderPackOptions(raw,Map.of());
  assertTrue(EffectiveOptions.current(options).isEmpty(),"No lazy inventory construction on a rendering callback");
  EffectiveOptions.capture(options);var first=EffectiveOptions.current(options);assertEquals("2",first.get("QUALITY"));
  for(int i=0;i<1000;i++){assertSame(first,EffectiveOptions.current(options));assertSame(first,EffectiveOptions.current(options.getOptionSet(),options.getOptionValues()));}
  ((MutableOptionValues)options.getOptionValues()).getStringValues().put("QUALITY","3");assertTrue(EffectiveOptions.current(options).isEmpty());
  EffectiveOptions.capture(options);assertEquals("3",EffectiveOptions.current(options).get("QUALITY"));assertNotSame(first,EffectiveOptions.current(options));
 }
}
