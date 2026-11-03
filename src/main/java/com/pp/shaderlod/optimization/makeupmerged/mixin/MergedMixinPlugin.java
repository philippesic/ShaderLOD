package com.pp.shaderlod.optimization.makeupmerged.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Only explicitly opted-in, exact pinned dev runtime receives the hook. */
public final class MergedMixinPlugin implements IMixinConfigPlugin {
 private static Boolean pinned;
 private static boolean exactApi(){
  if(pinned!=null)return pinned;
  String[][] pins={
   {"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e57ee69430ac4a79ab4608e2a3341b5af2ce08c5ecc84406502a9d0379a6333f"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer.class","de983844ab8aece524a9830a9b6ee173cc5e4b5a1777ba06cf0d8a6af5c150bc"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","e567edab9e84facef6da76fe71ad1d4de3d765a9f57e12b624c935b49964a3a2"},
   {"net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion$DeviceResources.class","d5aee768a47c424e3e75317c44bc085b17a04224544195e3282aa311842c51ad"},
   {"net/caffeinemc/mods/sodium/client/gl/arena/GlBufferArena.class","25de0124a691c1a92bdf4c545794c27276c2bf780b98180b1922bd9244b65840"},
   {"net/caffeinemc/mods/sodium/client/gl/buffer/GlMutableBuffer.class","fdc54b954e60b85062efd8283027448726ab1d203e88a2d08b61d0c04b3248a9"},
   {"net/caffeinemc/mods/sodium/client/gl/device/CommandList.class","eaaac763999ac08ab892bc174ff82c3185a29e57c06825a6581714938e4f81fe"},
   {"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","8b355f8255d0c6d1fd5e9bf1a89d9dacb2b494cabacdfbb1059201cb0dde3d8c"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateCommandList.class","ce99c04d41e79d7f2d500e82c5f8311c35748f72fbaee3cbbf35fb03a8ce5ad4"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice$ImmediateDrawCommandList.class","60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b"},
   {"net/irisshaders/iris/compat/sodium/mixin/MixinShaderChunkRenderer.class","f6e1b9235241046036c2dda78cd8f3d19f62882a901b79c4f1ecfe46936ce399"},
   {"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","dc0564352c37ba3a01919bbe185fb3e894998837950d32fe8f8e68837370c51f"},
   {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
   {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
   {"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","17bbfb7cc7bd5da043f508924c6485c2bc550a123dad94165cc033610803f8ae"},
   {"net/caffeinemc/mods/sodium/client/gl/device/GLRenderDevice.class","5fb173e3939064faa6415d90ed27fe993d3c802fa17c40a3ed69b4f51a9e9555"},
   {"net/caffeinemc/mods/sodium/client/gl/state/GlStateTracker.class","44e7de0e141e0a0d85906b78992bc717a34299a2dacec7e74c2bae6f004e9c4c"},
   {"net/irisshaders/iris/compat/sodium/mixin/MixinRenderRegionArenas.class","ddb23ec4e2697fc0bcbe499ffc35cb38b1d661a75be66d977e9ee28422c81605"}
  };
  try{for(var pin:pins)try(var in=MergedMixinPlugin.class.getClassLoader().getResourceAsStream(pin[0])){
   if(in==null){System.out.println("[ShaderLOD merged] gate=renderer-api missing="+pin[0]+" loader="+MergedMixinPlugin.class.getClassLoader().getClass().getName());return pinned=false;}
   String actual=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));
   if(!actual.equals(pin[1])){System.out.println("[ShaderLOD merged] gate=renderer-api mismatch="+pin[0]+" actual="+actual+" expected="+pin[1]);return pinned=false;}
  }System.out.println("[ShaderLOD merged] gate=renderer-api admitted");return pinned=true;}catch(Exception|LinkageError e){System.out.println("[ShaderLOD merged] gate=renderer-api exception="+e.getClass().getSimpleName());return pinned=false;}
 }
 public boolean shouldApplyMixin(String t,String m){boolean flag=true;boolean apply=exactApi();System.out.println("[ShaderLOD merged] gate=renderer-plugin mixin="+m+" flag="+flag+" apply="+apply);return apply;}
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("makeup-ultra-fast-shaders",com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN,com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate::status);System.out.println("[ShaderLOD merged] gate=renderer-plugin loaded package="+p);}public String getRefMapperConfig(){return null;}
 public void acceptTargets(Set<String> a,Set<String> b){}public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
