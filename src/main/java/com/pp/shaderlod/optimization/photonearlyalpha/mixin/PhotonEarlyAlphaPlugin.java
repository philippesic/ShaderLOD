package com.pp.shaderlod.optimization.photonearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class PhotonEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("photon",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.photonearlyalpha.PhotonEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
   {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
   {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
   {"net/irisshaders/iris/gl/blending/AlphaTest.class","d64dc340271e3ec90d405175de1bb092719fd6aca5935fff771987e1d8a1535d"},
   {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","f0acce6faba3fde7e4e72d60b647fd7a0a69e02916791fa1dc905237a2d77544"},
   {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","cb9a9c34a1406469446259ae5d092326af0902f3e7199bab9cc39e8869e0dc33"},
   {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","b2de15e6beb4a9fcabba560ad87c9c203fd60715754a3959bc0ea3a7e5641b9a"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","92fc47a24254bc13083a9ab39423446a6345ec7f0ec646b435849c82da34dbf1"},
   {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","2cc6d66298b0694e1a03c50da198d4d59ad3f63759183909ded86703af36fc47"},
   {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","17bbfb7cc7bd5da043f508924c6485c2bc550a123dad94165cc033610803f8ae"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","e73940f5ba1cc7aec6c99aa5926ec7ac7bc073193dc073ebeb2caf5d0ac712b5"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","5d37e29205c73c7623ad727372071e0558cdc6c0cf709ae7b6669c5ac4951ab0"}};
  try{for(var pin:pins)try(var in=PhotonEarlyAlphaPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){if(m.endsWith("PhotonAlphaDrawMixin") && com.pp.shaderlod.optimization.nativechain.NativeChainAdmission.useReplacement()) return false;return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
