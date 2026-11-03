package com.pp.shaderlod.experiment.hierarchy.mixin;
import java.util.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;
public final class DepthHierarchyPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String t,String m){return (Boolean.getBoolean("shaderlod.dev.depthHierarchyR50")||Boolean.getBoolean("shaderlod.dev.depthHierarchyProbe"))&&com.pp.shaderlod.experiment.makeup.mixin.MakeupAlphaMixinPlugin.exactApi();}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
