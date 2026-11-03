package com.pp.shaderlod.optimization.makeupprime.mixin;
import com.pp.shaderlod.optimization.makeupprime.MakeUpDepthPriming;
import com.pp.shaderlod.optimization.makeupprime.DepthProgramAccess;
import com.pp.shaderlod.optimization.makeupprime.C01Observation;
import com.pp.shaderlod.optimization.makeupprime.C01Prefix;
import com.pp.shaderlod.optimization.makeupprime.C01Bounds;
import org.spongepowered.asm.mixin.injection.Redirect;
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
 @org.spongepowered.asm.mixin.Unique private RenderRegion shaderlod$c01Region;
 @org.spongepowered.asm.mixin.Unique private CameraTransform shaderlod$c01Camera;
 @Shadow @Final private MultiDrawBatch batch;
 @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;
 @Shadow private static void fillCommandBuffer(MultiDrawBatch b,RenderRegion r,SectionRenderDataStorage s,ChunkRenderList l,CameraTransform c,TerrainRenderPass p,boolean f){throw new AssertionError();}
 @Shadow private static void setModelMatrixUniforms(ChunkShaderInterface s,RenderRegion r,CameraTransform c){throw new AssertionError();}
 @Shadow private static void executeDrawBatch(CommandList c,GlTessellation t,MultiDrawBatch b){throw new AssertionError();}
 @Shadow private GlTessellation prepareTessellation(CommandList c,RenderRegion r){throw new AssertionError();}
 @Shadow private static int getVisibleFaces(int x,int y,int z,int sx,int sy,int sz){throw new AssertionError();}
 @Shadow private static void addNonIndexedDrawCommands(MultiDrawBatch b,long p,int mask){throw new AssertionError();}
 @Inject(method="render",at=@At("HEAD"),require=1)
 private void shaderlod$c01Begin(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform v,CallbackInfo ci){
  C01Bounds.beginFrame(p==DefaultTerrainRenderPasses.SOLID&&!ShadowRenderingState.areShadowsCurrentlyBeingRendered());
  C01Prefix.beginFrame(p==DefaultTerrainRenderPasses.SOLID&&!ShadowRenderingState.areShadowsCurrentlyBeingRendered());
  if(p==DefaultTerrainRenderPasses.SOLID&&!ShadowRenderingState.areShadowsCurrentlyBeingRendered())C01Observation.beginFrame();
 }
 @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;fillCommandBuffer(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion;Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataStorage;Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/ChunkRenderList;Lnet/caffeinemc/mods/sodium/client/render/viewport/CameraTransform;Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;Z)V"),require=1,allow=1)
 private void shaderlod$c01BoundsPrepare(MultiDrawBatch b,RenderRegion r,SectionRenderDataStorage s,ChunkRenderList l,CameraTransform c,TerrainRenderPass p,boolean f){
  fillCommandBuffer(b,r,s,l,c,p,f);
  if(C01Bounds.active()){shaderlod$c01Region=r;shaderlod$c01Camera=c;}
  C01Bounds.prepare(r,s,l,c,f,b,DepthChunkRendererMixin::getVisibleFaces);
 }
 @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer;executeDrawBatch(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlTessellation;Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;)V"),require=1,allow=1)
 private void shaderlod$c01PrefixDraw(CommandList commands,GlTessellation tessellation,MultiDrawBatch draws) {
  if(C01Bounds.active()){
   final var region=shaderlod$c01Region;final var camera=shaderlod$c01Camera;
   final var shader=this.activeProgram.getInterface();final var indexSize=draws.getIndexBufferSize();
   C01Bounds.draw(this.activeProgram.handle(),draws,b->executeDrawBatch(commands,tessellation,b),work->{
    sharedIndexBuffer.ensureCapacity(commands,indexSize);
    var live=prepareTessellation(commands,region);setModelMatrixUniforms(shader,region,camera);
    work.accept(b->executeDrawBatch(commands,live,b));
   });
  }
  else C01Prefix.draw(this.activeProgram.handle(),draws,()->executeDrawBatch(commands,tessellation,draws));
 }
 @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V"),require=1)
 private void shaderlod$c01PrefixEnd(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform v,CallbackInfo ci){try{C01Bounds.endFrame(this.activeProgram.handle());C01Prefix.endFrame();}finally{shaderlod$c01Region=null;shaderlod$c01Camera=null;}}
 @Inject(method="render",at=@At("RETURN"),require=1)
 private void shaderlod$c01End(ChunkRenderMatrices m,CommandList c,ChunkRenderListIterable l,TerrainRenderPass p,CameraTransform v,CallbackInfo ci){
  if(p==DefaultTerrainRenderPasses.SOLID&&!ShadowRenderingState.areShadowsCurrentlyBeingRendered())C01Observation.endFrame();
 }
 @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V"),require=1)
 private void shaderlod$c01AfterNative(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
  if(pass!=DefaultTerrainRenderPasses.SOLID||ShadowRenderingState.areShadowsCurrentlyBeingRendered()||!C01Observation.observing())return;
  var program=this.activeProgram;if(program==null)return;
  if(!C01Observation.nativeProgram(program.handle()))return;
  var state=MakeUpDepthPriming.State.capture(program.handle(),false);if(state==null)return;
  var shader=program.getInterface();boolean faceCulling=SodiumClientMod.options().performance.useBlockFaceCulling;
  org.lwjgl.opengl.GL11C.glColorMask(false,false,false,false);org.lwjgl.opengl.GL11C.glDepthMask(false);
  try {
      var observeLists=lists.iterator(false);
      while(observeLists.hasNext()) {
       var list=observeLists.next();var region=list.getRegion();var storage=region.getStorage(pass);if(storage==null)continue;
       fillCommandBuffer(batch,region,storage,list,camera,pass,faceCulling);
       long expected=C01Observation.appendSignature(0xcbf29ce484222325L,batch),observed=0xcbf29ce484222325L;
       int expectedCount=batch.size,observedCount=0;if(expectedCount==0)continue;
       sharedIndexBuffer.ensureCapacity(commands,batch.getIndexBufferSize());var tessellation=prepareTessellation(commands,region);setModelMatrixUniforms(shader,region,camera);
       var sections=list.sectionsWithGeometryIterator(false);if(sections==null)continue;
       while(sections.hasNext()) {
        int section=sections.nextByteAsInt();long pointer=storage.getDataPointer(section);
        int x=region.getChunkX()+net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackX(section);
        int y=region.getChunkY()+net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackY(section);
        int z=region.getChunkZ()+net.caffeinemc.mods.sodium.client.render.chunk.LocalSectionIndex.unpackZ(section);
        int mask=(faceCulling?getVisibleFaces(camera.intX,camera.intY,camera.intZ,x,y,z):net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing.ALL)
          &net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataUnsafe.getSliceMask(pointer);
        if(mask==0)continue;batch.clear();addNonIndexedDrawCommands(batch,pointer,mask);
        observed=C01Observation.appendSignature(observed,batch);observedCount+=batch.size;
        boolean near=camera.intX>=x*16-8&&camera.intX<=x*16+24&&camera.intY>=y*16-8&&camera.intY<=y*16+24&&camera.intZ>=z*16-8&&camera.intZ<=z*16+24;
        C01Observation.section(x,y,z,pointer,near,batch,()->executeDrawBatch(commands,tessellation,batch));
       }
       C01Observation.verifyRegion(expected,observed,expectedCount,observedCount);
      }
  } finally {
   org.lwjgl.opengl.GL11C.glDepthMask(true);org.lwjgl.opengl.GL11C.glColorMask(true,true,true,true);
   state.restoreBindingsAndRaster();state.assertRestored(program.handle());MakeUpDepthPriming.assertNoGlError("C01 post-native observation restore");
  }
 }
 @Inject(method="render",order=1000,at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",shift=At.Shift.AFTER),require=1,allow=1)
 private void shaderlod$makeupprime(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
  if(pass!=DefaultTerrainRenderPasses.SOLID || ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !MakeUpDepthPriming.requested())return;
  if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
  if(!(pipeline.getSodiumPrograms() instanceof DepthProgramAccess owner))return;
  var depth=owner.shaderlod$makeupdepthdepthProgram(pass);if(depth==null)return;
  var nativeProgram=this.activeProgram;if(nativeProgram==null || pipeline.getSodiumPrograms().getProgram(pass)!=nativeProgram)return;
  if(!MakeUpDepthPriming.drawEnabled())return;
  MakeUpDepthPriming.State state;try{state=MakeUpDepthPriming.State.capture(nativeProgram.handle(),pass==DefaultTerrainRenderPasses.CUTOUT);}catch(RuntimeException|LinkageError unavailable){MakeUpDepthPriming.disable();return;}if(state==null)return;
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
     submission.draw(()->executeDrawBatch(commands,tessellation,batch));draws++;MakeUpDepthPriming.assertNoGlError("depth submission");
    }

   }
   public void restore(){
    try{state.endCoverage();}finally{try{if(setup)shader.resetState();}finally{try{nativeProgram.bind();nativeProgram.getInterface().setupState();}finally{try{framebuffer.bind();}finally{state.restoreBindingsAndRaster();}}}}
   }
   public void verify(){state.assertRestored(nativeProgram.handle());MakeUpDepthPriming.assertNoGlError("depth restore");if(draws>0)MakeUpDepthPriming.admission(passName,nativeProgram.handle(),depth.handle(),draws,state);}
   public void disable(){MakeUpDepthPriming.disable();}
  });
 }
}
