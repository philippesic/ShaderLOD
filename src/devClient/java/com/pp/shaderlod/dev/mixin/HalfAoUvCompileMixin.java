package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.HalfAoUvObservation;import net.irisshaders.iris.gl.program.Program;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.*;import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs$Scope",remap=false)
public abstract class HalfAoUvCompileMixin {
 @Inject(method="complete",at=@At("RETURN"),require=1) private void observed(Program p,CallbackInfo ci){HalfAoUvObservation.compiled(this,p);}
}
