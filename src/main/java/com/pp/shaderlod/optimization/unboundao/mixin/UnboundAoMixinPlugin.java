package com.pp.shaderlod.optimization.unboundao.mixin;
import com.pp.shaderlod.optimization.unboundao.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class UnboundAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.unboundmips.UnboundDeadMipAdmission.install();com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("complementary-unbound",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"Unbound half-AO; API="+UnboundAoApiPins.status()+"; "+(UnboundAoApiPins.exact()?UnboundAoRuntime.status():"hooks not installed; API refused"));if(UnboundAoApiPins.exact())UnboundAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!UnboundAoApiPins.exact())return false;for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return UnboundAoApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
