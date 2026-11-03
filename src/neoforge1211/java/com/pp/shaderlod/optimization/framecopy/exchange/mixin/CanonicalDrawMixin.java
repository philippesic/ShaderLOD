package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="com.mojang.blaze3d.platform.GlStateManager",remap=false)
public abstract class CanonicalDrawMixin {
    @WrapOperation(method="_drawElements(IIIJ)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V"),require=1,allow=1)
    private static void exchange$draw(int mode,int count,int type,long offset,Operation<Void> original){boolean owned=CanonicalBankExchange.beforeDraw(mode,count,type,offset);original.call(mode,count,type,offset);CanonicalBankExchange.drawReturned(owned);}
}
