package com.pp.shaderlod.dev.readiness.mixin;
import com.pp.shaderlod.dev.readiness.ReadinessEvents;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=RenderSectionManager.class,remap=false)
public abstract class ManagerReadinessMixin {
    @Shadow @Final @Mutable private ConcurrentLinkedDeque<?> buildResults;
    @Shadow @Final private ChunkBuilder builder;
    @Inject(method="<init>",at=@At("RETURN"),remap=false)
    private void queue(CallbackInfo ci){
        if(!buildResults.isEmpty()){ReadinessEvents.fail("INITIAL_RESULT_QUEUE_NONEMPTY");return;}
        buildResults=new ReadinessEvents.ResultQueue<>();ReadinessEvents.manager(this,builder,buildResults);
    }
    @WrapOperation(method="collectChunkBuildResults",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobResult;unwrap()Ljava/lang/Object;"),require=1,allow=1,remap=false)
    private Object unwrap(ChunkJobResult<?> result,Operation<Object> original){
        try{Object output=original.call(result);ReadinessEvents.event("UNWRAP",result,output,ReadinessEvents.id(this),0);return output;}
        catch(RuntimeException|Error e){ReadinessEvents.failed("UNWRAP",e);throw e;}
    }
    @WrapOperation(method="processChunkBuildResults",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;uploadResults(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Ljava/util/Collection;)V"),require=1,allow=1,remap=false)
    private void upload(RenderRegionManager regions,CommandList commands,Collection<?> outputs,Operation<Void> original){
        ReadinessEvents.event("UPLOAD_BEGIN",this,outputs,outputs.size(),0);
        for(Object output:outputs)ReadinessEvents.event("UPLOAD_OUTPUT",outputs,output,ReadinessEvents.id(this),0);
        try{original.call(regions,commands,outputs);ReadinessEvents.event("UPLOAD_RETURN",this,outputs,outputs.size(),0);}
        catch(RuntimeException|Error e){ReadinessEvents.failed("UPLOAD",e);throw e;}
    }
}
