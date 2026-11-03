package com.pp.shaderlod.port.dev.mixin;
import com.pp.shaderlod.fabric.dev.budget.BslAlphaCapture;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer",remap=false)
public abstract class PortBslAlphaDrawMixin {
    @Inject(method="executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V",at=@At("HEAD"),require=1,allow=1,remap=false)
    private static void shaderlod$actualDraw(CommandList commands,GlTessellation tessellation,MultiDrawBatch batch,CallbackInfo ci) {BslAlphaCapture.draw();}
}
