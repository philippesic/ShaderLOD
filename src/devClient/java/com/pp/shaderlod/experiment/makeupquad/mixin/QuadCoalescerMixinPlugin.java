package com.pp.shaderlod.experiment.makeupquad.mixin;
import com.pp.shaderlod.experiment.makeupquad.QuadCoalescerGate;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Do not apply the pinned injector on an unknown renderer binary. */
public final class QuadCoalescerMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p) {}
    public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String t,String m){
        String mode=System.getProperty("shaderlod.dev.makeupQuadCoalescerR1","off");
        if (!mode.equals("control") && !mode.equals("candidate")) return false;
        return QuadCoalescerGate.exactApi();
    }
    public void acceptTargets(Set<String> mine,Set<String> others){}
    public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i){}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
