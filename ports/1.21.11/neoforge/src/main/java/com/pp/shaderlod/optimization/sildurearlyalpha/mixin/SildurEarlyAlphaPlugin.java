package com.pp.shaderlod.optimization.sildurearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class SildurEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("sildurs-vibrant-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  return pinned=resourcesMatch(SildurEarlyAlphaPlugin.class.getClassLoader());
 }
 public static boolean resourcesMatch(ClassLoader loader){
  String[][] pins={
   {"net/irisshaders/iris/shaderpack/programs/ProgramFallbackResolver.class","4c2fdeeb0938aa01385c30ace2baa9510da9c48b9ef0b736c832ebdf1c19a550"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class","a1d142aac769ad65f77761f5f8275c7f22972f2e967c09a19074433eea117893"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class","7d0ee9f3c134126775182f23ba085f97d1d3d28a78a2a5ee115413317f47b495"},
   {"net/irisshaders/iris/shaderpack/loading/ProgramId.class","0ffd004b711652a5b6f09f366c0af2e454e5ed1b5b32ef12923244a34176e2c1"},
   {"net/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms.class","0f12e9706269332947a490b291bd15ab436bca9cbb30d3f6167406a7b8160007"},
   {"net/irisshaders/iris/Iris.class","e6db6370696e8821da52651bec248602db30b9457e01a54fac3e4ec4452a012c"},
   {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","313783af9ce511c40fcb39d246c4322de0120c1031c0cfd2fe3685fdde7c71ca"},
   {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","7f2c1a6eed82fc15a730c0d2892eb91f5324f23a0e1f44defbd3ebc3f197a309"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","d6540e2616c288d319208368331f8779f024ef20ee09a29a06adadc587f537e5"},
   {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","a175cdffcd169b1c26062a8bdea9a9357515b8c403d78d5587cc8d769dbd05a6"},
   {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","09aacd566bb2d1585ea32561447c23567d44c82f12b9beb6a7c5f1d1246832b9"},
   {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","6b0376f8a15fb3823118c4afd7608cbc8eb5629dc82b1049dc45116cbbc6d262"},
   {"net/irisshaders/iris/gl/blending/AlphaTest.class","d64dc340271e3ec90d405175de1bb092719fd6aca5935fff771987e1d8a1535d"},
   {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","ddcfe64a531955b22dcccb3f957e0e372b5948cc3f291290cf39954948894a41"},
   {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","d738758d58b691d3e939041e45e898047843ebff72bc9bcadb26c9d6ff827662"},
   {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","7e6c737df927de1a7b0a1e99ee1f0b6605de8cb15f8135277817ad746e313272"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","a8c1cef1e051ad68c9d79b6998744635e2c6a62bd75d0ef41e0a8cd977d7c59a"},
   {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","2b502309e52bcff35fcfa91432422267a47af3b6ab06ace2bdd5afc87f5a3db4"},
   {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","02c54dff4a695ad6b233cafb95a0d6a095b829be901fab1a0734a790815844d2"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","300606c502a85baea1828a8ca74c2e7c1fce830686f34ce3f816f256c7864d06"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","d2972daef0042aaa0aca7bd4d366b62e3a6fd1e1a5a39007c628806cd9867197"}};
  try{for(var pin:pins)try(var in=loader.getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;
  }return true;}catch(Exception|LinkageError e){return false;}
 }
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
