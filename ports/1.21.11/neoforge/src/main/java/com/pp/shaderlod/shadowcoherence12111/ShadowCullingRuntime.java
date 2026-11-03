package com.pp.shaderlod.shadowcoherence12111;
import com.pp.shaderlod.ShadowCullingController;import com.pp.shaderlod.ShadowFrustumCoherenceController;import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;import com.pp.shaderlod.optimization.OptimizationId;import net.irisshaders.iris.Iris;import net.irisshaders.iris.api.v0.IrisApi;import net.minecraft.client.Minecraft;
/** MC1.21.11 neoforge end-tick source/config inspection; AABB calls read only unchanged cached controllers. */
public final class ShadowCullingRuntime {
 private ShadowCullingRuntime(){}
 public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(event==null){applyEligibility(false,false,false);return;}
  try{
   Minecraft client=Minecraft.getInstance();
   if(!ShadowCoherenceApi.exactApi()||client==null||client.level==null){applyEligibility(false,false,false);return;}
   boolean positive=MakeUpQuadRuntime.enabled(OptimizationId.SHADOW_POSITIVE_VERTEX);
   boolean coherence=MakeUpQuadRuntime.enabled(OptimizationId.SHADOW_FRUSTUM_COHERENCE);
   boolean eligible=(positive||coherence)&&MakeUpQuadRuntime.nativeGraphAvailable()&&IrisApi.getInstance().isShaderPackInUse()&&!Iris.isFallback();
   applyEligibility(positive,coherence,eligible);
  }catch(RuntimeException|LinkageError unavailable){applyEligibility(false,false,false);}
 }
 /** Exact tick policy seam; no source/API admission is inferred from these booleans. */
 static void applyEligibility(boolean positive,boolean coherence,boolean eligible){ShadowCullingController.configure(positive);ShadowCullingController.setEligible(eligible);ShadowFrustumCoherenceController.configure(coherence&&eligible);}
}
