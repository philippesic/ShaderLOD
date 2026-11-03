package com.pp.shaderlod.optimization.blissao.mixin;
import com.pp.shaderlod.optimization.blissao.*;import com.pp.shaderlod.optimization.blisspixel.BlissPixelApiPins;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class BlissAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("bliss-shader",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,BlissAoRuntime::status);if(BlissPixelApiPins.exact())BlissAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return BlissPixelApiPins.exact()&&(!mixin.endsWith("QuadDrawMixin")||BlissPixelApiPins.backend().equals("Quad"));}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
