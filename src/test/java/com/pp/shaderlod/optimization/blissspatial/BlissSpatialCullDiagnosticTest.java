package com.pp.shaderlod.optimization.blissspatial;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class BlissSpatialCullDiagnosticTest {
 private static final String TARGET="com/mojang/blaze3d/vertex/VertexBuffer.class";
 private static final String NON_VERTEX="net/irisshaders/iris/pathways/FullScreenQuadRenderer.class";
 private static final ClassLoader OWNER=BlissSpatialCull.class.getClassLoader();
 private static final class ObservedLoader extends ClassLoader {
  final Map<String,Integer> reads=new TreeMap<>();final String failure,changedResource;
  ObservedLoader(String failure){this(failure,TARGET);}
  ObservedLoader(String failure,String changedResource){super(OWNER);this.failure=failure;this.changedResource=changedResource;}
  @Override public InputStream getResourceAsStream(String name){
   reads.merge(name,1,Integer::sum);
   if(!name.equals(changedResource))return super.getResourceAsStream(name);
   if(failure.equals("missing"))return null;
   if(failure.equals("error"))throw new SecurityException("diagnostic fixture denied exact resource");
   try(var in=super.getResourceAsStream(name)){
    assertNotNull(in);byte[] original=in.readAllBytes(),changed=original.clone();changed[changed.length/2]^=1;
    assertEquals(original.length,changed.length);return new ByteArrayInputStream(changed);
   }catch(IOException failure){throw new UncheckedIOException(failure);}
  }
 }
 private static BlissSpatialCull.ResourceReceipt target(BlissSpatialCull.Receipt receipt){
  return receipt.resources().stream().filter(r->r.resource().equals(TARGET)).findFirst().orElseThrow();
 }
 private enum Tuple { NEOFORGE, FABRIC }
 private static Tuple actualTuple(){
  boolean neoforge=OWNER.getResource("net/neoforged/fml/ModList.class")!=null;
  boolean fabric=OWNER.getResource("net/fabricmc/loader/api/FabricLoader.class")!=null;
  assertNotEquals(neoforge,fabric,"Expected exactly one authentic NeoForge or Fabric test classpath; unknown/mixed context refuses the fixture");
  return neoforge?Tuple.NEOFORGE:Tuple.FABRIC;
 }
 private static BlissSpatialCull.Receipt authenticBaseline(){
  var tuple=actualTuple();var receipt=BlissSpatialCull.inspect(OWNER);assertEquals(7,receipt.resources().size());
  for(var r:receipt.resources()){
   assertNotNull(r.actual(),r.toString());assertNotEquals("missing",r.location());
   // Bind each observation to actual classpath resource bytes independently of admission.
   try(var in=OWNER.getResourceAsStream(r.resource())){
    assertNotNull(in,r.resource());assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())),r.actual());
   }catch(Exception failure){throw new AssertionError("Authentic resource unavailable: "+r.resource(),failure);}
   assertTrue(Set.of("MATCH","MISMATCH").contains(r.outcome()),r.toString());
  }
  if(tuple==Tuple.NEOFORGE){
   // Mandatory positive proof for the exact original NeoForge seven-resource certificate.
   assertTrue(receipt.exact(),receipt.toString());assertEquals("neoforge-1.21.1-iris1.8.12-development",receipt.profile());
   for(var r:receipt.resources()){assertEquals("MATCH",r.outcome());assertEquals(r.expected(),r.actual());}
  }else{
   // Fabric's genuine Loom/Iris resources are not an admitted NeoForge tuple.
   assertFalse(receipt.exact(),"Fabric must refuse the existing NeoForge-only cull pins");assertTrue(receipt.profile().startsWith("unqualified;"));
   assertTrue(receipt.resources().stream().anyMatch(r->r.outcome().equals("MISMATCH")&&!r.expected().equals(r.actual())),receipt.toString());
   assertFalse(BlissSpatialCull.allowed(true,1029,2305,receipt.exact()));
  }
  return receipt;
 }
 private static void complete(ObservedLoader owner,BlissSpatialCull.Receipt receipt,BlissSpatialCull.Receipt baseline){
  assertEquals(7,receipt.resources().size());assertEquals(7,owner.reads.size());
  assertTrue(owner.reads.values().stream().allMatch(count->count==1));
  int unchanged=0;
  for(var r:receipt.resources())if(!r.resource().equals(owner.changedResource)){
   var original=baseline.resources().stream().filter(b->b.resource().equals(r.resource())).findFirst().orElseThrow();
   assertEquals(original,r,"Untouched actual tuple resource must retain hash/URL/outcome");unchanged++;
  }
  assertEquals(6,unchanged);assertFalse(receipt.exact());assertTrue(receipt.profile().startsWith("unqualified;"));
 }
 @Test void authenticLoaderTupleKeepsNeoForgePositiveAndFabricRefusal(){
  var receipt=authenticBaseline();
  assertThrows(UnsupportedOperationException.class,()->receipt.resources().clear());
  assertSame(BlissSpatialCull.status(),BlissSpatialCull.status());assertEquals(receipt.exact(),BlissSpatialCull.exact());
 }
 @Test void sameLengthMutationRetainsActualHashAndAllSevenObservations(){
  var baseline=authenticBaseline();var loader=new ObservedLoader("mutation");var receipt=BlissSpatialCull.inspect(loader);complete(loader,receipt,baseline);
  var changed=target(receipt);assertEquals("MISMATCH",changed.outcome());assertNotNull(changed.actual());assertNotEquals(changed.expected(),changed.actual());assertNotEquals(target(baseline).actual(),changed.actual());
  assertFalse(BlissSpatialCull.allowed(true,1029,2305,receipt.exact()));assertTrue(BlissSpatialCull.allowed(false,1029,2305,receipt.exact()));
 }
 @Test void missingResourceRetainsIdentityAndDoesNotHideOtherOutcomes(){
  var baseline=authenticBaseline();var loader=new ObservedLoader("missing");var receipt=BlissSpatialCull.inspect(loader);complete(loader,receipt,baseline);
  assertEquals("MISSING",target(receipt).outcome());assertNull(target(receipt).actual());assertNotNull(target(receipt).location());
 }
 @Test void resourceExceptionRetainsCauseAndDoesNotHideOtherOutcomes(){
  var baseline=authenticBaseline();var loader=new ObservedLoader("error");var receipt=BlissSpatialCull.inspect(loader);complete(loader,receipt,baseline);
  assertTrue(target(receipt).outcome().contains("SecurityException: diagnostic fixture denied exact resource"));assertNull(target(receipt).actual());
 }
 @Test void nonVertexTamperCannotMatchEitherCompleteProfile(){
  var baseline=authenticBaseline();var loader=new ObservedLoader("mutation",NON_VERTEX);var receipt=BlissSpatialCull.inspect(loader);complete(loader,receipt,baseline);
  var changed=receipt.resources().stream().filter(r->r.resource().equals(NON_VERTEX)).findFirst().orElseThrow();
  assertEquals("MISMATCH",changed.outcome());assertNotEquals(changed.expected(),changed.actual());
 }
 @Test void foreignClassResourceCannotQualifyAnUnknownMixedTuple(){
  authenticBaseline();
  var mixed=new ClassLoader(OWNER){
   @Override public InputStream getResourceAsStream(String name){
    // Authentic class bytes in the wrong member slot must never qualify a tuple.
    return super.getResourceAsStream(name.equals(NON_VERTEX)?"net/irisshaders/iris/pipeline/transform/transformer/CompositeTransformer.class":name);
   }
  };
  var receipt=BlissSpatialCull.inspect(mixed);assertFalse(receipt.exact());assertTrue(receipt.profile().startsWith("unqualified;"));assertEquals(7,receipt.resources().size());
  assertFalse(BlissSpatialCull.allowed(true,1029,2305,receipt.exact()));
 }
}
