package com.pp.shaderlod.optimization.astraao.mixin;
import com.pp.shaderlod.optimization.astraao.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class AstraAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("astralex",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"Astra half-AO; API="+AstraAoApiPins.status()+"; "+(AstraAoApiPins.exact()?AstraAoRuntime.status():"hooks not installed; API refused"));if(AstraAoApiPins.exact())AstraAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!AstraAoApiPins.exact())return false;for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return AstraAoApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
