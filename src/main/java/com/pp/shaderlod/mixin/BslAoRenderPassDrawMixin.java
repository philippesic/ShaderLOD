package com.pp.shaderlod.mixin;
import com.pp.shaderlod.optimization.bsl.BslAoDrawScope;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
/** The actual literal native draw AFTER 1.21.11 encoder setup and VAO/IBO binds.
 * Reuse the already-prepared direct primitive, not RenderPass.drawIndexed. */
@Mixin(targets="com.mojang.blaze3d.opengl.GlStateManager",remap=false)
public abstract class BslAoRenderPassDrawMixin {
 @WrapMethod(method="_drawElements(IIIJ)V")
 private static void draw(int mode,int count,int type,long pointer,Operation<Void> original){BslAoDrawScope.draw(mode,count,type,pointer,()->original.call(mode,count,type,pointer));}
}
