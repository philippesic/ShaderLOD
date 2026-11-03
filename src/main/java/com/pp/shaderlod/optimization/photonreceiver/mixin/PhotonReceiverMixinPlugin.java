package com.pp.shaderlod.optimization.photonreceiver.mixin;
import java.util.*;import org.spongepowered.asm.mixin.extensibility.*;import org.objectweb.asm.tree.ClassNode;
public final class PhotonReceiverMixinPlugin implements IMixinConfigPlugin{
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("photon",com.pp.shaderlod.optimization.OptimizationId.SHADOW_RECEIVER_FOOTPRINT,com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverRuntime::status);}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverApi.exact();}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}