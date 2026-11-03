package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.G01QuadPayloadProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.mojang.blaze3d.platform.GlStateManager",remap=false)
public abstract class G01PayloadDrawMixin {
 @Inject(method="_drawElements(IIIJ)V",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V"),require=1,allow=1)
 private static void draw(int mode,int count,int type,long offset,CallbackInfo ci){G01QuadPayloadProbe.draw(mode,count,type,offset);}
}
