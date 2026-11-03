package com.pp.shaderlod.experiment.bsl.mixin;
import java.util.*;
import java.security.MessageDigest;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class BslPrefixR8Plugin implements IMixinConfigPlugin {
 private Boolean pinned;
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String t,String m){
  if(!"1".equals(System.getProperty("shaderlod.dev.bslPrefixSustainedR8")))return false;
  if(pinned!=null)return pinned;
  try{String[][] pins={{"net/irisshaders/iris/pbr/format/TextureFormatLoader.class","7d2a63b529c6a88774d97a4b031dbac0dcb14bed97593714a71d7d53ed92b57e"},{"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
{"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
{"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"},{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
{"net/irisshaders/iris/pbr/texture/PBRAtlasTexture.class","1a47bffc6dfd069aeeeb33a8e8255134d3b0373ac4852830ba23cc9c2ce34ae4"},
{"net/irisshaders/iris/pbr/texture/PBRTextureManager.class","2c626afb4595e513c5d4e10713925579afd93f51a377f01213b167680c20e9d4"},
{"net/irisshaders/iris/pbr/format/TextureFormat.class","3404a49896c5afccc15a936cd77cff6b304eb997183d5b21ca715929e374f484"},
{"net/irisshaders/iris/pbr/format/LabPBRTextureFormat.class","c057657ce412d6ca872d61cd25cf4c3cc2d422dfc1f4f4e5ee7ea92761edcd31"},
{"net/irisshaders/iris/gl/program/ProgramUniforms.class","92d557456c18f45abf2ef7895e484bf0ebca648b331ed70fc84567982adb878f"},
{"net/irisshaders/iris/gl/uniform/IntUniform.class","70d87451b04c6626345b91243d432c700032476a97cb033c66bc85fcfc8c1041"}};for(var pin:pins)try(var in=getClass().getClassLoader().getResourceAsStream(pin[0])){
   if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
