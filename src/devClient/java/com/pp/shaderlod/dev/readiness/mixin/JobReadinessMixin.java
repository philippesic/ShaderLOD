package com.pp.shaderlod.dev.readiness.mixin;
import com.pp.shaderlod.dev.readiness.ReadinessEvents;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobTyped;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ChunkJobTyped.class,remap=false)
public abstract class JobReadinessMixin {
    @Shadow @Final private ChunkBuilderTask<?> task;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void defined(CallbackInfo ci){ReadinessEvents.event("JOB_DEFINED",this,task,0,0);}
    @WrapMethod(method="execute",remap=false)
    private void execute(ChunkBuildContext context,Operation<Void> original){
        ReadinessEvents.event("EXEC_BEGIN",this,task,0,0);
        try{original.call(context);ReadinessEvents.event("EXEC_RETURN",this,task,0,0);}
        catch(RuntimeException|Error e){ReadinessEvents.failed("EXEC",e);throw e;}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    @WrapOperation(method="execute",at=@At(value="INVOKE",target="Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),require=1,allow=1,remap=false)
    private void complete(Consumer consumer,Object result,Operation<Void> original){
        ReadinessEvents.event("COMPLETE_RESULT",this,result,ReadinessEvents.id(task),0);
        try{original.call(consumer,result);ReadinessEvents.event("RESULT_CONSUMER_RETURN",this,result,0,0);}
        catch(RuntimeException|Error e){ReadinessEvents.failed("RESULT_CONSUMER",e);throw e;}
    }
}
