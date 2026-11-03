package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoNormalBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.*;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class BslAoQuadDrawMixin {
 @Shadow @Final private WorldRenderingPipeline pipeline;
 @Unique private Program shaderlod$program;
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1)
 private void use(Program program,Operation<Void> original){shaderlod$program=program;original.call(program);}
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1)
 private void draw(FullScreenQuadRenderer renderer,Operation<Void> original){BslAoNormalBridge.quad(pipeline,shaderlod$program,()->original.call(renderer));}
}
