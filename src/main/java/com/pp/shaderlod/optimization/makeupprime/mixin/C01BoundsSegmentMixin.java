package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.C01MeshBounds;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GlBufferSegment.class,remap=false)
public abstract class C01BoundsSegmentMixin {
 @Inject(method="delete",at=@At("HEAD"),require=1)
 private void shaderlod$delete(CallbackInfo ci){((C01MeshBounds.Carrier)this).shaderlod$bounds(null);}
}
