package com.pp.shaderlod.optimization.solasao.mixin;
import com.pp.shaderlod.optimization.solasao.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class SolasAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.solasmips.SolasDeadMipAdmission.install();com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("solas-shader",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"Solas half-AO; API="+SolasAoApiPins.status()+"; "+(SolasAoApiPins.exact()?SolasAoRuntime.status():"hooks not installed; API refused"));if(SolasAoApiPins.exact())SolasAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!SolasAoApiPins.exact())return false;for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return SolasAoApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
