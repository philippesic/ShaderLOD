package com.pp.shaderlod.optimization.makeupao.mixin;
import com.pp.shaderlod.optimization.makeupao.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class MakeUpAoMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("makeup-ultra-fast-shaders",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"MakeUp half-AO; API="+MakeUpAoApiPins.status()+"; "+(MakeUpAoApiPins.exact()?MakeUpAoRuntime.status():"hooks not installed; API refused"));if(MakeUpAoApiPins.exact())MakeUpAoRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!MakeUpAoApiPins.exact())return false;if(mixin.substring(mixin.lastIndexOf('.')+1).startsWith("MakeUpAoCompile"))return MakeUpAoCompilationPins.exact();for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return MakeUpAoApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
