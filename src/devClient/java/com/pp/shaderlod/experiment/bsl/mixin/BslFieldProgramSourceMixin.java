package com.pp.shaderlod.experiment.bsl.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import net.irisshaders.iris.gl.shader.GlShader;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Capture after the actual native link, before ProgramCreator's detach loop
 * and ProgramBuilder's shader destruction. Preserve native calls and inputs. */
@Mixin(value=ProgramCreator.class,remap=false)
public abstract class BslFieldProgramSourceMixin {
 @WrapOperation(method="create",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;glLinkProgram(I)V"),require=1)
 private static void linked(int program,Operation<Void> original,String name,GlShader[] shaders) {
  long token=BslShadowFieldBridge.nativeBeforeLink(program);
  try {
   original.call(program);
   BslShadowFieldBridge.nativeLinkedSources(token,program,name,shaders);
  } catch(RuntimeException|Error failure) {
   BslShadowFieldBridge.nativeLinkFailed(token,program);
   throw failure;
  }
 }
}
