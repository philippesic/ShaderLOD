package com.pp.shaderlod.optimization.reimaginedearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class ReimaginedEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("complementary-reimagined",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  return pinned=resourcesMatch(ReimaginedEarlyAlphaPlugin.class.getClassLoader());
 }
 public static boolean resourcesMatch(ClassLoader loader){
  String[][] pins={
   {"net/irisshaders/iris/shaderpack/programs/ProgramFallbackResolver.class","c3c005cb8615355b98aadb1699456eec1b3a89218d0e205c7bc7b3799d574e6c"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class","296ff722433acb3eab17f6c39e9c1c72838e923a4b995d043fd656b9a1f0fa0d"},
   {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class","0279856437aa28ce72b7f6a8249fc0be7837ec05a3c4d9e039a39981252f27ff"},
   {"net/irisshaders/iris/shaderpack/loading/ProgramId.class","e7916cf9a8a01ebf0ae84f096053018203952360356b8ab4dc3ba1c89f4b1589"},
   {"net/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms.class","6504040299eae0c8d4fca0e4f0097fd44444f0f18949bc9909f52a7a61648b22"},
   {"net/irisshaders/iris/Iris.class","089838deb5a7bf72af0bdfa5b02683c5ec0fb05ec2efb5ee70c8da2aca5528ba"},
   {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","5175b6dc1422df3450a142447a4e0570c7bfcd61ebeb12b05cacb4366fb4d2f5"},
   {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","59c59d44f12d3d7cbf805f86537d5289f48dd76b8c63577ae1a7fb127490fde5"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","ab941ca4eb29a84a35db555c7ac12355a0436798a2a2c665dc0aec486a9f6a65"},
   {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","9486c6aab670bcbda345ac79a5ca41d98ef40c2e02b3742eae3b328038c96373"},
   {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","dde8eb4bc65bc6da23f2031477b22c410cdd0bf13b70309fe5f8198b1e383717"},
   {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","9fb5e4c654496793f4cc1c0f8e385d3197fce0b336c7393060f008c744e22070"},
   {"net/irisshaders/iris/gl/blending/AlphaTest.class","be9da578ceb782547b7a60d753eca43cdf884fdb9bd68bade41b99978e1576dc"},
   {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","9347c4d399290619a11dadeb577c05576520ce46f626b004fd106b9b8edeb5a9"},
   {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","4292aa79a89fc8b8a826f851790fea0e16018732568d21cf3d814425b17d3968"},
   {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","3aab71b9829526194ea013520e0eefd66487b7387a946145f324a1fae54d0988"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","506a916a24242bd170a3d4106f69c86f4746be4adce241ea5c1ded92105fd4d6"},
   {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","a3273399bc8f444f9ee42806c6940e126453c543518123fc71b3076cf16fe797"},
   {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","94e4d8e715e8b05c1a04006e70aacfb23366679b09583f4c9792175aa19b9396"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","6701e8a1dc1ac984c473b932033271b1a900b402167dbc5581f2046d1d444a98"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","0f1f704f26d0045125c11ab7823a622920c8b8df6f359008daae2e43f16ebc5c"},
{"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","880133ee49f1c735845f9abdf597e46387762ead5c27280d0467ad50a97f6926"},
{"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","d8ec37130555f3e9670760fc06f94a0c93e268aee8d218df1ba91f7522586e9a"},
{"com/mojang/blaze3d/opengl/GlTexture.class","358d545fb069c12a3cef7ed83486dcaa63b5e249a8256094b9910ce391c8fb83"},
{"net/irisshaders/iris/shaderpack/properties/ShaderProperties.class","9234683ff8e66d6c12731e6637c5e83fc0c8dc3fcd793a51ed98d0edd8caf026"}};
  try{for(var pin:pins)try(var in=loader.getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;
  }return true;}catch(Exception|LinkageError e){return false;}
 }
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
