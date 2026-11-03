package com.pp.shaderlod.optimization.makeupspatial.mixin;
import com.pp.shaderlod.optimization.makeupspatial.*;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class SpatialPipelineMixin{
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void shaderlod$destroy(CallbackInfo ci){try{var p=((IrisRenderingPipeline)(Object)this).getSodiumPrograms();if(p instanceof SpatialProgramAccess s)s.shaderlod$deleteSpatialProgram();}finally{MakeUpSpatialRuntime.destroy();}}
}
