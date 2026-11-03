package com.pp.shaderlod.optimization.reimagineddepth.mixin;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.pp.shaderlod.optimization.reimagineddepth.ReimaginedAoDepthRuntime;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CompositeRenderer.class,remap=false)
public abstract class ReimaginedAoDepthCompositeMixin {
 @Shadow @Final private WorldRenderingPipeline pipeline;
 @Shadow @Final private RenderTargets renderTargets;
 @Unique private Program shaderlod$activeAoProgram;
 @Inject(method="createProgram",at=@At("RETURN"),require=1)
 private void built(ProgramSource source,ImmutableSet<Integer> reads,ImmutableSet<Integer> flipped,Supplier<ShadowRenderTargets> shadows,CallbackInfoReturnable<Program> ci){ReimaginedAoDepthRuntime.built(pipeline,renderTargets,source,ci.getReturnValue());}
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1)
 private void use(Program program,Operation<Void> original){shaderlod$activeAoProgram=program;ReimaginedAoDepthRuntime.mark(pipeline,renderTargets,program);original.call(program);}
 @WrapMethod(method="renderAll()V")private void scope(Operation<Void> original){try{original.call();}finally{ReimaginedAoDepthRuntime.leave();}}
}
