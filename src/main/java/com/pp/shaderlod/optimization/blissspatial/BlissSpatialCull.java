package com.pp.shaderlod.optimization.blissspatial;
import java.util.*;import java.security.*;import org.lwjgl.opengl.GL11;
/** Native pinned CCW fullscreen triangles under the fixed positive composite transform. No GL state writes. */
public final class BlissSpatialCull {
 private static final Map<String,String> PINS=Map.of("net/irisshaders/iris/pathways/FullScreenQuadRenderer.class","7595d1408a17a4203af0c66b7d8a7f66c7f28e4a7b9ac7243893800e9d5a4d22","net/irisshaders/iris/pipeline/transform/transformer/CompositeTransformer.class","e67965a33e2c78ab57b95a2bfbcd118297fe2b0e7e530c5bc4d58482ca2f53fd","com/mojang/blaze3d/systems/RenderSystem.class","b84297aa8f2f8bc356a6e1b9288ced45ceba9437048913500f775d82fbec869d","com/mojang/blaze3d/vertex/VertexBuffer.class","e66ac367267b7a6e94989c9d4fc13326e2d871b92a306ebcf8a3286eb7ba8143","com/mojang/blaze3d/vertex/VertexFormat$Mode.class","578acd1f418502e46790854f1a7597a837fcfdaa944b3825bab0542d7ea5a134","net/irisshaders/iris/pipeline/transform/TransformPatcher.class","2cc6d66298b0694e1a03c50da198d4d59ad3f63759183909ded86703af36fc47","net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","f0acce6faba3fde7e4e72d60b647fd7a0a69e02916791fa1dc905237a2d77544");
 private static final String DEVELOPMENT="neoforge-1.21.1-iris1.8.12-development";
 private static final String PRODUCTION="neoforge-1.21.1-iris1.8.12-production";
 // Exact raw production VB preserves the winding law; full semantic review:
 // dev/logs/pc-prism-20261018/sildur-cull-production-r1/vertex-diff/semantic-comparison.json
 private static final Map<String,Map<String,String>> PROFILES=Map.of(DEVELOPMENT,PINS,PRODUCTION,productionPins());
 private static Map<String,String> productionPins(){
  var profile=new TreeMap<>(PINS);
  profile.put("com/mojang/blaze3d/vertex/VertexBuffer.class","b110771ef56680decac425f088e3acac4d78050fa6b40eb064bae788de5ed882");
  return Map.copyOf(profile);
 }
 private static final Receipt RECEIPT=inspect(BlissSpatialCull.class.getClassLoader());
 private static final String STATUS=RECEIPT.toString();
 private BlissSpatialCull(){}
 public static boolean exact(){return RECEIPT.exact();}
 /** Immutable first owner-loader observation; no resource I/O on draw/status calls. */
 public static String status(){return STATUS;}
 public static boolean allowed(boolean enabled,int mode,int front,boolean exactNativeQuad){return !enabled||(exactNativeQuad&&mode==GL11.GL_BACK&&front==GL11.GL_CCW);}
 record ResourceReceipt(String resource,String expected,String actual,String location,String outcome){}
 record Receipt(boolean exact,String profile,String loader,List<ResourceReceipt> resources){
  Receipt{resources=List.copyOf(resources);}
 }
 record ObservedResource(String resource,String actual,String location,String outcome){}
 private static boolean matches(Map<String,String> profile,List<ObservedResource> observations){
  return profile.size()==7&&observations.size()==7&&observations.stream().allMatch(r->r.outcome().equals("READ")&&Objects.equals(profile.get(r.resource()),r.actual()));
 }
 /** Read seven complete resources once; qualify only a complete named profile. */
 static Receipt inspect(ClassLoader owner){
  var observations=new ArrayList<ObservedResource>();
  String loader=owner==null?"bootstrap/null":owner.getClass().getName()+"@"+Integer.toHexString(System.identityHashCode(owner));
  for(String resource:new TreeSet<>(PINS.keySet())){
   String location="unavailable",actual=null,outcome;
   // URL evidence cannot change the existing full-byte admission law.
   try{var url=owner.getResource(resource);location=url==null?"missing":url.toExternalForm();}
   catch(Exception|LinkageError failure){location="error:"+failure;}
   try(var in=owner.getResourceAsStream(resource)){
    if(in==null)outcome="MISSING";
    else{actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));outcome="READ";}
   }catch(Exception|LinkageError failure){outcome="ERROR:"+failure;}
   observations.add(new ObservedResource(resource,actual,location,outcome));
  }
  String selected=null;
  for(var profile:new TreeMap<>(PROFILES).entrySet())if(matches(profile.getValue(),observations)){selected=profile.getKey();break;}
  boolean exact=selected!=null;
  var expected=exact?PROFILES.get(selected):PINS;
  var resources=new ArrayList<ResourceReceipt>();
  for(var r:observations){String outcome=r.outcome().equals("READ")?(expected.get(r.resource()).equals(r.actual())?"MATCH":"MISMATCH"):r.outcome();resources.add(new ResourceReceipt(r.resource(),expected.get(r.resource()),r.actual(),r.location(),outcome));}
  return new Receipt(exact,exact?selected:"unqualified; diagnostic reference="+DEVELOPMENT,loader,resources);
 }
}
