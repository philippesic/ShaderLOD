package com.pp.shaderlod.dev.bindingfix.mixin;
import com.pp.shaderlod.dev.bindingfix.BindingInputContext;
import com.pp.shaderlod.dev.bindingfix.BindingInputContract;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.*;
/** Outer return wrapper preserves Iris slot0 substitution and native unused slot declarations. */
@Mixin(targets="com.mojang.blaze3d.pipeline.RenderPipeline",remap=false)
public abstract class BindingInputPipelineMixin {
    @Shadow @Final private VertexFormat[] vertexFormatPerBuffer;
    @WrapMethod(method="getVertexFormatBindings")
    private VertexFormat[] shaderlod$nativeShape(Operation<VertexFormat[]> original) {
        VertexFormat[] rewritten=original.call();
        BindingInputContext.observeGetter((Object)this,vertexFormatPerBuffer,rewritten);
        var context=BindingInputContext.current();
        if(context==null || context.pipeline()!=(Object)this || rewritten.length==16)return rewritten;
        VertexFormat[] corrected=BindingInputContract.restoreOrOriginal(true,vertexFormatPerBuffer,rewritten,context.boundSlots());
        if(corrected==rewritten)return rewritten; // Unknown shape remains native, including native validation failure.
        BindingInputContext.lastIrisDescriptorSlots=rewritten.length;
        BindingInputContext.lastCorrectedDescriptorSlots=corrected.length;
        BindingInputContext.lastIrisSlotZeroIdentity=System.identityHashCode(rewritten[0]);
        BindingInputContext.lastCorrectedSlotZeroIdentity=System.identityHashCode(corrected[0]);
        BindingInputContext.correctedQueries++;
        if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())BindingInputContext.shadowCorrectedQueries++;
        else BindingInputContext.ordinaryCorrectedQueries++;
        return corrected;
    }
}
