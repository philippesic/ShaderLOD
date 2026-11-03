package com.pp.shaderlod.shadowcoherence12111;
import java.util.*;import org.objectweb.asm.tree.ClassNode;import org.spongepowered.asm.mixin.extensibility.*;
public final class ShadowCoherencePlugin implements IMixinConfigPlugin {
 private static final Map<String,String> TARGETS=Map.of("com.pp.shaderlod.shadowcoherence12111.mixin.ShadowCullingFrustumMixin","net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum","com.pp.shaderlod.shadowcoherence12111.mixin.ShadowCullingFrustumMixin$SafeZone","net.irisshaders.iris.shadows.frustum.advanced.SafeZoneCullingFrustum","com.pp.shaderlod.shadowcoherence12111.mixin.ShadowBoxDistanceAccessor","net.irisshaders.iris.shadows.frustum.BoxCuller");
 public void onLoad(String p){} public String getRefMapperConfig(){return null;} public boolean shouldApplyMixin(String target,String mixin){return target!=null&&mixin!=null&&target.equals(TARGETS.get(mixin))&&ShadowCoherenceApi.exactApi();} public void acceptTargets(Set<String>a,Set<String>b){} public List<String>getMixins(){return null;} public void preApply(String t,ClassNode n,String m,IMixinInfo i){}public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
