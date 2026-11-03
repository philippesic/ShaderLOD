package com.pp.shaderlod.optimization.makeupprime.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
/** Gate before touching renderer targets. Inactive/original arms have no injected draw code. */
public final class DepthMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){if(exactApi())com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.install();} public String getRefMapperConfig(){return null;}
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
,
   {"net/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers.class","c14944bcd0e650b243aa39ff748980aab380ebac4552c5538a616fd8a2e3ba82"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionMeshParts.class","ee343bd5f90dc5fba2596a0b1d5ee3e825ce5f1ee0ba2d1cdd989ffb2912c4de"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage.class","4ff103954f245cf6ca7dfc6d12a73d2e8e9160d9f3631dff5f2dc5a805f0df7b"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe.class","2575f892c39b62fad17efe815a7a32453a8becc2e1d5da36a6c86807e992953f"},
   {"net/caffeinemc/mods/sodium/client/gl/arena/PendingUpload.class","d68018e529af2cffc7b44736c15c0f5229afd4acba8a8a8fd52b56ddb3493bf9"},
   {"net/caffeinemc/mods/sodium/client/gl/arena/GlBufferSegment.class","2441d05a1bade037b1c434b3edb112ee391f613d5b6d7b836099e3606396e63e"},
   {"net/caffeinemc/mods/sodium/client/util/NativeBuffer.class","7a7dc54eb5fc3a235519a4395ac0bac3e590128aab1ddb7803417b3848814415"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/LocalSectionIndex.class","cfdfdca3e2846002725271e85d88d69a6f7c6c30aa4b7001960af8a21b5c8d74"},
   {"net/irisshaders/iris/vertices/sodium/terrain/XHFPModelVertexType.class","2a757a06f6bd74d681b2a66ae2bd9a09f88b0eccbaff3b95970ab14c0dc4f167"},
   {"net/irisshaders/iris/vertices/sodium/terrain/XHFPTerrainVertex.class","3a0854be647b35b38d9bed221c9c8bb50ef8313e7392f6ef1d997e795eab40d7"},
   {"net/irisshaders/iris/vertices/sodium/terrain/IrisChunkMeshAttributes.class","f6091de0b861d5236467075c3c8178c1e9c3d172e51636ac7176e108be577144"}
  };
  try{for(var pin:pins)try(var in=DepthMixinPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){if(!exactApi())return false;com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.install();return true;}
 public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
