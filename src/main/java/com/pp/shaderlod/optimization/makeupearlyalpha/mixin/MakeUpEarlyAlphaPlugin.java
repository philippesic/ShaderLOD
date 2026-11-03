package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Observers preserve native behavior; draw admission remains guarded. */
public final class MakeUpEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("makeup-ultra-fast-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
   {"net/caffeinemc/mods/sodium/client/gl/GlObject.class","f1894a8169341fa22fd3823ddc79056a8775bf91957716fdfb72392e53ad5ba5"},
   {"net/irisshaders/iris/shaderpack/ShaderPack.class","604f775da6a84109950941227c77ec17c0a90db11a7b2de11b0bdae5181c0ef9"},
   {"net/irisshaders/iris/shaderpack/option/ShaderPackOptions.class","a6126e1e10013467efd930676756e1fd883f331217bfffd184a5ddb4670334c3"},
   {"net/irisshaders/iris/shaderpack/include/IncludeGraph.class","05a65c8ae0be4ecb3a12ae907123a57195a5495401d997764cba416d225b92fc"},
   {"net/irisshaders/iris/shaderpack/include/IncludeProcessor.class","3fa642aa4b134a99626ea5e5223f1221604c371d13b6b3d6ade1649767e928a3"},
   {"net/irisshaders/iris/shaderpack/preprocessor/JcppProcessor.class","32bf89b92d56000a63b03ddbf89107ddfb26de7c0d6287ff98cb70f4ee4e5a10"},
   {"net/irisshaders/iris/shaderpack/properties/ShaderProperties.class","1cf6ddb0622853cbabdab6eda3461ba72abbe61e391be83e22f550e079e8551c"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class","7d0ee9f3c134126775182f23ba085f97d1d3d28a78a2a5ee115413317f47b495"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class","a1d142aac769ad65f77761f5f8275c7f22972f2e967c09a19074433eea117893"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class","80e9edbf32bf34171bdafcfb94e79a3c764a11b81d0a1183788220c64b8b7758"},
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
  try{for(var pin:pins)try(var in=MakeUpEarlyAlphaPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){if(m.endsWith("MakeUpAlphaDrawMixin") && com.pp.shaderlod.optimization.nativechain.NativeChainAdmission.useReplacement()) return false;return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
