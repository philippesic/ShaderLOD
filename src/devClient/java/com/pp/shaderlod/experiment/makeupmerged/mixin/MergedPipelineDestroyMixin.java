package com.pp.shaderlod.experiment.makeupmerged.mixin;
import com.pp.shaderlod.experiment.makeupmerged.MergedProgramAccess;
import com.pp.shaderlod.experiment.makeupmerged.MergedLinkageGate;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Pipeline reload owns derivative disposal; original program ownership stays native. */
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class MergedPipelineDestroyMixin {
    @Shadow @Final private SodiumPrograms sodiumPrograms;
    @Inject(method="destroy",at=@At("HEAD"),require=1,allow=1)
    private void shaderlod$destroy(CallbackInfo ci){if(sodiumPrograms instanceof MergedProgramAccess access)access.deleteMergedPrograms();}
    /** Exact native factory boundary catches constructor failure before any pipeline object exists. */
    @Mixin(value=Iris.class,remap=false)
    public abstract static class Construction {
        @Redirect(method="createPipeline",at=@At(value="NEW",target="net/irisshaders/iris/pipeline/IrisRenderingPipeline"),require=1,allow=1)
        private static IrisRenderingPipeline shaderlod$construct(ProgramSet programs) {
            try(var scope=MergedLinkageGate.beginConstruction()) {
                var pipeline=new IrisRenderingPipeline(programs);scope.commit();return pipeline;
            }
        }
    }
}
