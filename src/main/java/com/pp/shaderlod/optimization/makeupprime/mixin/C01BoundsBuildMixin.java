package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.C01MeshBounds;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionMeshParts;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.impl.DefaultChunkMeshAttributes;
import net.irisshaders.iris.vertices.sodium.terrain.IrisChunkMeshAttributes;
import net.irisshaders.iris.vertices.sodium.terrain.XHFPModelVertexType;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ChunkBuildBuffers.class,remap=false)
public abstract class C01BoundsBuildMixin {
 @Shadow @Final private ChunkVertexType vertexType;
 @Inject(method="createMesh",at=@At("RETURN"),require=1)
 private void shaderlod$mesh(TerrainRenderPass pass,boolean force,CallbackInfoReturnable<BuiltSectionMeshParts> ci){
  if(!Boolean.getBoolean("shaderlod.dev.c01Bounds")||pass!=DefaultTerrainRenderPasses.SOLID||!(vertexType instanceof XHFPModelVertexType))return;
  var mesh=ci.getReturnValue();if(mesh==null)return;
  var format=vertexType.getVertexFormat();var attribute=format.getAttribute(IrisChunkMeshAttributes.BLOCK_ID);
  if(attribute==null||attribute.getFormat()!=org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT||!attribute.isIntType()||attribute.getCount()!=1)return;
  var position=format.getAttribute(DefaultChunkMeshAttributes.POSITION);var draw=format.getAttribute(DefaultChunkMeshAttributes.LIGHT_MATERIAL_INDEX);
  if(position==null||position.getPointer()!=0||position.getFormat()!=org.lwjgl.opengl.GL11C.GL_UNSIGNED_INT||position.getCount()!=2||!position.isIntType()
      ||draw==null||draw.getPointer()!=16||draw.getFormat()!=org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE||draw.getCount()!=4||!draw.isIntType())return;
  var buffer=mesh.getVertexData();
  ((C01MeshBounds.Carrier)buffer).shaderlod$bounds(C01MeshBounds.scan(buffer.getDirectBuffer(),format.getStride(),attribute.getPointer(),mesh.getVertexCounts()));
 }
}
