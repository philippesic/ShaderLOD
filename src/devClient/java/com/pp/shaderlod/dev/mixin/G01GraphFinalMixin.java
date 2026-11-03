package com.pp.shaderlod.dev.mixin;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.pp.shaderlod.dev.G01GraphProbe;
import java.util.Set;
import java.util.function.Supplier;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.targets.RenderTargets;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.irisshaders.iris.pathways.CenterDepthSampler;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=FinalPassRenderer.class,remap=false)
public abstract class G01GraphFinalMixin {
 @Inject(method="<init>",at=@At("RETURN"),require=1)
 private void constructed(WorldRenderingPipeline pipeline,ProgramSet set,RenderTargets targets,TextureAccess noise,ShaderStorageBufferHolder ssbo,FrameUpdateNotifier notifier,ImmutableSet<Integer> flipped,CenterDepthSampler center,Supplier<ShadowRenderTargets> shadows,Object2ObjectMap<String,TextureAccess> custom,Object2ObjectMap<String,TextureAccess> irisCustom,Set<GlImage> images,ImmutableSet<Integer> once,CustomUniforms uniforms,CallbackInfo ci){G01GraphProbe.constructed(this,pipeline,set,targets,flipped,once);}
 @Inject(method="renderFinalPass()V",at=@At("HEAD"),require=1)
 private void begin(CallbackInfo ci){G01GraphProbe.begin(this);}
 @WrapOperation(method="renderFinalPass()V",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/platform/GlStateManager;_glCopyTexSubImage2D(IIIIIIII)V"),require=1)
 private void copy(int target,int level,int xo,int yo,int x,int y,int width,int height,Operation<Void> original){original.call(target,level,xo,yo,x,y,width,height);G01GraphProbe.copied(this,target,level,xo,yo,x,y,width,height);}
 @Inject(method="renderFinalPass()V",at=@At("RETURN"),require=1)
 private void end(CallbackInfo ci){G01GraphProbe.end(this);}
}
