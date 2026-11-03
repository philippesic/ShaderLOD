package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.MipFamilyProbe;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.targets.RenderTarget;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value={CompositeRenderer.class,FinalPassRenderer.class},remap=false)
public abstract class MipFamilyRendererMixin {
 @Shadow(remap=false) @Final private WorldRenderingPipeline pipeline;
 @WrapMethod(method="createProgram(Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lcom/google/common/collect/ImmutableSet;Lcom/google/common/collect/ImmutableSet;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/program/Program;")
 private Program shaderlod$familyCompile(ProgramSource source,ImmutableSet<Integer>a,ImmutableSet<Integer>b,Supplier<ShadowRenderTargets> shadows,Operation<Program> original){
  if(!MipFamilyProbe.observing())return original.call(source,a,b,shadows);
  try(var scope=MipFamilyProbe.open(this,pipeline,source)){var program=original.call(source,a,b,shadows);scope.complete(program);return program;}
 }
 @Inject(method={"renderAll()V","renderFinalPass()V"},at=@At("HEAD"),require=1)
 private void shaderlod$familyEnter(CallbackInfo ci){if(MipFamilyProbe.observing())MipFamilyProbe.enter(this);}
 @WrapOperation(method={"renderAll()V","renderFinalPass()V"},at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1)
 private void shaderlod$familyQuad(FullScreenQuadRenderer quad,Operation<Void> original){if(MipFamilyProbe.observing())MipFamilyProbe.beforeQuad(this);original.call(quad);if(MipFamilyProbe.observing())MipFamilyProbe.quad(this);}
 @Inject(method="setupMipmapping",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/IrisRenderSystem;generateMipmaps(II)V",shift=At.Shift.AFTER),require=1)
 private static void shaderlod$familyGenerated(RenderTarget target,boolean alt,CallbackInfo ci){if(MipFamilyProbe.observing())MipFamilyProbe.generated(target,alt);}
 @Inject(method={"renderAll()V","renderFinalPass()V"},at=@At("RETURN"),require=1)
 private void shaderlod$familyReturn(CallbackInfo ci){if(MipFamilyProbe.observing()&&((Object)this) instanceof FinalPassRenderer)MipFamilyProbe.finalReturn(this);}
 @Inject(method={"renderAll()V","renderFinalPass()V"},at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V",shift=At.Shift.AFTER),require=0)
 private void shaderlod$familyCopy(CallbackInfo ci){if(MipFamilyProbe.observing())MipFamilyProbe.copy(this);}
}
