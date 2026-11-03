package com.pp.shaderlod.optimization.sildurao;

import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Queried-state value/ownership tests, without a GL context. Driver queries are a separate runtime obligation. */
class SildurAoMetadataTest {
 private static SildurAoGlProof.Binding image(){return new SildurAoGlProof.Binding(7,1920,1200,0x8051,0,0,0,0x2601,0x2601,0,0x812F,0x812F,1);}
 private static SildurAoGlProof.MaterialBinding material(){return new SildurAoGlProof.MaterialBinding(image(),0,1000,0x1903,0x1904,0x1905,0x1906,0x0203,0x812F,-1000,1000,0,0,0,0,0);}
 private static Object changed(Object record,int index)throws Exception {
  var fields=record.getClass().getRecordComponents();Object[] values=new Object[fields.length];Class<?>[] types=new Class<?>[fields.length];
  for(int i=0;i<fields.length;i++){types[i]=fields[i].getType();values[i]=fields[i].getAccessor().invoke(record);}
  if(types[index]==int.class)values[index]=(int)values[index]+1;
  else if(types[index]==float.class)values[index]=(float)values[index]+1;
  else values[index]=changed(values[index],0);
  return record.getClass().getDeclaredConstructor(types).newInstance(values);
 }
 @Test void everyCapturedImageAndSamplingStateMutationRefusesReuse()throws Exception {
  var before=material();assertTrue(SildurAoGlProof.sameMaterial(before,material()));
  assertFalse(SildurAoGlProof.sameMaterial(null,before));assertFalse(SildurAoGlProof.sameMaterial(before,null));
  for(int i=0;i<before.getClass().getRecordComponents().length;i++)assertFalse(SildurAoGlProof.sameMaterial(before,(SildurAoGlProof.MaterialBinding)changed(before,i)),"material state component "+i);
  for(int i=0;i<image().getClass().getRecordComponents().length;i++)assertNotEquals(image(),changed(image(),i),"image state component "+i);
 }
 private static SildurAoEpoch.Context context(long source){return new SildurAoEpoch.Context(PIPE,PACK,TARGET,source,4,1920,1200,3,5);}
 private static final Object PIPE=new Object(),PACK=new Object(),TARGET=new Object();
 @Test void completionBindingFailureEpochAndFrameWrapCannotAuthorizeStaleMetadata(){
  var epoch=new SildurAoEpoch();var c=context(1);var output=new SildurAoEpoch.Output(12,2,1920,1200);
  epoch.begin(c);assertFalse(epoch.consumer(c,12,true));assertTrue(epoch.start(c,output,true));assertFalse(epoch.consumer(c,12,true));
  assertFalse(epoch.returned(c,output,true,false));assertFalse(epoch.consumer(c,12,true));
  epoch.begin(c);assertTrue(epoch.start(c,output,true));assertTrue(epoch.returned(c,output,true,true));assertTrue(epoch.consumer(c,12,true));
  assertFalse(epoch.consumer(c,13,true));assertFalse(epoch.consumer(context(2),12,true));
  // Begin is independent of shader frameCounter: a repeated/wrapped frame value cannot reuse prior completion.
  epoch.begin(c);assertFalse(epoch.consumer(c,12,true));epoch.revoke();epoch.begin(c);assertFalse(epoch.start(c,output,true));
 }
 @Test void halfMetadataRefusesShiftedSpatialJoinsWhileGraphZeroKeepsItsContract(){
  assertTrue(SildurAoContract.options(SildurAoContract.generated("1")));assertTrue(SildurAoContract.metadataRoute(SildurAoContract.generated("1")));
  for(String key:List.of("SHADERLOD_SILDUR_SPATIAL_REUSE","SHADERLOD_SILDUR_LIGHTING_RATE")){
   var options=new TreeMap<>(SildurAoContract.generated("1"));options.put(key,"1");assertFalse(SildurAoContract.options(options));
   options.put(SildurAoContract.OPTION,"0");assertTrue(SildurAoContract.options(options));assertFalse(SildurAoContract.metadataRoute(options));
  }
 }
 @Test void compositeExitDropsMetadataOwnership()throws Exception {
  var normal=SildurAoRuntime.class.getDeclaredField("producerNormal");var material=SildurAoRuntime.class.getDeclaredField("producerMaterial");normal.setAccessible(true);material.setAccessible(true);
  normal.set(null,image());material.set(null,material());SildurAoRuntime.endComposite();assertNull(normal.get(null));assertNull(material.get(null));
  var saved=new LinkedHashMap<Field,Object>();
  for(String name:List.of("pending","retryBlocked","blockedGeneration","failure")){var field=SildurAoRuntime.class.getDeclaredField(name);field.setAccessible(true);saved.put(field,field.get(null));}
  var epochField=SildurAoRuntime.class.getDeclaredField("EPOCH");epochField.setAccessible(true);var epoch=(SildurAoEpoch)epochField.get(null);boolean revoked=epoch.revoked();
  try{for(String name:List.of("disabled","lose")){
   normal.set(null,image());material.set(null,material());var exit=SildurAoRuntime.class.getDeclaredMethod(name,String.class);exit.setAccessible(true);exit.invoke(null,"scripted metadata ownership failure");assertNull(normal.get(null));assertNull(material.get(null));
  }}finally{for(var entry:saved.entrySet())entry.getKey().set(null,entry.getValue());if(!revoked)epoch.requalified();}
 }
}
