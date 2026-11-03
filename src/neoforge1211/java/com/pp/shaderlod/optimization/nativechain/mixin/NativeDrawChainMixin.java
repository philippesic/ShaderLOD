package com.pp.shaderlod.optimization.nativechain.mixin;
import com.pp.shaderlod.optimization.nativechain.NativeDrawChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList",remap=false)
public abstract class NativeDrawChainMixin {
    @Redirect(method="multiDrawElementsBaseVertex",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex(IJIJIJ)V"),require=1,allow=1)
    private void shaderlod$nativeDraw(int mode,long counts,int type,long indices,int drawcount,long bases) {
        NativeDrawChain.draw(mode,counts,type,indices,drawcount,bases);
    }
}
