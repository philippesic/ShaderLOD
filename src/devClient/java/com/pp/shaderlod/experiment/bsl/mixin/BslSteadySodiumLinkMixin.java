package com.pp.shaderlod.experiment.bsl.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.experiment.bsl.BslShadowFieldBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
/** One construction-time actual linked-stage capture, not a frame observer. */
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.GlProgram$Builder",remap=false)
public abstract class BslSteadySodiumLinkMixin {
 @Shadow @Final private int program;
 @Unique private long shaderlod$linkToken=-1;
 @WrapMethod(method="link")
 private GlProgram<?> lifetime(Function<?,?> factory,Operation<GlProgram<?>> original) {
  shaderlod$linkToken=-1;
  try {
   GlProgram<?> result=original.call(factory);
   BslShadowFieldBridge.sodiumLinkReturned(shaderlod$linkToken,result);
   return result;
  } finally {BslShadowFieldBridge.sodiumLinkFinished(shaderlod$linkToken,program);}
 }
 @WrapOperation(method="link",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glLinkProgram(I)V"),require=1,allow=1)
 private void linked(int program,Operation<Void> original) {
  long token=BslShadowFieldBridge.sodiumBeforeLink(program);shaderlod$linkToken=token;
  try{original.call(program);BslShadowFieldBridge.sodiumLinked(token,program);}
  catch(RuntimeException|Error failure){BslShadowFieldBridge.sodiumLinkFailed(token,program);throw failure;}
 }
}
