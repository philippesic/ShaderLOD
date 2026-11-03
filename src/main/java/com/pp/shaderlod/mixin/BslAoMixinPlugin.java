package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoApiPins;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
public final class BslAoMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p) {String api=BslAoApiPins.exactApi()?BslAoApiPins.backend():"refused";com.pp.shaderlod.optimization.OptimizationRuntimeContext.configureImplementation("bsl-shaders",com.pp.shaderlod.optimization.OptimizationId.AO_RENDER_SCALE,()->"API="+api+"; "+com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.status());if(BslAoApiPins.exactApi())com.pp.shaderlod.optimization.bsl.BslAoNormalBridge.install();} public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){if(!BslAoApiPins.exactApi())return false;for(String backend:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(backend+"DrawMixin"))return BslAoApiPins.backend().equals(backend);return true;}
    public void acceptTargets(Set<String> a,Set<String> b) {} public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i) {}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i) {}
}
