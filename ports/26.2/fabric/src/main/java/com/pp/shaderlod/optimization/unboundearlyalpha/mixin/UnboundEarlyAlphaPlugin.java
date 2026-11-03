package com.pp.shaderlod.optimization.unboundearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class UnboundEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("complementary-unbound",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 private static final String[][] PROFILE_0={
{"net/irisshaders/iris/Iris.class","8056f6f84efebd10f5367bff0ace4d7659641445552bf051d6f0ed56055560a7"},
{"net/irisshaders/iris/pipeline/programs/ShaderCreator.class","a1d78ea02b98d9eb9d17b64849e6909109abbb064ed9ac38cdf278afbb4dbacd"},
{"net/irisshaders/iris/pipeline/programs/ShaderSupplier.class","e9c968a564c057a900b0c441a2b914d545634247d929b6d167f7350a62861095"},
{"net/irisshaders/iris/pipeline/programs/PartialShader.class","c7402a5cae5c8c25b364ecb525df9a15ee0e5e90d75fede895868992befaf5f2"},
{"net/irisshaders/iris/pipeline/programs/ShaderKey.class","06fa8b18412a0c6b5dc75b38db3a08f8fd8b1cec8f5edbf1b445884fb6389322"},
{"net/irisshaders/iris/pipeline/programs/ExtendedShader.class","9a6ccfbed975d5562bbdf1d8fc38bc74ef90757ece69e847b055c61031b640cc"},
{"net/irisshaders/iris/pipeline/programs/IrisProgram.class","e7876aa84409005a86bee60c2d34d4b159152dc301a449559baa25e36cbc82b2"},
{"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","101fb251c68034e4c8346de1c550616059d896dbfbb3fae56e5982a89877c25f"},
{"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","06f5c63c3d50c07a364164745d1cfc038cf28fa4157a5323726a578b4fc977be"},
{"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","24e6c1d4fa9efd20075b039e50ba1cf18cf5b7d1eba9321d0d2f40800ffc98a6"},
{"net/irisshaders/iris/gl/blending/AlphaTest.class","be9da578ceb782547b7a60d753eca43cdf884fdb9bd68bade41b99978e1576dc"},
{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","bc9352887ef09159c4680b759b498c7bc80531660b4f4ac893f3f060392064c4"},
{"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","0f06c5ce42bbf22dea0f762110b3069b8339f8ace204020f568a0bf4862c5f50"},
{"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","9486c6aab670bcbda345ac79a5ca41d98ef40c2e02b3742eae3b328038c96373"},
{"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","4446c9db570d3b4b18e40c2a9224cac846f0aaf8c17dbc02c3525e059e915419"},
{"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","900d06ef291b5d2ae2cb44056f94c07561d3abcb62de95d9d145eff5e801c659"},
{"net/irisshaders/iris/shadows/ShadowRenderTargets.class","01e611523041352fb73e118f6c83b086cb917f50eea9f011b1c14ce641855b1c"},
{"net/irisshaders/iris/uniforms/CapturedRenderingState.class","3aab71b9829526194ea013520e0eefd66487b7387a946145f324a1fae54d0988"},
{"net/minecraft/client/renderer/texture/AbstractTexture.class","6ce4428ffa049c588b68eb1c956148fd55698098a289c2acbd61fb283c68878b"},
{"com/mojang/blaze3d/opengl/GlTexture.class","c0175ed557cec629aa98cd5ce8d64219f7b7818f2b78571cfb763433f185c223"},
{"com/mojang/blaze3d/opengl/GlProgram.class","84d660a3416547e641b8d1eb0a84a4ea7610acd4594266535c06ef86043f52aa"},
{"com/mojang/blaze3d/opengl/GlCommandEncoder.class","16359d35b89008f6a90691c44e2fce4c1c01390d107a2dd42c9852594b9d0054"},
{"com/mojang/blaze3d/opengl/GlRenderPass.class","860b03f274462526634a2021c9b521cb17fe5991e9388b7b3d94753d05d1d6c2"},
{"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","caa9e3ca49377e601a074d25da47e450d4458ca26a0ad2d9190312c87b1f1ecd"},
{"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","0488fed4e8c51431266ede394c2e02f3ddd867719d5fa02bac92577609e119b4"},
{"META-INF/services/net.irisshaders.iris.platform.IrisPlatformHelpers","0a755de418e4936575f074dbdf764c72eba2878e3a11548d03711aea525df552"},
{"net/irisshaders/iris/platform/IrisFabricHelpers.class","373cba17e81c0f1c7236df12eb08c2d560bb12a8d7e9a30d63c859b42bea51f3"},
{"net/fabricmc/loader/api/FabricLoader.class","e230ea2a1398a859c0c55a351ea221f02bf16f619792aab2234ec303223b25ee"},
{"net/irisshaders/iris/mixin/MixinGpuTexture.class","8b850666cd239b272d1088becf1d5d8355e83f68646a98dafeba523eaf4f9125"},
{"net/irisshaders/iris/mixin/MixinGpuTexture2.class","41c7f1b865117f14be24b46695262a3a228fb2670190f6c02f327bc1bc8d6aa5"}};
 private static final String[][] PROFILE_1={
{"net/irisshaders/iris/Iris.class","8056f6f84efebd10f5367bff0ace4d7659641445552bf051d6f0ed56055560a7"},
{"net/irisshaders/iris/pipeline/programs/ShaderCreator.class","a1d78ea02b98d9eb9d17b64849e6909109abbb064ed9ac38cdf278afbb4dbacd"},
{"net/irisshaders/iris/pipeline/programs/ShaderSupplier.class","e9c968a564c057a900b0c441a2b914d545634247d929b6d167f7350a62861095"},
{"net/irisshaders/iris/pipeline/programs/PartialShader.class","c7402a5cae5c8c25b364ecb525df9a15ee0e5e90d75fede895868992befaf5f2"},
{"net/irisshaders/iris/pipeline/programs/ShaderKey.class","06fa8b18412a0c6b5dc75b38db3a08f8fd8b1cec8f5edbf1b445884fb6389322"},
{"net/irisshaders/iris/pipeline/programs/ExtendedShader.class","9a6ccfbed975d5562bbdf1d8fc38bc74ef90757ece69e847b055c61031b640cc"},
{"net/irisshaders/iris/pipeline/programs/IrisProgram.class","e7876aa84409005a86bee60c2d34d4b159152dc301a449559baa25e36cbc82b2"},
{"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","101fb251c68034e4c8346de1c550616059d896dbfbb3fae56e5982a89877c25f"},
{"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","06f5c63c3d50c07a364164745d1cfc038cf28fa4157a5323726a578b4fc977be"},
{"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","24e6c1d4fa9efd20075b039e50ba1cf18cf5b7d1eba9321d0d2f40800ffc98a6"},
{"net/irisshaders/iris/gl/blending/AlphaTest.class","be9da578ceb782547b7a60d753eca43cdf884fdb9bd68bade41b99978e1576dc"},
{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","bc9352887ef09159c4680b759b498c7bc80531660b4f4ac893f3f060392064c4"},
{"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","0f06c5ce42bbf22dea0f762110b3069b8339f8ace204020f568a0bf4862c5f50"},
{"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","9486c6aab670bcbda345ac79a5ca41d98ef40c2e02b3742eae3b328038c96373"},
{"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","4446c9db570d3b4b18e40c2a9224cac846f0aaf8c17dbc02c3525e059e915419"},
{"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","900d06ef291b5d2ae2cb44056f94c07561d3abcb62de95d9d145eff5e801c659"},
{"net/irisshaders/iris/shadows/ShadowRenderTargets.class","01e611523041352fb73e118f6c83b086cb917f50eea9f011b1c14ce641855b1c"},
{"net/irisshaders/iris/uniforms/CapturedRenderingState.class","3aab71b9829526194ea013520e0eefd66487b7387a946145f324a1fae54d0988"},
{"net/minecraft/client/renderer/texture/AbstractTexture.class","80aa67a59918a6f5dbb3ec7f75cec9b2546f943e31eb75206f14a0b301a87789"},
{"com/mojang/blaze3d/opengl/GlTexture.class","f1d1beb537afe01511e9c609b30eaace118954ec500587a3f5443adf1f5701ce"},
{"com/mojang/blaze3d/opengl/GlProgram.class","53e2f905beef1fd2dfcac2a2a021a9e0e218a09bcf6be0550dde260b74f8aa66"},
{"com/mojang/blaze3d/opengl/GlCommandEncoder.class","817def104e86530ea2ec7eb6d23eaf4ade18e8ffbb6f5a551db47cecceb890ed"},
{"com/mojang/blaze3d/opengl/GlRenderPass.class","7ad9eed06cc24d3a5c8db45a57d9bb6691cf354b07774036316046cb3acc10fa"},
{"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","caa9e3ca49377e601a074d25da47e450d4458ca26a0ad2d9190312c87b1f1ecd"},
{"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","0488fed4e8c51431266ede394c2e02f3ddd867719d5fa02bac92577609e119b4"},
{"META-INF/services/net.irisshaders.iris.platform.IrisPlatformHelpers","0a755de418e4936575f074dbdf764c72eba2878e3a11548d03711aea525df552"},
{"net/irisshaders/iris/platform/IrisFabricHelpers.class","373cba17e81c0f1c7236df12eb08c2d560bb12a8d7e9a30d63c859b42bea51f3"},
{"net/fabricmc/loader/api/FabricLoader.class","e230ea2a1398a859c0c55a351ea221f02bf16f619792aab2234ec303223b25ee"},
{"net/irisshaders/iris/mixin/MixinGpuTexture.class","8b850666cd239b272d1088becf1d5d8355e83f68646a98dafeba523eaf4f9125"},
{"net/irisshaders/iris/mixin/MixinGpuTexture2.class","41c7f1b865117f14be24b46695262a3a228fb2670190f6c02f327bc1bc8d6aa5"}};
 private static boolean matches(ClassLoader loader,String[][] pins){try{for(var pin:pins)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError refused){return false;}}
 public static boolean resourcesMatch(ClassLoader loader){return matches(loader,PROFILE_0)||matches(loader,PROFILE_1);}
 public static boolean exactApi(){if(pinned==null)pinned=resourcesMatch(UnboundEarlyAlphaPlugin.class.getClassLoader());return pinned;}
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
