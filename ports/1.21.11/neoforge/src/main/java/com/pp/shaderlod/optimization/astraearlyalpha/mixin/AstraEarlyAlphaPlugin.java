package com.pp.shaderlod.optimization.astraearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class AstraEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("astralex",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.astraearlyalpha.AstraEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","313783af9ce511c40fcb39d246c4322de0120c1031c0cfd2fe3685fdde7c71ca"},
{"net/irisshaders/iris/pipeline/CustomTextureManager.class","f710752db160aab405edd9c3dffd9c74f68bbd5fec8539f5bbfad3664e1871f6"},
{"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","8b965da3be1c00dad9a949ad8ebf44fdc0870036a05c9b53931dd1ca1f90871a"},
{"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","26c47dbfa97ec4584e7a8efe64fe6e44b8ab73302374b52add9e05caa93999a1"},
{"net/irisshaders/iris/gl/texture/TextureAccess.class","82b251c1c128ea334acfba6f0034a4a64915bdc4d84b1118c756fa8620e01c77"},
{"net/irisshaders/iris/gl/texture/TextureType.class","2f668a1092950ea2496870bf41aafeaa1d677941825bca164fefd120a1ffddc7"},
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
{"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","d2972daef0042aaa0aca7bd4d366b62e3a6fd1e1a5a39007c628806cd9867197"},
{"net/irisshaders/iris/shadows/ShadowRenderTargets.class","7f2c1a6eed82fc15a730c0d2892eb91f5324f23a0e1f44defbd3ebc3f197a309"},
{"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","a175cdffcd169b1c26062a8bdea9a9357515b8c403d78d5587cc8d769dbd05a6"},
{"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","d6540e2616c288d319208368331f8779f024ef20ee09a29a06adadc587f537e5"},
{"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","92144e8ed4a2a472c47b210161f6e9743ed28203b6c8dcfe86fb95f21b1cc134"},
{"net/irisshaders/iris/Iris.class","e6db6370696e8821da52651bec248602db30b9457e01a54fac3e4ec4452a012c"}};
  try{for(var pin:pins)try(var in=AstraEarlyAlphaPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
