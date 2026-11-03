package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class MakeUpEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("makeup-ultra-fast-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 private static final String[][] NAMED = {
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","a1a8c3b59e50ce2cfea5e62263632f60a9a93f44417b9d9086ed12882a2238ae"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","3f1f601034e85d6867b309af0cb42bf22390db526fab34a8a39c2992d26dd6e0"},
  {"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","cce7e3f0d104fac0eb25ea2930f43850879903980a27a234e2f8a1aa2b779e69"},
  {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","fb87dd2caf7f3170a446acf13c2fb6c88d16a20f6760dd9db54abd2cde920664"},
  {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","cdf0b40871447ce9be387c700298ea8616c8b462056a207aca6bc7ce7aa519c7"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","25f739da387b408d2aa1bf3b7c7b5275574a8d6f392f3f1ecb10345403e5aa15"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","8733904af0bc73c0c729a73f151cf1bdad81ae598f857c4af5cf284a01939cab"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","8a9736586a37082cf1ba21d742d20542662faabd1e97756e8bf9e06bff6605ac"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","7dc3d98604ad708efcb212f9b8300f204b20d7a81e8fb8f9942e069eb6bf12ba"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","f4212d5af7ca12027afff3dab58a943446db445dc7f642bcb20166c32d64d7df"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","c9098ede6d1e24c7f85c9bdad6e5a255d0bf1abcb94e3614aa8255fe8ff76b4d"},
  {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","43b1cb162ea7e6e8bd8e57a3277882b8daeec73ab957f16a97e8d9dbe1d5efaa"},
  {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","3a9559d2b20702c50695ce8da1c9d3d23b877ef3c5951312fea623878e34461f"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","414ef07e6511cf1aabd67cec292e9d27725acad740cbdc8015cc49f90f44f292"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","177f1d85dd944dcd46375221a088a077425b8138b4645da6e31aee02a1107df2"},
  {"net/irisshaders/iris/Iris.class","3fdc8ac841f047ed76a61210132833a1654e317926bae9ca33e0605756582997"},
  {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","3fed4986f6c5eaca3a63cb248e9b790a63cc8a65fd029720b495eb46cfc8cd99"},
  {"net/irisshaders/iris/mixin/MixinGpuTexture.class","38895a73ad093025f22222b084d707d65a4ed3c6d745d81dbd2cea9bec2df783"},
  {"net/irisshaders/iris/mixin/MixinGpuTexture2.class","4ebc9244d628320f11d6bed2ff504b0f6133eda00bef7cc349a84cb5b6c44b5d"}
 };
 private static final String[][] INTERMEDIARY = {
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","dc02ac12e10c5c2862ed2d2a719dcbdaab2212157b0893a30ece886e54e69f8c"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","a7373f714a37d4637259bed11509ec2e79c5cab9fbe3fe946a013f9edde5ef82"},
  {"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","cce7e3f0d104fac0eb25ea2930f43850879903980a27a234e2f8a1aa2b779e69"},
  {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","aef56955a29f09f893ff02ab62f18de6bbde7740f57ce539d17bec1abffcc86b"},
  {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","b5775aa6c802a8c358d9754d75f20c714d36d8bb111def98ed5295ecd650cda8"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","24314dc7828adad2330f54c48de14c5f1d0fcad1bd5efc328dd160e21f349c75"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","8733904af0bc73c0c729a73f151cf1bdad81ae598f857c4af5cf284a01939cab"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","8a9736586a37082cf1ba21d742d20542662faabd1e97756e8bf9e06bff6605ac"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","e302dea3baa6e5750bce38d72321e835475d84589d51aa0e5214025485708860"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","f4212d5af7ca12027afff3dab58a943446db445dc7f642bcb20166c32d64d7df"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","b41b371736edda913f735c37e01be07acadc11379a560a4ef6eac1ea81803c18"},
  {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","43b1cb162ea7e6e8bd8e57a3277882b8daeec73ab957f16a97e8d9dbe1d5efaa"},
  {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","ad5d0f46043984da95fac73b9e62e5ce41a12f1fb5376eba46e9555525581552"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","6b907ff387fe928561fed89b78c17503fddfd6a51adb092a39f83dd872ed9ba4"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","177f1d85dd944dcd46375221a088a077425b8138b4645da6e31aee02a1107df2"},
  {"net/irisshaders/iris/Iris.class","f29015f17a6e420fd5ec7e8fbad1d104a7141b24c19dbf73adf2f9f5edee66d1"},
  {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","3fed4986f6c5eaca3a63cb248e9b790a63cc8a65fd029720b495eb46cfc8cd99"},
  {"net/irisshaders/iris/mixin/MixinGpuTexture.class","e998d56ac3161d9cf70fb96a4a274fa5ea2101a2f9276763d881e2e83f12e186"},
  {"net/irisshaders/iris/mixin/MixinGpuTexture2.class","4ebc9244d628320f11d6bed2ff504b0f6133eda00bef7cc349a84cb5b6c44b5d"}
 };
 public static boolean exactApi(){ if(pinned!=null)return pinned; return pinned=resourcesMatch(MakeUpEarlyAlphaPlugin.class.getClassLoader()); }
 public static boolean resourcesMatch(ClassLoader loader){ return profileMatches(loader,NAMED)||profileMatches(loader,INTERMEDIARY); }
 private static boolean profileMatches(ClassLoader loader,String[][] profile){try{for(var pin:profile)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError e){return false;}}
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
