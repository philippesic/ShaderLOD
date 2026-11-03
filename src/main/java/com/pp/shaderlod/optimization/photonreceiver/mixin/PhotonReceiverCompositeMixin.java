package com.pp.shaderlod.optimization.photonreceiver.mixin;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.pp.shaderlod.optimization.photonreceiver.PhotonReceiverRuntime;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CompositeRenderer.class,remap=false)
public abstract class PhotonReceiverCompositeMixin {
 @Shadow @Final private WorldRenderingPipeline pipeline;
 @Shadow @Final private RenderTargets renderTargets;
 @Inject(method="createProgram",at=@At("RETURN"),require=1)
 private void built(ProgramSource source,ImmutableSet<Integer> reads,ImmutableSet<Integer> flipped,Supplier<ShadowRenderTargets> shadows,CallbackInfoReturnable<Program> ci){PhotonReceiverRuntime.built(pipeline,renderTargets,source,ci.getReturnValue());}
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/Program;use()V"),require=1)
 private void use(Program program,Operation<Void> original){PhotonReceiverRuntime.leave();original.call(program);PhotonReceiverRuntime.mark(pipeline,renderTargets,program);}
 @WrapMethod(method="renderAll()V")private void scope(Operation<Void> original){
  Throwable primary=null;try{original.call();}catch(RuntimeException|Error failure){primary=failure;throw failure;}
  finally{try{PhotonReceiverRuntime.leave();}catch(RuntimeException|Error failure){if(primary!=null)primary.addSuppressed(failure);else throw failure;}}
 }
}
