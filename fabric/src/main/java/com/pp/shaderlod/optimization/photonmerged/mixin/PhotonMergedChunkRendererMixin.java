package com.pp.shaderlod.optimization.photonmerged.mixin;

import com.pp.shaderlod.optimization.photonmerged.*;
import com.pp.shaderlod.optimization.makeupmerged.MergePlan;
import com.pp.shaderlod.optimization.makeupmerged.MergedBuffers;
import com.pp.shaderlod.optimization.makeupmerged.BufferBindings;
import com.pp.shaderlod.optimization.makeupmerged.MergeUniforms;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.buffer.GlMutableBuffer;
import net.caffeinemc.mods.sodium.client.gl.device.*;
import net.caffeinemc.mods.sodium.client.render.chunk.*;
import net.caffeinemc.mods.sodium.client.render.chunk.data.SectionRenderDataStorage;
import net.caffeinemc.mods.sodium.client.gl.tessellation.GlTessellation;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.*;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegion;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.*;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;

/** Replaces only eligible current-world SOLID/CUTOUT native loop after native program/FBO setup. */
@Mixin(value=DefaultChunkRenderer.class,remap=false)
public abstract class PhotonMergedChunkRendererMixin extends ShaderChunkRenderer {
    protected PhotonMergedChunkRendererMixin(RenderDevice d,ChunkVertexType v){super(d,v);}
    @Shadow @Final private MultiDrawBatch batch;
    @Shadow @Final private SharedQuadIndexBuffer sharedIndexBuffer;
    @Shadow private static void fillCommandBuffer(MultiDrawBatch b,RenderRegion r,SectionRenderDataStorage s,ChunkRenderList l,CameraTransform c,TerrainRenderPass p,boolean f){throw new AssertionError();}
    @Shadow private static void setModelMatrixUniforms(ChunkShaderInterface s,RenderRegion r,CameraTransform c){throw new AssertionError();}
    @Shadow private static void executeDrawBatch(CommandList c,GlTessellation t,MultiDrawBatch b){throw new AssertionError();}
    @Shadow private GlTessellation prepareTessellation(CommandList c,RenderRegion r){throw new AssertionError();}
    @Unique private MergedBuffers shaderlodPhoton$merged;
    @Unique private boolean shaderlodPhoton$reported;
    @Unique private java.util.Set<TerrainRenderPass> shaderlodPhoton$drawReceipts;
    @Inject(method="render",at=@At(value="INVOKE",target="Lnet/caffeinemc/mods/sodium/client/render/chunk/shader/ChunkShaderInterface;setModelViewMatrix(Lorg/joml/Matrix4fc;)V",shift=At.Shift.AFTER),cancellable=true,require=1,allow=1,order=1100)
    private void shaderlodPhoton$merge(ChunkRenderMatrices matrices,CommandList commands,ChunkRenderListIterable lists,TerrainRenderPass pass,CameraTransform camera,CallbackInfo ci) {
        if((pass!=DefaultTerrainRenderPasses.SOLID && pass!=DefaultTerrainRenderPasses.CUTOUT)||ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
        if(!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))return;
        if(!(pipeline.getSodiumPrograms() instanceof PhotonMergedProgramAccess access))return;
        if(this.vertexFormat.getStride()!=net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getVertexFormat().getVertexFormat().getStride())return;
        var original=this.activeProgram;
        if(access.photonOriginalProgram(pass)!=original || pipeline.getSodiumPrograms().getProgram(pass)!=original)return;
        var framebuffer=pipeline.getSodiumPrograms().getFramebuffer(pass);
        if(framebuffer.getId()!=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING))return;
        var derivative=access.photonMergedProgram(pass);if(derivative==null)return;
        var bindings=BufferBindings.capture(this.device);if(bindings==null)return;
        try(bindings) {
            MergePlan plan;var sources=new ArrayList<GlMutableBuffer>();
            try {
                plan=new MergePlan(this.vertexFormat.getStride());
                var iterator=lists.iterator(false);boolean culling=SodiumClientMod.options().performance.useBlockFaceCulling;
                while(iterator.hasNext()) {
                    var list=iterator.next();var region=list.getRegion();var storage=region.getStorage(pass);if(storage==null)continue;
                    fillCommandBuffer(batch,region,storage,list,camera,pass,culling);if(batch.isEmpty())continue;
                    if(region.getResources()==null||!(region.getResources().getGeometryBuffer() instanceof GlMutableBuffer source)
                            ||source.getActiveMapping()!=null)throw new IllegalArgumentException("unknown regional buffer");
                    int[] counts=new int[batch.size],bases=new int[batch.size];long[] pointers=new long[batch.size];
                    for(int i=0;i<batch.size;i++) {
                        counts[i]=MemoryUtil.memGetInt(batch.pElementCount+(long)i*4);bases[i]=MemoryUtil.memGetInt(batch.pBaseVertex+(long)i*4);
                        pointers[i]=MemoryUtil.memGetAddress(batch.pElementPointer+((long)i<<Pointer.POINTER_SHIFT));
                    }
                    float x=(region.getOriginX()-camera.intX)-camera.fracX;
                    float y=(region.getOriginY()-camera.intY)-camera.fracY;
                    float z=(region.getOriginZ()-camera.intZ)-camera.fracZ;
                    plan.add(source.getSize(),counts,bases,pointers,x,y,z);sources.add(source);
                }
            }catch(IllegalArgumentException|ArithmeticException invalid){shaderlodPhoton$fallback(invalid.getMessage());return;}
            if(plan.spans.size()<2)return; // Native empty/single-region paths retain their own setup and draw.
            if(shaderlodPhoton$merged==null)shaderlodPhoton$merged=new MergedBuffers();
            sharedIndexBuffer.ensureCapacity(commands,plan.maxElements);
            boolean ready;
            try {ready=shaderlodPhoton$merged.prepare(commands,plan,sources,this.vertexFormat,sharedIndexBuffer.getBufferObject());}
            catch(RuntimeException failed){shaderlodPhoton$fallback(failed.toString());return;}
            if(!ready){shaderlodPhoton$fallback("copy/table preparation rejected");return;}
            var activation=MergeUniforms.activate(original,derivative,matrices,framebuffer);
            if(activation==null){shaderlodPhoton$fallback("derived program/ABI activation rejected");return;}
            this.activeProgram=activation.program();boolean submitted=false;
            try(activation) {
                try {shaderlodPhoton$merged.bind(activation.uniforms(),plan);}
                catch(RuntimeException failed){shaderlodPhoton$fallback(failed.toString());return;}
                // No catch/replay after draw begins: ambiguous GL submission cannot safely replay geometry.
                shaderlodPhoton$merged.draw(commands,activation.uniforms());submitted=true;
            }finally{this.activeProgram=original;}
            if(submitted){
                super.end(pass);ci.cancel();
                if(shaderlodPhoton$drawReceipts==null)shaderlodPhoton$drawReceipts=new java.util.HashSet<>();
                if(shaderlodPhoton$drawReceipts.add(pass)){System.out.println("[ShaderLOD merged] pass="+pass+" nativeProductCoalescerEligible=true nativeRegions="+plan.spans.size()+" mergedCalls=1 subdraws="+plan.commands+" copiedBytes="+plan.copiedBytes+" allocatedBytes="+((long)plan.vertices*plan.stride)+" pages="+plan.pages()+" program="+derivative.handle());}
            }
        }
    }
    @Unique private void shaderlodPhoton$fallback(String reason){if(!shaderlodPhoton$reported){shaderlodPhoton$reported=true;System.out.println("[ShaderLOD merged] native fallback: "+reason);}}
    @Inject(method="delete",at=@At("HEAD"),require=1,allow=1)
    private void shaderlodPhoton$delete(CommandList commands,CallbackInfo ci){if(shaderlodPhoton$merged!=null){shaderlodPhoton$merged.delete(commands);shaderlodPhoton$merged=null;}}
}
