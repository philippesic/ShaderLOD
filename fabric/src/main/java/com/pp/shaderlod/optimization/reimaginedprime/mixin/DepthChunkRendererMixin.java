package com.pp.shaderlod.optimization.reimaginedprime.mixin;
import com.pp.shaderlod.optimization.reimaginedprime.ReimaginedDepthPriming;
import com.pp.shaderlod.optimization.reimaginedprime.DepthProgramAccess;
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
 private void shaderlod$reimaginedprime(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
  if((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT) || ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !ReimaginedDepthPriming.requested())return;
  if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
  if(!(pipeline.getSodiumPrograms() instanceof DepthProgramAccess owner))return;
  var depth=owner.shaderlod$reimagineddepthProgram(pass);if(depth==null)return;
  var nativeProgram=this.activeProgram;if(nativeProgram==null || pipeline.getSodiumPrograms().getProgram(pass)!=nativeProgram)return;
  if(!ReimaginedDepthPriming.drawEnabled())return;
  ReimaginedDepthPriming.State state;try{state=ReimaginedDepthPriming.State.capture(nativeProgram.handle(),pass==DefaultTerrainRenderPasses.CUTOUT);}catch(RuntimeException|LinkageError unavailable){ReimaginedDepthPriming.disable();return;}if(state==null)return;
  var framebuffer=pipeline.getSodiumPrograms().getFramebuffer(pass);
  if(framebuffer.getId()!=state.drawFbo)return;
  var shader=depth.getInterface();
  String passName=pass==DefaultTerrainRenderPasses.SOLID?"SOLID":"CUTOUT";
  com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.run(new com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.Work(){
   boolean setup;int draws;
   public boolean prepare(){setup=true;depth.bind();shader.setupState();shader.setProjectionMatrix(matrices.projection());shader.setModelViewMatrix(matrices.modelView());return state.beginCoverage();}
   public void prime(com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.Submission submission){
    var iterator=lists.iterator(false);boolean faceCulling=SodiumClientMod.options().performance.useBlockFaceCulling;
    while(iterator.hasNext()){var list=iterator.next();var region=list.getRegion();var storage=region.getStorage(pass);if(storage==null)continue;
     fillCommandBuffer(batch,region,storage,list,camera,pass,faceCulling);if(batch.isEmpty())continue;
     sharedIndexBuffer.ensureCapacity(commands,batch.getIndexBufferSize());var tessellation=prepareTessellation(commands,region);setModelMatrixUniforms(shader,region,camera);
     submission.draw(()->executeDrawBatch(commands,tessellation,batch));draws++;ReimaginedDepthPriming.assertNoGlError("depth submission");
    }
   }
   public void restore(){
    try{state.endCoverage();}finally{try{if(setup)shader.resetState();}finally{try{nativeProgram.bind();nativeProgram.getInterface().setupState();}finally{try{framebuffer.bind();}finally{state.restoreBindingsAndRaster();}}}}
   }
   public void verify(){state.assertRestored(nativeProgram.handle());ReimaginedDepthPriming.assertNoGlError("depth restore");if(draws>0)ReimaginedDepthPriming.admission(passName,nativeProgram.handle(),depth.handle(),draws,state);}
   public void disable(){ReimaginedDepthPriming.disable();}
  });
 }
}
