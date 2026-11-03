package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.C01MeshBounds;
import net.caffeinemc.mods.sodium.client.gl.arena.*;
import net.caffeinemc.mods.sodium.client.util.NativeBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=PendingUpload.class,remap=false)
public abstract class C01BoundsUploadMixin {
 @Inject(method="<init>",at=@At("RETURN"),require=1)
 private void shaderlod$upload(NativeBuffer data,CallbackInfo ci){((C01MeshBounds.Carrier)this).shaderlod$bounds(((C01MeshBounds.Carrier)data).shaderlod$bounds());}
 @Inject(method="setResult",at=@At("RETURN"),require=1)
 private void shaderlod$result(GlBufferSegment result,CallbackInfo ci){((C01MeshBounds.Carrier)result).shaderlod$bounds(((C01MeshBounds.Carrier)this).shaderlod$bounds());}
}
