package com.pp.shaderlod.optimization.bslmerged.mixin;
import com.pp.shaderlod.optimization.bslmerged.BslMergedProgramAccess;
import com.pp.shaderlod.optimization.bslmerged.BslMergedLinkageGate;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Pipeline reload owns derivative disposal; original program ownership stays native. */
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class BslMergedPipelineDestroyMixin {
    @Shadow @Final private SodiumPrograms sodiumPrograms;
    @Inject(method="destroy",at=@At("HEAD"),require=1,allow=1)
    private void shaderlodBsl$destroy(CallbackInfo ci){BslMergedLinkageGate.invalidateSource();if(sodiumPrograms instanceof BslMergedProgramAccess access)access.bslDeleteMergedPrograms();}
}
