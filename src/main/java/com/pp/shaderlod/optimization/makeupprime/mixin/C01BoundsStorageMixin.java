package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.C01MeshBounds;
import net.caffeinemc.mods.sodium.client.gl.arena.GlBufferSegment;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SectionRenderDataStorage.class,remap=false)
public abstract class C01BoundsStorageMixin implements C01MeshBounds.Storage {
 @Shadow @Final private GlBufferSegment[] vertexAllocations;
 @Shadow public abstract long getDataPointer(int section);
 public C01MeshBounds shaderlod$bounds(int section){
  var segment=vertexAllocations[section];if(segment==null)return null;
  var b=((C01MeshBounds.Carrier)segment).shaderlod$bounds();
  return b!=null&&b.section==section&&b.vertices==segment.getLength()
    &&SectionRenderDataUnsafe.getVertexOffset(getDataPointer(section),0)==segment.getOffset()?b:null;
 }
}
