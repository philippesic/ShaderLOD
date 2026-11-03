package com.pp.shaderlod.optimization.sildurearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Exact pinned API gate. Original arms have no new injections. */
public final class SildurEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("sildurs-vibrant-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.sildurearlyalpha.SildurEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
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
  {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","43b1cb162ea7e6e8bd8e57a3277882b8daeec73ab957f16a97e8d9dbe1d5efaa"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","177f1d85dd944dcd46375221a088a077425b8138b4645da6e31aee02a1107df2"},
  {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","3fed4986f6c5eaca3a63cb248e9b790a63cc8a65fd029720b495eb46cfc8cd99"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","c9098ede6d1e24c7f85c9bdad6e5a255d0bf1abcb94e3614aa8255fe8ff76b4d"},
  {"net/irisshaders/iris/Iris.class","3fdc8ac841f047ed76a61210132833a1654e317926bae9ca33e0605756582997"},
  {"net/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms.class","d9871aef2e706555fd6e05f63107fc7d5ac9245297ea71f384bf240e70600306"},
  {"net/irisshaders/iris/shaderpack/loading/ProgramId.class","47849385424bdc6ab55be37a47847913351baacdd7b979b706f442cd9285224f"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class","85b2617ebde660ba3d800e76c8db41e2e54c520cac626f4b4e740209f10b1538"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class","298a15f80330d4d408fcc71811945da4c2c7fd0ba970dd0b217fed248f60fb3e"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramFallbackResolver.class","b6517233fb6590178fc153a8ec7c9b5c0a9d914b2fc5a64a57e183935381876e"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","3ddc19d582b11cd99efd90011663c2d3fed989e40196b6568b24c24e200dc92e"}
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
  {"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","43b1cb162ea7e6e8bd8e57a3277882b8daeec73ab957f16a97e8d9dbe1d5efaa"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","177f1d85dd944dcd46375221a088a077425b8138b4645da6e31aee02a1107df2"},
  {"net/irisshaders/iris/shadows/ShadowRenderTargets.class","3fed4986f6c5eaca3a63cb248e9b790a63cc8a65fd029720b495eb46cfc8cd99"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","b41b371736edda913f735c37e01be07acadc11379a560a4ef6eac1ea81803c18"},
  {"net/irisshaders/iris/Iris.class","f29015f17a6e420fd5ec7e8fbad1d104a7141b24c19dbf73adf2f9f5edee66d1"},
  {"net/irisshaders/iris/uniforms/builtin/BuiltinReplacementUniforms.class","d9871aef2e706555fd6e05f63107fc7d5ac9245297ea71f384bf240e70600306"},
  {"net/irisshaders/iris/shaderpack/loading/ProgramId.class","47849385424bdc6ab55be37a47847913351baacdd7b979b706f442cd9285224f"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramSet.class","85b2617ebde660ba3d800e76c8db41e2e54c520cac626f4b4e740209f10b1538"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramSource.class","298a15f80330d4d408fcc71811945da4c2c7fd0ba970dd0b217fed248f60fb3e"},
  {"net/irisshaders/iris/shaderpack/programs/ProgramFallbackResolver.class","b6517233fb6590178fc153a8ec7c9b5c0a9d914b2fc5a64a57e183935381876e"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","0baef141e4b9bd9679e7ce0d71d62ea1e3e2e16e29003076e62e274ad4d459fd"}
 };
 public static boolean exactApi(){ if(pinned!=null)return pinned; return pinned=resourcesMatch(SildurEarlyAlphaPlugin.class.getClassLoader()); }
 public static boolean resourcesMatch(ClassLoader loader){ return profileMatches(loader,NAMED)||profileMatches(loader,INTERMEDIARY); }
 private static boolean profileMatches(ClassLoader loader,String[][] profile){try{for(var pin:profile)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError e){return false;}}
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
