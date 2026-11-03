package com.pp.shaderlod.dev.readiness.mixin;
import com.pp.shaderlod.dev.readiness.ReadinessEvents;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.function.Consumer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.*;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ChunkBuilder.class,remap=false)
public abstract class BuilderReadinessMixin {
    @SuppressWarnings({"rawtypes","unchecked"})
    @WrapMethod(method="scheduleTask",remap=false)
    private ChunkJobTyped schedule(ChunkBuilderTask task,boolean important,Consumer consumer,Operation<ChunkJobTyped> original){
        ReadinessEvents.event("SUBMIT_BEGIN",this,task,important?1:0,0);
        try{ChunkJobTyped job=original.call(task,important,consumer);ReadinessEvents.event("SUBMIT_RETURN",this,job,ReadinessEvents.id(task),0);return job;}
        catch(RuntimeException|Error e){ReadinessEvents.failed("SUBMIT",e);throw e;}
    }
}
