package com.pp.shaderlod.experiment.depth.mixin;
import com.pp.shaderlod.experiment.depth.DepthPrimingR2;
import com.pp.shaderlod.experiment.depth.DepthProgramAccess;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.SharedQuadIndexBuffer;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Second pure assembly/draw loop, before the untouched native colour loop. No list retention. */
@Mixin(value=DefaultChunkRenderer.class,remap=false)
public abstract class DepthChunkRendererMixin extends ShaderChunkRenderer {
 protected DepthChunkRendererMixin(RenderDevice d,ChunkVertexType v){super(d,v);}
 @Shadow @Final private MultiDrawBatch batch;
 @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;
 @Shadow private static void fillCommandBuffer(MultiDrawBatch b,RenderRegion r,SectionRenderDataStorage s,ChunkRenderList l,CameraTransform c,TerrainRenderPass p,boolean f){throw new AssertionError();}
 @Shadow private static void setModelMatrixUniforms(ChunkShaderInterface s,RenderRegion r,CameraTransform c){throw new AssertionError();}
 @Shadow private static void executeDrawBatch(CommandList c,GlTessellation t,MultiDrawBatch b){throw new AssertionError();}
 @Shadow private GlTessellation prepareTessellation(CommandList c,RenderRegion r){throw new AssertionError();}
 @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",shift=At.Shift.AFTER),require=1,allow=1)
 private void shaderlod$prime(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
  if((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT) || ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !DepthPrimingR2.drawEnabled())return;
  if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
  if(!(pipeline.getSodiumPrograms() instanceof DepthProgramAccess owner))return;
  var depth=owner.shaderlod$depthProgram(pass);if(depth==null)return;
  var nativeProgram=this.activeProgram;
  var state=DepthPrimingR2.State.capture(nativeProgram.handle());if(state==null)return;
  var framebuffer=pipeline.getSodiumPrograms().getFramebuffer(pass);
  if(framebuffer.getId()!=state.drawFbo)return;
  var shader=depth.getInterface();boolean setup=false;int draws=0;
  String passName=pass==DefaultTerrainRenderPasses.SOLID?"SOLID":"CUTOUT";
  try {
   depth.bind();setup=true;shader.setupState();
   shader.setProjectionMatrix(matrices.projection());shader.setModelViewMatrix(matrices.modelView());
   if(!state.beginCoverage())return;
   var iterator=lists.iterator(false);
   boolean faceCulling=SodiumClientMod.options().performance.useBlockFaceCulling;
   while(iterator.hasNext()) {
    var list=iterator.next();var region=list.getRegion();var storage=region.getStorage(pass);if(storage==null)continue;
    fillCommandBuffer(batch,region,storage,list,camera,pass,faceCulling);if(batch.isEmpty())continue;
    sharedIndexBuffer.ensureCapacity(commands,batch.getIndexBufferSize());
    var tessellation=prepareTessellation(commands,region);
    setModelMatrixUniforms(shader,region,camera);
    executeDrawBatch(commands,tessellation,batch);draws++;
   }
  } finally {
   // Raw masks/depth are restored before callbacks that may consult native GL caches.
   state.endCoverage();
   try {if(setup)shader.resetState();} finally {
    try {nativeProgram.bind();nativeProgram.getInterface().setupState();} finally {
     // resetState bound the main target; recover native terrain ownership explicitly.
     framebuffer.bind();state.restoreBindingsAndRaster();if(DepthPrimingR2.needsAdmission(passName))state.assertRestored(nativeProgram.handle());
    }
   }
  }
  if (draws > 0) DepthPrimingR2.admission(passName,nativeProgram.handle(),depth.handle(),draws,state);
 }
}
