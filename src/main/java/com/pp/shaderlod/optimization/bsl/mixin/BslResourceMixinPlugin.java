package com.pp.shaderlod.optimization.bsl.mixin;
import com.pp.shaderlod.optimization.bsl.BslResourceApiPins;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import java.util.*;
public final class BslResourceMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p) {} public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return BslResourceApiPins.exactApi();}
    public void acceptTargets(Set<String> a,Set<String> b) {} public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i) {}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i) {}
}
