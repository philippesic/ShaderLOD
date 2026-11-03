package com.pp.shaderlod.experiment.bsl.mixin;
import java.util.*;
import java.security.MessageDigest;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
public final class BslPrefixProbePlugin implements IMixinConfigPlugin {
 private Boolean pinned;
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String t,String m){
  if(!"1".equals(System.getProperty("shaderlod.dev.bslPrefixProbeR3")))return false;
  if(pinned!=null)return pinned;
  try{String[][] pins={{"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
{"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
{"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"}};for(var pin:pins)try(var in=getClass().getClassLoader().getResourceAsStream(pin[0])){
   if(in==null||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes())).equals(pin[1]))return pinned=false;
  }return pinned=true;}catch(Exception|LinkageError e){return pinned=false;}
 }
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
