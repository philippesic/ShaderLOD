package com.pp.shaderlod.optimization.reimaginedmerged.mixin;
import com.pp.shaderlod.optimization.reimaginedmerged.ReimaginedMergedProgramAccess;
import com.pp.shaderlod.optimization.reimaginedmerged.ReimaginedMergedLinkageGate;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Pipeline reload owns derivative disposal; original program ownership stays native. */
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class ReimaginedMergedPipelineDestroyMixin {
    @Shadow @Final private SodiumPrograms sodiumPrograms;
    @Inject(method="destroy",at=@At("HEAD"),require=1,allow=1)
    private void shaderlodReimagined$destroy(CallbackInfo ci){ReimaginedMergedLinkageGate.invalidateSourceCache();if(sodiumPrograms instanceof ReimaginedMergedProgramAccess access)access.reimaginedDeleteMergedPrograms();}
}
