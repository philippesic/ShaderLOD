package com.pp.shaderlod.optimization.blissmerged.mixin;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Exact pinned normal runtime; independent control is evaluated at the selected consumer. */
public final class BlissMergedMixinPlugin implements IMixinConfigPlugin {
 private static boolean exactApi(){return com.pp.shaderlod.optimization.makeupmerged.MakeUpMergedApiPins.exactApi();}
 public boolean shouldApplyMixin(String t,String m){boolean flag=true;boolean apply=exactApi();System.out.println("[ShaderLOD merged] gate=renderer-plugin mixin="+m+" flag="+flag+" apply="+apply);return apply;}
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("bliss-shader",com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN,com.pp.shaderlod.optimization.blissmerged.BlissMergedLinkageGate::status);System.out.println("[ShaderLOD merged] gate=renderer-plugin loaded package="+p);}public String getRefMapperConfig(){return null;}
 public void acceptTargets(Set<String> a,Set<String> b){}public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
