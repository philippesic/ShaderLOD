package com.pp.shaderlod.optimization.reimaginedspatial.mixin;
import com.pp.shaderlod.optimization.reimaginedspatial.*;import com.llamalad7.mixinextras.sugar.Local;import net.irisshaders.iris.gl.program.ProgramBuilder;import com.google.common.collect.ImmutableSet;import java.util.function.Supplier;import net.irisshaders.iris.gl.sampler.SamplerHolder;import net.irisshaders.iris.targets.RenderTargets;import net.irisshaders.iris.pipeline.*;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import net.irisshaders.iris.gl.program.Program;import net.irisshaders.iris.shadows.ShadowRenderTargets;import com.llamalad7.mixinextras.injector.wrapoperation.*;import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CompositeRenderer.class,remap=false)
public abstract class ReimaginedSpatialCompositeMixin {
 @Shadow @Final private WorldRenderingPipeline pipeline;@Shadow @Final private RenderTargets renderTargets;@Unique private Program shaderlod$spatialProgram;
 @WrapOperation(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/samplers/IrisSamplers;addRenderTargetSamplers(Lnet/irisshaders/iris/gl/sampler/SamplerHolder;Ljava/util/function/Supplier;Lnet/irisshaders/iris/targets/RenderTargets;ZLnet/irisshaders/iris/pipeline/WorldRenderingPipeline;)V"),require=1,allow=1)
 private void samplers(SamplerHolder h,Supplier<ImmutableSet<Integer>> f,RenderTargets t,boolean full,WorldRenderingPipeline p,Operation<Void> original,ProgramSource source,ImmutableSet<Integer> read,ImmutableSet<Integer> flipped,Supplier<ShadowRenderTargets> shadows,@Local ProgramBuilder builder){original.call(h,f,t,full,p);ReimaginedSpatialRuntime.samplers(p,t,source,h);ReimaginedSpatialRuntime.defaults(source,builder);}
 @Inject(method="createProgram",at=@At("RETURN"),require=1)
 private void built(ProgramSource s,ImmutableSet<Integer> read,ImmutableSet<Integer> flip,Supplier<ShadowRenderTargets> shadows,CallbackInfoReturnable<Program> c){ReimaginedSpatialRuntime.built(pipeline,renderTargets,s,c.getReturnValue());}
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1,allow=1)
 private void use(Program p,Operation<Void> original){shaderlod$spatialProgram=p;original.call(p);}
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
 private void skip(net.irisshaders.iris.pathways.FullScreenQuadRenderer q,Operation<Void> original){if(!ReimaginedSpatialRuntime.skipNormal(pipeline,shaderlod$spatialProgram))original.call(q);}
}
