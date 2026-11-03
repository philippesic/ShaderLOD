package com.pp.shaderlod.optimization.makeupspatial.mixin;
import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialRuntime;
import com.pp.shaderlod.optimization.makeupspatial.SpatialProgramAccess;
import com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialState;
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
public abstract class SpatialChunkRendererMixin extends ShaderChunkRenderer {
 protected SpatialChunkRendererMixin(RenderDevice d,ChunkVertexType v){super(d,v);}
 @Shadow @Final private MultiDrawBatch batch;
 @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;
 @Shadow private static void fillCommandBuffer(MultiDrawBatch b,RenderRegion r,SectionRenderDataStorage s,ChunkRenderList l,CameraTransform c,TerrainRenderPass p,boolean f){throw new AssertionError();}
 @Shadow private static void setModelMatrixUniforms(ChunkShaderInterface s,RenderRegion r,CameraTransform c){throw new AssertionError();}
 @Shadow private static void executeDrawBatch(CommandList c,GlTessellation t,MultiDrawBatch b){throw new AssertionError();}
 @Shadow private GlTessellation prepareTessellation(CommandList c,RenderRegion r){throw new AssertionError();}
 @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="render")
 private void shaderlod$spatialScope(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform camera,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){Throwable primary=null;try{original.call(m,c,l,p,camera);}catch(RuntimeException|Error failed){primary=failed;throw failed;}finally{if(p==DefaultTerrainRenderPasses.SOLID)try{MakeUpSpatialRuntime.finish();}catch(RuntimeException|Error cleanup){if(primary==null)throw cleanup;if(primary!=cleanup)try{primary.addSuppressed(cleanup);}catch(Throwable unavailable){}}}}
 @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V",shift=At.Shift.BEFORE),require=1,allow=1)
 private void shaderlod$finishBeforeNativeEnd(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci){if(pass==DefaultTerrainRenderPasses.SOLID)MakeUpSpatialRuntime.finish();}
 @Inject(method="render",order=1000,at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",shift=At.Shift.AFTER),require=1,allow=1)
 private void shaderlod$makeupprime(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
  if(pass!=DefaultTerrainRenderPasses.SOLID || ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !MakeUpSpatialRuntime.requested())return;
  MakeUpSpatialRuntime.drawVisit();
  if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
  if(!(pipeline.getSodiumPrograms() instanceof SpatialProgramAccess owner))return;
  var depth=owner.shaderlod$spatialProgram();if(depth==null)return;
  var nativeProgram=this.activeProgram;if(nativeProgram==null || pipeline.getSodiumPrograms().getProgram(pass)!=nativeProgram)return;
  if(!MakeUpSpatialRuntime.candidatePack()){MakeUpSpatialRuntime.refuse("normal source/profile/environment changed; native replay withheld");return;}
  MakeUpSpatialState state;try{state=MakeUpSpatialState.capture(nativeProgram.handle());}catch(RuntimeException|LinkageError unavailable){MakeUpSpatialRuntime.refuse("native SOLID state capture refused");return;}if(state==null)return;
  var framebuffer=pipeline.getSodiumPrograms().getFramebuffer(pass);if(framebuffer.getId()!=state.nativeState.drawFbo)return;
  MakeUpSpatialRuntime.Session session;try{session=MakeUpSpatialRuntime.begin(pipeline,nativeProgram.handle(),depth.handle(),state.nativeState.drawFbo,state.nativeState.depthObject);}catch(RuntimeException|LinkageError bad){try{framebuffer.bind();state.restore();state.verify(nativeProgram.handle());}finally{MakeUpSpatialRuntime.refuse("native input/output session refused: "+bad);}return;}if(session==null){framebuffer.bind();state.restore();state.verify(nativeProgram.handle());return;}
  var shader=depth.getInterface();
  String passName=pass==DefaultTerrainRenderPasses.SOLID?"SOLID":"CUTOUT";
  com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.run(new com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.Work(){
   boolean setup;int draws;
   public boolean prepare(){setup=true;depth.bind();shader.setupState();shader.setProjectionMatrix(matrices.projection());shader.setModelViewMatrix(matrices.modelView());return session.prepare(depth.handle());}
   public void prime(com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass.Submission submission){
    var iterator=lists.iterator(false);boolean faceCulling=SodiumClientMod.options().performance.useBlockFaceCulling;
    while(iterator.hasNext()){var list=iterator.next();var region=list.getRegion();var storage=region.getStorage(pass);if(storage==null)continue;
     fillCommandBuffer(batch,region,storage,list,camera,pass,faceCulling);if(batch.isEmpty())continue;
     sharedIndexBuffer.ensureCapacity(commands,batch.getIndexBufferSize());var tessellation=prepareTessellation(commands,region);setModelMatrixUniforms(shader,region,camera);
     submission.draw(()->executeDrawBatch(commands,tessellation,batch));draws++;com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.assertNoGlError("depth submission");
    }
    // An empty native SOLID pass is not a completed input field; restore normally and wait for a later pass.
    if(session.returned(draws)&&MakeUpSpatialRuntime.reducedMode())session.renderRate(owner.shaderlod$rateProgram());
   }
   public void restore(){
    try{MakeUpSpatialRuntime.ready(depth.handle(),false,0,0);}finally{try{if(setup)shader.resetState();}finally{try{nativeProgram.bind();nativeProgram.getInterface().setupState();}finally{try{framebuffer.bind();}finally{state.restore();}}}}
   }
   public void verify(){state.verify(nativeProgram.handle());com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming.assertNoGlError("depth restore");session.commit(draws);}
   public void disable(){MakeUpSpatialRuntime.refuse("native spatial replay failed; restored native color retained");}
  });
 }
}
