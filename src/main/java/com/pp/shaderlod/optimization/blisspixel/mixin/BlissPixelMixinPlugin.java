package com.pp.shaderlod.optimization.blisspixel.mixin;
import com.pp.shaderlod.optimization.blisspixel.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;import java.util.*;
public final class BlissPixelMixinPlugin implements IMixinConfigPlugin {
 public void onLoad(String p){if(BlissPixelApiPins.exact())BlissPixelRuntime.install();}public String getRefMapperConfig(){return null;}
 public boolean shouldApplyMixin(String target,String mixin){if(!BlissPixelApiPins.exact())return false;for(String name:List.of("Quad","RenderPass","Gl","Pearl"))if(mixin.endsWith(name+"DrawMixin"))return BlissPixelApiPins.backend().equals(name);return true;}
 public void acceptTargets(Set<String>a,Set<String>b){}public List<String>getMixins(){return null;}public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
