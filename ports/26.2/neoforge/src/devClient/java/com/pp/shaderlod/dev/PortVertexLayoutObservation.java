package com.pp.shaderlod.dev;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.mixin.PortGlRenderPassAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GlRenderPassAccessor;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.slf4j.LoggerFactory;
/** At most one shadow, one nonshadow and one binding/slot mismatch fact; no disk or policy change. */
public final class PortVertexLayoutObservation {
    private static boolean shadowRecorded,ordinaryRecorded,mismatchRecorded;
    private PortVertexLayoutObservation() {}
    public static void observe(Object pass) {
        if(!Boolean.getBoolean("shaderlod.dev.portOriginalOnly"))return;
        boolean shadow=ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        var data=new JsonObject();data.addProperty("observedUtc",java.time.Instant.now().toString());data.addProperty("shadowPass",shadow);
        data.addProperty("entry","GlCommandEncoder.executeDraws: after trySetup, before native validateDraw");data.addProperty("validationPolicyMutated",false);
        try {
            data.addProperty("passClass",pass.getClass().getName());data.addProperty("passIdentity",System.identityHashCode(pass));
            var pipeline=((GlRenderPassAccessor)pass).getPipeline();
            var slots=((PortGlRenderPassAccessor)pass).shaderlod$vertexBuffers();
            boolean mismatch=pipeline!=null && pipeline.info().getVertexFormatBindings().length<slots.length;
            boolean categoryRecorded=shadow ? shadowRecorded : ordinaryRecorded;
            if(categoryRecorded && (!mismatch || mismatchRecorded))return;
            if(shadow)shadowRecorded=true;else ordinaryRecorded=true;if(mismatch)mismatchRecorded=true;
            data.addProperty("bindingArrayShorterThanAllocatedSlots",mismatch);
            data.addProperty("allocatedVertexSlotCount",slots.length);var bound=new JsonArray();
            for(int i=0;i<slots.length;i++)if(slots[i]!=null)bound.add(i);data.add("nonNullVertexSlotIndices",bound);
            data.addProperty("pipelinePresent",pipeline!=null);
            if(pipeline!=null) {
                data.addProperty("pipelineClass",pipeline.getClass().getName());data.addProperty("pipelineIdentity",System.identityHashCode(pipeline));
                data.addProperty("pipelineInfoClass",pipeline.info().getClass().getName());
                data.addProperty("vertexFormatBindingCount",pipeline.info().getVertexFormatBindings().length);
                data.addProperty("pipelineCodeSource",String.valueOf(pipeline.getClass().getProtectionDomain().getCodeSource().getLocation()));
            }
        } catch(RuntimeException | LinkageError unavailable) {
            if(shadow ? shadowRecorded : ordinaryRecorded)return;
            if(shadow)shadowRecorded=true;else ordinaryRecorded=true;
            data.addProperty("layoutObservationUnavailable",unavailable.toString());
        }
        LoggerFactory.getLogger("shaderlod-dev").info("ShaderLOD pre-validation vertex layout: {}",data);
    }
}
