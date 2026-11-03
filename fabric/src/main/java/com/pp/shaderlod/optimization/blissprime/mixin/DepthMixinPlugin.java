package com.pp.shaderlod.optimization.blissprime.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Gate before touching renderer targets. Inactive/original arms have no injected draw code. */
public final class DepthMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){if(exactApi())com.pp.shaderlod.optimization.blissprime.BlissDepthPriming.install();} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  return pinned=resourcesMatch(DepthMixinPlugin.class.getClassLoader());
 }
 public static boolean resourcesMatch(ClassLoader loader){return profileMatches(loader,NAMED)||profileMatches(loader,INTERMEDIARY);}
 private static boolean profileMatches(ClassLoader loader,String[][] pins){try{for(var pin:pins)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError unavailable){return false;}}
 private static final String[][] NAMED={
  {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","59216b6708c1d82a8d7f54540fecf73782b2448811f7e825c9f16e3e4b5cc7ea"},
  {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","a22e4aa2d1bdc870ddf9241da83d35361ed0a11d6ba995b36e97621f378b50bc"},
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","4ba26f97a2a9006d3730962e3c7eb26f620c50cc7d98d82b6235720eac4753fb"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","4b358dd81cb7a1920b1c2bc9745c9cb3941f43a074ecda718e6f6b1fc9805749"},
  {"net/irisshaders/iris/pipeline/transform/PatchShaderType.class","60675a6d51995a13f7b2e9411aba90c05527e9aa09068c003e56043e3a38eb87"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","bdbb09a1e8c7f17cbf34b5a4a839e99d072201e373f313c44f7dd4ad1d51b145"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","eddce1de800044da5e38aa6eba30296f84b06840944520e551ef7929e91df74e"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","eea6e6cdbca25786177fd69eba632643df6691afe17257f6cbe580351393f3b4"},
  {"net/irisshaders/iris/shaderpack/properties/ProgramDirectives.class","ab81d4637bf35456c23df076eb9cd106d8db66d21cefed547dbed0ed7c4ccddd"},
  {"net/irisshaders/iris/gl/framebuffer/GlFramebuffer.class","c03428ca0d0d6ffdda086075e2e8b0e8fda5ac488fecd59c9ffd63bfd68f3fd7"},
  {"net/irisshaders/iris/gl/IrisRenderSystem.class","991b9d2adc856c6f9eae53516b4f1e4802a137f0e48c80bb3ec9fdfb667dc311"},
  {"net/irisshaders/iris/gl/blending/BlendModeOverride.class","1ab6ff001562d3aaa97b4129a58e3ed7d74728425cf562bac21082428785b505"},
{"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/gl/blending/AlphaTests.class","27d245913eb8bf6f13137fed144de26c281155feb99d46c135d92482e4ee2d66"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","571f06982ae05c65ff68b263dde5a2f514d1ffa31276b7dd9c29aff82a2550ab"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","b27a699fb3a88333dcd134d1dc89a10768b19667180d0404ec94ea3ce24066a2"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","a553eca23bd0ead8582028e057426e80aac26ae7de8444422a7ec7f3a2498693"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","d2e007f9d6d57013183ece2feb18a63b1009d18aaa4baf120e47527214154973"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class","2d602a90b6ee0619cb33911847c3eeb76d5d13dd04e4dd702b8f06556a3bce72"},
  {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","b155dbeeebad99b1fac87103816529383eb256b1fb650e59ba5c825687b7acfa"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","12b529949ab46fbd8c42b3b29c0e8bc470b6e8de9476f65c98b679264fb13142"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","f37739868b818f2603dce56e741f660d0b4f4b4f032465d48b7c8c39d6fcb263"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","543067aaad4089ab2720b7e5c1a8855d5f6a12751d87e1e31c6077488076931f"},
 };
 private static final String[][] INTERMEDIARY={
  {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","59216b6708c1d82a8d7f54540fecf73782b2448811f7e825c9f16e3e4b5cc7ea"},
  {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","a22e4aa2d1bdc870ddf9241da83d35361ed0a11d6ba995b36e97621f378b50bc"},
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","7f49c1da2e12807f0680ca894f631c104b2a8846492e4c116014db11dc3e29b6"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","043308b62b7c6feb3626282a8c97e25de83403294871a7668367e43aff51c530"},
  {"net/irisshaders/iris/pipeline/transform/PatchShaderType.class","60675a6d51995a13f7b2e9411aba90c05527e9aa09068c003e56043e3a38eb87"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","bdbb09a1e8c7f17cbf34b5a4a839e99d072201e373f313c44f7dd4ad1d51b145"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","eddce1de800044da5e38aa6eba30296f84b06840944520e551ef7929e91df74e"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","006906a07a47313efc873289f9f59de5c2ec2030ed85ffaca39491916e8ab91e"},
  {"net/irisshaders/iris/shaderpack/properties/ProgramDirectives.class","ab81d4637bf35456c23df076eb9cd106d8db66d21cefed547dbed0ed7c4ccddd"},
  {"net/irisshaders/iris/gl/framebuffer/GlFramebuffer.class","c03428ca0d0d6ffdda086075e2e8b0e8fda5ac488fecd59c9ffd63bfd68f3fd7"},
  {"net/irisshaders/iris/gl/IrisRenderSystem.class","d2e258108265e58c30cd82510ff5e67f2ed5863ddffe5009d439788a3817c90e"},
  {"net/irisshaders/iris/gl/blending/BlendModeOverride.class","1ab6ff001562d3aaa97b4129a58e3ed7d74728425cf562bac21082428785b505"},
{"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/gl/blending/AlphaTests.class","27d245913eb8bf6f13137fed144de26c281155feb99d46c135d92482e4ee2d66"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","571f06982ae05c65ff68b263dde5a2f514d1ffa31276b7dd9c29aff82a2550ab"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","7b0877fb90a63086ca980707cc3d0f09523f0730b589de75c34fcbb6fbd303c1"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","a553eca23bd0ead8582028e057426e80aac26ae7de8444422a7ec7f3a2498693"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","e8085a1233270b10be87639b2fde6456266b3b4d74f585bd795127162262e1a6"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class","9242f786bf95a6484a6a5031e0f4c4b814799d4ee12aaa511b9413384318916e"},
  {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","b155dbeeebad99b1fac87103816529383eb256b1fb650e59ba5c825687b7acfa"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","498dbe74f29277634a7808b62252e392d7d9c6cf9bab204cc3cb2ac337b02f33"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","f631b00eda74bdef870be0f5505dfd244d1d4ff1ba419919b009b711190a7050"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f7fa0fa8badf0881491d048602ddc16cbd66998c7586de380e0556185e8fd0d5"},
 };
 public boolean shouldApplyMixin(String t,String m){if(!exactApi())return false;com.pp.shaderlod.optimization.blissprime.BlissDepthPriming.install();return true;}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
