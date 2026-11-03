package com.pp.shaderlod.integration.makeupquad.mixin;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
/** Resource-only class pin gate; absent or different Iris/Sodium never loads typed runtime/mixins. */
public final class MakeUpQuadMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p) {}
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String target,String mixin) { if(!MakeUpQuadApiPins.exactApi())return false;if(mixin.endsWith("LegacyScalarBatchMixin")||mixin.endsWith("LegacyScalarFillMixin"))return com.pp.shaderlod.integration.makeupquad.LegacyScalarApiPins.exact();if(mixin.endsWith("AstraQuadInputMixin"))return com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.inputKind()==0;if(mixin.endsWith("AstraQuadModernInputMixin"))return com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.inputKind()==1;if(mixin.endsWith("AstraQuadCreatorSetInputMixin"))return com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.inputKind()==3;if(mixin.endsWith("AstraQuadCreatorInputMixin"))return com.pp.shaderlod.integration.astraquad.AstraQuadInputProof.inputKind()==2;if(mixin.endsWith("SolasQuadInputMixin"))return com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.inputKind()==0;if(mixin.endsWith("SolasQuadModernInputMixin"))return com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.inputKind()==1;if(mixin.endsWith("SolasQuadCreatorInputMixin"))return com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.inputKind()==2;if(mixin.endsWith("SolasQuadCreatorSetInputMixin"))return com.pp.shaderlod.integration.solasquad.SolasQuadInputProof.inputKind()==3;return true; }
    public void acceptTargets(Set<String> mine,Set<String> others) {}
    public List<String> getMixins() { return null; }
    public void preApply(String t,ClassNode n,String m,IMixinInfo i) {}
    public void postApply(String t,ClassNode n,String m,IMixinInfo i) {}
}
