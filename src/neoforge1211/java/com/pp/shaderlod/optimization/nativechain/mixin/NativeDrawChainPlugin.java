package com.pp.shaderlod.optimization.nativechain.mixin;
import com.pp.shaderlod.optimization.nativechain.NativeChainAdmission;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
public final class NativeDrawChainPlugin implements IMixinConfigPlugin {
 public void onLoad(String packageName) {}
 public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){return NativeChainAdmission.useReplacement();}
 public void acceptTargets(Set<String> own,Set<String> others){}
 public List<String> getMixins(){return null;}
 public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
 public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
