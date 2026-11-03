package com.pp.shaderlod.optimization.reimaginedprime.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Gate before touching renderer targets. Inactive/original arms have no injected draw code. */
public final class DepthMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){if(exactApi())com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming.install();} public String getRefMapperConfig(){return null;}
 private static Boolean pinned;
 public static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
   {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","f6e1b9235241046036c2dda78cd8f3d19f62882a901b79c4f1ecfe46936ce399"},
   {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","dc0564352c37ba3a01919bbe185fb3e894998837950d32fe8f8e68837370c51f"},
   {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
   {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
   {"net/irisshaders/iris/pipeline/transform/PatchShaderType.class","3a4bf5b4f819f907d948dd76a1ee50369ef78ddb03b31c4f7d1c54e31f9c3201"},
   {"net/irisshaders/iris/pipeline/transform/TransformPatcher.class","2cc6d66298b0694e1a03c50da198d4d59ad3f63759183909ded86703af36fc47"},
   {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","17bbfb7cc7bd5da043f508924c6485c2bc550a123dad94165cc033610803f8ae"},
   {"net/irisshaders/iris/pipeline/transform/transformer/CommonTransformer.class","f0acce6faba3fde7e4e72d60b647fd7a0a69e02916791fa1dc905237a2d77544"},
   {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
   {"net/irisshaders/iris/shaderpack/properties/ProgramDirectives.class","f73338f164c05bb1c471718771685be1b964d4df100d480ecf2339a1ee477675"},
   {"net/irisshaders/iris/gl/framebuffer/GlFramebuffer.class","6b5bd7283f6eb50b2aaebfe17142b209c7012b7d12c1481f91cfc5371f377d6f"},
   {"net/irisshaders/iris/gl/IrisRenderSystem.class","1782e30fb9c25e3189ecd2215747321d7af7083ec9f74a5de657c879f0cbc71d"},
   {"net/irisshaders/iris/gl/blending/AlphaTest.class","d64dc340271e3ec90d405175de1bb092719fd6aca5935fff771987e1d8a1535d"},
   {"net/irisshaders/iris/gl/blending/AlphaTests.class","fbc12624f60eebe0e1c97c555f907f2c4acb21aff196311f6eddb87cc0cd401b"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e57ee69430ac4a79ab4608e2a3341b5af2ce08c5ecc84406502a9d0379a6333f"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","de983844ab8aece524a9830a9b6ee173cc5e4b5a1777ba06cf0d8a6af5c150bc"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlShader.class","e73940f5ba1cc7aec6c99aa5926ec7ac7bc073193dc073ebeb2caf5d0ac712b5"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class","80e9edbf32bf34171bdafcfb94e79a3c764a11b81d0a1183788220c64b8b7758"},
   {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","8b355f8255d0c6d1fd5e9bf1a89d9dacb2b494cabacdfbb1059201cb0dde3d8c"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass.class","92fc47a24254bc13083a9ab39423446a6345ec7f0ec646b435849c82da34dbf1"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/terrain/DefaultTerrainRenderPasses.class","fe150514b15d94bddd644f42c17c8f4674d84d8b50930a6525d4b6e91cea2387"},
   {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"}
  };
  try{for(var pin:pins)try(var in=DepthMixinPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){if(!exactApi())return false;com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming.install();return true;}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
