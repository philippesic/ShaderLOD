package com.pp.shaderlod.optimization;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual four clone callers and their private cache holders; no GL context or game substitute. */
class DepthCloneConsumerStorageTest {
 private static final List<String> CONSUMERS=List.of(
  "bslspatial.BslSpatialRuntime","solasspatial.SolasSpatialRuntime",
  "reimaginedspatial.ReimaginedWorldPixelRuntime","unboundspatial.UnboundWorldPixelRuntime");
 private static final String ROOT="com/pp/shaderlod/optimization/",STORAGE=ROOT+"DepthTextureStorage$Storage";
 private static ClassNode node(String resource)throws Exception{
  try(var in=DepthCloneConsumerStorageTest.class.getClassLoader().getResourceAsStream(resource+".class")){
   assertNotNull(in,resource);var n=new ClassNode();new ClassReader(in.readAllBytes()).accept(n,0);return n;
  }
 }
 private static boolean call(MethodNode method,String owner,String name){
  for(var i:method.instructions)if(i instanceof MethodInsnNode m&&m.owner.equals(owner)&&m.name.equals(name))return true;
  return false;
 }
 @Test void compiledCaptureAndAllocationUseTheSharedDescriptorAtAllFourRealBoundaries()throws Exception{
  for(String consumer:CONSUMERS){
   String owner=ROOT+consumer.replace('.','/');var main=node(owner);var fields=node(owner+"$Fields");
   assertTrue(main.methods.stream().anyMatch(m->call(m,ROOT+"DepthTextureStorage","resolve")&&call(m,STORAGE,"equals")),consumer+" capture and cache must compare the complete observed storage");
   assertTrue(fields.fields.stream().anyMatch(f->f.name.equals("storage")&&f.desc.equals("L"+STORAGE+";")),consumer+" holder must retain typed storage");
   assertTrue(fields.methods.stream().anyMatch(m->m.name.equals("allocate")&&call(m,STORAGE,"privateFormat")),consumer+" allocation must use explicit sized format");
  }
 }
 private static Object holder(String consumer,DepthTextureStorage.Storage storage)throws Exception{
  var type=Class.forName("com.pp.shaderlod.optimization."+consumer+"$Fields",false,DepthCloneConsumerStorageTest.class.getClassLoader());
  Class<?> mode=consumer.contains("WorldPixel")?int[].class:boolean.class;
  var ctor=type.getDeclaredConstructor(int.class,int.class,DepthTextureStorage.Storage.class,mode);ctor.setAccessible(true);
  return ctor.newInstance(850,510,storage,mode==boolean.class?Boolean.TRUE:new int[]{0x8C3A,0x881B,0x8058});
 }
 private static DepthTextureStorage.Storage observed(Object holder)throws Exception{
  var field=holder.getClass().getDeclaredField("storage");assertTrue(Modifier.isFinal(field.getModifiers()));field.setAccessible(true);
  return (DepthTextureStorage.Storage)field.get(holder);
 }
 @Test void equalPrivateAllocationsCannotHideDifferentRawDepthCacheIdentities()throws Exception{
  var raw24=DepthTextureStorage.resolve(0x1902,24,0x8C17);var sized24=DepthTextureStorage.resolve(0x81A6,0,0);
  var rawFloat=DepthTextureStorage.resolve(0x1902,32,0x1406);var sizedFloat=DepthTextureStorage.resolve(0x8CAC,0,0);
  assertNotNull(raw24);assertNotNull(rawFloat);
  assertEquals(raw24.privateFormat(),sized24.privateFormat());assertNotEquals(raw24,sized24);
  assertEquals(rawFloat.privateFormat(),sizedFloat.privateFormat());assertNotEquals(rawFloat,sizedFloat);
  for(String consumer:CONSUMERS){
   assertSame(raw24,observed(holder(consumer,raw24)));assertSame(sized24,observed(holder(consumer,sized24)));
   assertNotEquals(observed(holder(consumer,raw24)),observed(holder(consumer,sized24)),consumer+" raw-to-sized mutation must invalidate cache identity");
   assertNotEquals(observed(holder(consumer,rawFloat)),observed(holder(consumer,sizedFloat)),consumer+" float raw-to-sized alias must invalidate cache identity");
   assertNotEquals(observed(holder(consumer,raw24)),observed(holder(consumer,DepthTextureStorage.resolve(0x1902,16,0x8C17))),consumer+" precision mutation must invalidate cache identity");
  }
 }
 @Test void typedUnsizedDonorRefusesUnknownPrecisionAndKeepsSizedDepthStencilCases(){
  for(int bits:new int[]{0,8,24,64})assertNull(DepthTextureStorage.resolve(0x1902,bits,0x1406));
  assertNull(DepthTextureStorage.resolve(0x1902,24,0));
  for(int raw:new int[]{0x81A5,0x81A6,0x81A7,0x8CAC,0x88F0,0x8CAD}){
   var storage=DepthTextureStorage.resolve(raw,0,0);assertNotNull(storage);assertEquals(raw,storage.privateFormat());
  }
 }
}
