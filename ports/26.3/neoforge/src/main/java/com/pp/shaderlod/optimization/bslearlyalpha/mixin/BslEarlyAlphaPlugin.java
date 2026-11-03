package com.pp.shaderlod.optimization.bslearlyalpha.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Private pinned API gate. Original arms have no new injections. */
public final class BslEarlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("bsl-shaders",com.pp.shaderlod.optimization.OptimizationId.EARLY_ALPHA,com.pp.shaderlod.optimization.bslearlyalpha.BslEarlyAlphaRuntime::status);} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 private static final String[][] PROFILE_0={
{"net/irisshaders/iris/Iris.class","edc6f8738ed79ac42756068613cbef937a2dea1996d2929148a3ca089ea36071"},
{"net/irisshaders/iris/pipeline/programs/ShaderCreator.class","4bcf5b6f9c83e2f5f30f4c525f44acf469f3ef5fe2326af2448e12bc74db32ea"},
{"net/irisshaders/iris/pipeline/programs/ShaderSupplier.class","7bce94de44c86cfb3609c2146fa369af8c707366468fb092214b7848bb241944"},
{"net/irisshaders/iris/pipeline/programs/PartialShader.class","6e21b705ecc994d1519ac900706baa419782b7759ebf9e5759f3af7715331a86"},
{"net/irisshaders/iris/pipeline/programs/ShaderKey.class","9d5636ac35ed0089d50bb227b28ccf6e0366f8a81ddda63a33ea8fb284399787"},
{"net/irisshaders/iris/pipeline/programs/ExtendedShader.class","cba0aaf079dc868a9385ecc050d04c79d567ffa9657f69e1d459e79f2dca03d9"},
{"net/irisshaders/iris/pipeline/programs/IrisProgram.class","a3f6457704034c695247fb80b67d2b3805d476f5ea2eb009be39391826fa035d"},
{"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","5a1e59fcf95570f16645b4581af1d0d10906411afb5a71933b15312501dc2248"},
{"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","11519cea90ba553ad5ef9498d15b6799333cdeb8a29e6e5c1a8f71bdfc105709"},
{"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","56fe400f3a955d27e832b5a0ffa657d69a239e87fc9b4612cc1f777ce507bbf9"},
{"net/irisshaders/iris/gl/blending/AlphaTest.class","be9da578ceb782547b7a60d753eca43cdf884fdb9bd68bade41b99978e1576dc"},
{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","53a667ae4114da83f10a19246898afb4ca5e2c1599595a53878559cfa03cee88"},
{"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","18d5e3da2d58d9af72e34aa08fae62961feb5814777cf0f9f05f7ee1c2b655fe"},
{"net/irisshaders/iris/mixinterface/GpuTextureInterface.class","9486c6aab670bcbda345ac79a5ca41d98ef40c2e02b3742eae3b328038c96373"},
{"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","a9fa7185be862eeb3ea5c43730f23ba561373e3edf881445bdd8ca93243149f1"},
{"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","900d06ef291b5d2ae2cb44056f94c07561d3abcb62de95d9d145eff5e801c659"},
{"net/irisshaders/iris/shadows/ShadowRenderTargets.class","302fefbae2c27a64ef0a9e164b4d112571ac4d6e78f748cc43f9c5634610c7a2"},
{"net/irisshaders/iris/uniforms/CapturedRenderingState.class","3aab71b9829526194ea013520e0eefd66487b7387a946145f324a1fae54d0988"},
{"net/minecraft/client/renderer/texture/AbstractTexture.class","2c74d06c5f40843af9568ac10044543d8e3f45de9f0f48b7748976c3f471933b"},
{"com/mojang/renderpearl/backend/opengl/GlTexture.class","ed7e65ae74460328cd85799797239d66b752deb4b3ec2491c0db49f8e7011c0d"},
{"com/mojang/renderpearl/backend/opengl/GlProgram.class","2b18c0f3ad6eb83e6b9185cdd0f8731f038db8e6e7731e473794a5e8f608f0f2"},
{"com/mojang/renderpearl/backend/opengl/GlCommandEncoder.class","51dd6827403a0b754a9e232dc0bebfb312906bafdd0c0a4469c27c97581bb69f"},
{"com/mojang/renderpearl/backend/opengl/GlRenderPass.class","7aa32235904d873d4c2d13161cdf30635e01f05bae922c6a8c30c1fa6927c9a6"},
{"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","d8f347e89b4b35eb30418ae5d6227703e8c87446c58ef1483d31ad5787e907b0"},
{"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e83545a59ede6438a5fb9e6f2615ff7bdea505b8acfbd3bbf1d34e2303134b55"},
{"META-INF/services/net.irisshaders.iris.platform.IrisPlatformHelpers","49bbd44003407993dc5063b01fda76dcdd6708dbe64df608cd018dd423a0fa41"},
{"net/irisshaders/iris/platform/IrisForgeHelpers.class","45110dc1313dbb2944968cc610cfdcc1e6f2d7eb2ac77f81b40d8c5102b5bde7"},
{"net/neoforged/fml/ModList.class","53684d9c0c0115671c80d60daaa851783f43e4294ab5dda823530ce2b436cd29"},
{"net/irisshaders/iris/mixin/MixinGpuTexture.class","112c7c841a19439b4ed207cfc34c4a2be913138e4c15162835567e7578e51431"},
{"net/irisshaders/iris/mixin/MixinGpuTexture2.class","5ee04ac8ab51e6817a390de0fb42d072ad80cc157e876ea8f97bb87db674b274"},
{"com/mojang/renderpearl/backend/opengl/GlRenderPipeline.class","58d1f1068c776af7c2dd25ee184dba156a03b046455ae421374a37c626fda7ad"},
{"net/irisshaders/iris/mixin/MixinGlRenderPipeline.class","e59b9de65af2dca385532f8c6862a72f8d581c958213cf08854518c197509321"},
{"net/irisshaders/iris/mixin/MixinGlCommandEncoder.class","3140d1616eb9bb9045e2e982c680e12df732e622e3176adce186432472f95ed4"},
{"net/irisshaders/iris/mixinterface/GlProgramBindings.class","9b3d3e9685da6a3b2e662b8257d062f2a1d3f24e262f2819e4e24f9abc9d6963"},
{"net/irisshaders/iris/mixin/MixinGlProgram.class","9755f5d63669c32ed44e0a7b69f3c5c1d1092673eacb31904ce2edaaff3b1dcb"},
{"net/irisshaders/iris/mixin/MixinGlProgram$1.class","12b26de265681dc935d52320934f0de21fbc10a98bd50c3548a7ff8270bece4c"}};
 private static boolean matches(ClassLoader loader,String[][] pins){try{for(var pin:pins)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError refused){return false;}}
 public static boolean resourcesMatch(ClassLoader loader){return matches(loader,PROFILE_0);}
 public static boolean exactApi(){if(pinned==null)pinned=resourcesMatch(BslEarlyAlphaPlugin.class.getClassLoader());return pinned;}
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
