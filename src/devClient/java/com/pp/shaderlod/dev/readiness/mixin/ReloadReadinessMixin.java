package com.pp.shaderlod.dev.readiness.mixin;
import com.pp.shaderlod.dev.readiness.ReadinessEvents;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=Iris.class,remap=false)
public abstract class ReloadReadinessMixin {
    @WrapMethod(method="reload",remap=false)
    private static void reload(Operation<Void> original){ReadinessEvents.event("RELOAD_BEGIN",null,null,0,0);try{original.call();ReadinessEvents.event("RELOAD_RETURN",null,null,0,0);}catch(RuntimeException|Error e){ReadinessEvents.failed("RELOAD",e);throw e;}}
}
