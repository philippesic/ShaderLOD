package com.pp.shaderlod.experiment.sourceonlyalpha.mixin;
import java.util.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;
public final class SourceOnlyAlphaPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String t,String m){return (Boolean.getBoolean(com.pp.shaderlod.experiment.sourceonlyalpha.SourceOnlyAlphaR23.PROPERTY)||Boolean.getBoolean("shaderlod.dev.bslPostSampleAlphaR1")||Boolean.getBoolean("shaderlod.dev.makeupSingleAlphaR1"))&&com.pp.shaderlod.experiment.sourceonlyalpha.SourceOnlyAlphaR23.exactApi();}
 public void acceptTargets(Set<String> a,Set<String> b){}public List<String> getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
