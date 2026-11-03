package com.pp.shaderlod.pccontrol;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;

/** No observer hooks in ordinary PC timings; explicit diagnostic launch and exact raw API only. */
public final class BslMipWitnessPlugin implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) {}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(!Boolean.getBoolean("shaderlod.dev.bslMipWitnessRuntime"))return false;
        try{return (Boolean)Class.forName("com.pp.shaderlod.optimization.mips.BslMipWitnessPins").getMethod("exactApi").invoke(null);}
        catch(ReflectiveOperationException|LinkageError absentOrUnsupported){return false;}
    }
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
