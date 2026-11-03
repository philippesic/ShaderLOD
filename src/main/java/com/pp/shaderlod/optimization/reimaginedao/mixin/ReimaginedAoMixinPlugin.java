package com.pp.shaderlod.optimization.reimaginedao.mixin;
import com.pp.shaderlod.optimization.reimaginedao.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class ReimaginedAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.reimaginedmips.ReimaginedDeadMipAdmission.install();com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("complementary-reimagined",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"Reimagined half-AO; API="+ReimaginedAoApiPins.status()+"; "+(ReimaginedAoApiPins.exact()?ReimaginedAoRuntime.status():"hooks not installed; API refused"));if(ReimaginedAoApiPins.exact())ReimaginedAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!ReimaginedAoApiPins.exact())return false;for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return ReimaginedAoApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
