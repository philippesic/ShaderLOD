package com.pp.shaderlod.optimization.makeupao.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import net.irisshaders.iris.gl.program.Program;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Program.class,remap=false)
public abstract class MakeUpAoCompileProgramMixin {
    @Inject(method="destroyInternal()V",at=@At("HEAD"),require=1)
    private void shaderlod$delete(CallbackInfo ci){IrisCompositeCompiledInputs.forget((Program)(Object)this);}
}
