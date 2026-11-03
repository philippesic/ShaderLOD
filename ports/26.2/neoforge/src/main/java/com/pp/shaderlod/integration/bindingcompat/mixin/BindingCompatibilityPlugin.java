package com.pp.shaderlod.integration.bindingcompat.mixin;
import java.util.*;
import org.spongepowered.asm.mixin.extensibility.*;
import org.objectweb.asm.tree.ClassNode;
public final class BindingCompatibilityPlugin implements IMixinConfigPlugin {
 public void onLoad(String name){} public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){try{return !Boolean.getBoolean("shaderlod.dev.portBindingInputFix")&&com.pp.shaderlod.integration.bindingcompat.BindingInputPins.match()&&com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins.exactApi();}catch(Exception|LinkageError unavailable){return false;}}
 public void acceptTargets(Set<String> own,Set<String> other){} public List<String> getMixins(){return null;}
 public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){} public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
