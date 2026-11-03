package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoDrawScope;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class BslAoPearlDrawMixin {
 @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lcom/mojang/renderpearl/backend/opengl/GlStateManager;_drawElements(IIIJ)V"),require=1,allow=1)
 private void draw(int mode,int count,int type,long pointer,Operation<Void> original){BslAoDrawScope.draw(mode,count,type,pointer,()->original.call(mode,count,type,pointer));}
}
