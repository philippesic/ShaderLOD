package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import com.pp.shaderlod.optimization.framecopy.rotation.HistoryRotationPins;
import java.util.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;
public final class CanonicalExchangeMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String pkg) {}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return Boolean.getBoolean(CanonicalBankExchange.PROPERTY)&&!Boolean.getBoolean("shaderlod.experimentalMakeUpHistoryRotation")&&HistoryRotationPins.exactApi();}
    public void acceptTargets(Set<String> mine,Set<String> other){}
    public List<String> getMixins(){return null;}
    public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){
        String simple=mixin.substring(mixin.lastIndexOf('.')+1);String hook=switch(simple){
            case "CanonicalSourceMixin" -> "source:"+target.substring(target.lastIndexOf('.')+1);
            case "CanonicalPipelineMixin" -> "pipeline";case "CanonicalTargetsMixin" -> "targets";
            case "CanonicalTargetMixin" -> "target";case "CanonicalCompositeMixin" -> "composite";
            case "CanonicalFinalMixin" -> "final";case "CanonicalDrawMixin" -> "draw";
            case "CanonicalFrameMixin" -> "frame";case "CanonicalClearMixin" -> "clear";default -> throw new IllegalStateException(simple);
        };
        boolean linked=false;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions)
            if(instruction instanceof MethodInsnNode call&&call.owner.equals("com/pp/shaderlod/optimization/framecopy/exchange/CanonicalBankExchange"))linked=true;
        if(!linked)throw new IllegalStateException("Missing transformed exchange call:"+hook);
        CanonicalBankExchange.hook(hook);
    }
}
