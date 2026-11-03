package com.pp.shaderlod.optimization.sourcecache.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class SourceLifecycleMixin {
 @WrapMethod(method="createPipeline")
 private static WorldRenderingPipeline build(NamespacedId dimension,Operation<WorldRenderingPipeline> original){
  return IrisSourceLifecycle.build(()->original.call(dimension));
 }
 @Inject(method={"reload()V","destroyEverything()V"},at=@At("HEAD"),require=2,allow=2)
 private static void revoke(CallbackInfo ci){IrisSourceLifecycle.invalidate();}
}
