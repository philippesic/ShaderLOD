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
 private static final String[][] NAMED = {
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","4ba26f97a2a9006d3730962e3c7eb26f620c50cc7d98d82b6235720eac4753fb"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","4b358dd81cb7a1920b1c2bc9745c9cb3941f43a074ecda718e6f6b1fc9805749"},
  {"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","eddce1de800044da5e38aa6eba30296f84b06840944520e551ef7929e91df74e"},
  {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","85446876ccc4ea73039d0c8584f748dfa273bd2ecef4b23e000ef94d6ad90e54"},
  {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","837930478e21c5b46382596f75fb9417a5ed9059a8c098f5ba944b6cf7a02e0d"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","12b529949ab46fbd8c42b3b29c0e8bc470b6e8de9476f65c98b679264fb13142"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","bdbb09a1e8c7f17cbf34b5a4a839e99d072201e373f313c44f7dd4ad1d51b145"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","d2e007f9d6d57013183ece2feb18a63b1009d18aaa4baf120e47527214154973"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","eec87a2a2fc135cac20d929b29e158452dcee60fbe618a849e11392e5b6c3566"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","a553eca23bd0ead8582028e057426e80aac26ae7de8444422a7ec7f3a2498693"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","543067aaad4089ab2720b7e5c1a8855d5f6a12751d87e1e31c6077488076931f"},
  {"net/irisshaders/iris/Iris.class","3d77455150d5711af960fb9d85123fb201eb9996d948afcf61c46a6983b7836c"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","eea6e6cdbca25786177fd69eba632643df6691afe17257f6cbe580351393f3b4"},
  {"net/irisshaders/iris/pipeline/CustomTextureManager.class","9a6a3875a4d0205310c1eda867c06861e200e696c81cb0516416d111298ef464"},
  {"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","5addf4e0cfa9eb48e6b7100a233205bd8929284bba0c54b74cdc62dc6e436cfd"},
  {"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","9fecb403de48804fc076f0251cc4b93584dfaeb83e2836801371f3880afda070"},
  {"net/irisshaders/iris/gl/texture/TextureAccess.class","b6f3625c34a4f66b453e4ef6d49fb42b55b881d65eaaf0e5f563068e588abff0"},
  {"net/irisshaders/iris/gl/texture/TextureType.class","83249aa67458d9064b23e3c144564b9b0fa4dd84e693f5c30d0f660d2be49599"}
 };
 private static final String[][] INTERMEDIARY = {
  {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","7f49c1da2e12807f0680ca894f631c104b2a8846492e4c116014db11dc3e29b6"},
  {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","043308b62b7c6feb3626282a8c97e25de83403294871a7668367e43aff51c530"},
  {"net/irisshaders/iris/gl/blending/AlphaTest.class","4dfb5631141c6c2893bfe8fbd3cdbf5504e75275691fc4f312e06a0d5967c660"},
  {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","eddce1de800044da5e38aa6eba30296f84b06840944520e551ef7929e91df74e"},
  {"net/irisshaders/iris/uniforms/IrisInternalUniforms.class","85446876ccc4ea73039d0c8584f748dfa273bd2ecef4b23e000ef94d6ad90e54"},
  {"net/irisshaders/iris/uniforms/CapturedRenderingState.class","cee78f18e653a99b9b3e352b4beb3477ba7563acec1a97e8c78609a9dff55c02"},
  {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","498dbe74f29277634a7808b62252e392d7d9c6cf9bab204cc3cb2ac337b02f33"},
  {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","bdbb09a1e8c7f17cbf34b5a4a839e99d072201e373f313c44f7dd4ad1d51b145"},
  {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","f79728bc0f0cded446753da51f61114daed8988eef4f567bca9e351280874b28"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","e8085a1233270b10be87639b2fde6456266b3b4d74f585bd795127162262e1a6"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/ShaderWorkarounds.class","eec87a2a2fc135cac20d929b29e158452dcee60fbe618a849e11392e5b6c3566"},
  {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","a553eca23bd0ead8582028e057426e80aac26ae7de8444422a7ec7f3a2498693"},
  {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f7fa0fa8badf0881491d048602ddc16cbd66998c7586de380e0556185e8fd0d5"},
  {"net/irisshaders/iris/Iris.class","aafe700b689d93193a4c80bacb98e623f66949720ca0038b6a2da21c498074fd"},
  {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","006906a07a47313efc873289f9f59de5c2ec2030ed85ffaca39491916e8ab91e"},
  {"net/irisshaders/iris/pipeline/CustomTextureManager.class","add7cf46383e6e2bc7a6d47c3d0cba8fd91a4b7e0663bb5e1caadcf98f19e2b2"},
  {"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","4bc940645d01337d9c557e27db114d2a965e3974951ff611fbca00683a6c3529"},
  {"net/irisshaders/iris/pbr/texture/PBRTextureHolder.class","d4b08ef1162bffba3215e4a6fef7522545a8180119ae6bc2a31812c4cc68461a"},
  {"net/irisshaders/iris/gl/texture/TextureAccess.class","b6f3625c34a4f66b453e4ef6d49fb42b55b881d65eaaf0e5f563068e588abff0"},
  {"net/irisshaders/iris/gl/texture/TextureType.class","83249aa67458d9064b23e3c144564b9b0fa4dd84e693f5c30d0f660d2be49599"}
 };
 public static boolean exactApi(){ if(pinned!=null)return pinned; return pinned=resourcesMatch(AstraEarlyAlphaPlugin.class.getClassLoader()); }
 public static boolean resourcesMatch(ClassLoader loader){ return profileMatches(loader,NAMED)||profileMatches(loader,INTERMEDIARY); }
 private static boolean profileMatches(ClassLoader loader,String[][] profile){try{for(var pin:profile)try(var in=loader.getResourceAsStream(pin[0])){if(in==null||!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return false;}return true;}catch(Exception|LinkageError e){return false;}}
 public boolean shouldApplyMixin(String t,String m){return exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
