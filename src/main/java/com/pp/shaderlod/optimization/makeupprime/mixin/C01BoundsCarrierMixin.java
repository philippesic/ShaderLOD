package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.C01MeshBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
@Mixin(targets={"net.caffeinemc.mods.sodium.client.util.NativeBuffer","net.caffeinemc.mods.sodium.client.gl.arena.PendingUpload","net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment"},remap=false)
public abstract class C01BoundsCarrierMixin implements C01MeshBounds.Carrier {
 @Unique private C01MeshBounds shaderlod$c01Bounds;
 public C01MeshBounds shaderlod$bounds(){return shaderlod$c01Bounds;}
 public void shaderlod$bounds(C01MeshBounds b){shaderlod$c01Bounds=b;}
}
