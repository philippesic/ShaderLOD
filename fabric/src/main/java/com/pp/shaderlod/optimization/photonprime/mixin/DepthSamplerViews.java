package com.pp.shaderlod.optimization.photonprime.mixin;
import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.sampler.SamplerBinding;
import net.irisshaders.iris.gl.texture.TextureType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Exact Photon nativeVS noisetex and coverage atlas sampler write set. */
public final class DepthSamplerViews {private DepthSamplerViews(){}
 @Mixin(value=SodiumShader.class,remap=false)public interface ShaderView{@Accessor("samplers") ProgramSamplers shaderlodPhoton$samplers();}
 @Mixin(value=ProgramSamplers.class,remap=false)public interface ProgramView{@Accessor("samplerBindings") ImmutableList<SamplerBinding> shaderlodPhoton$bindings();}
 @Mixin(value=SamplerBinding.class,remap=false)public interface BindingView{@Accessor("textureUnit") int shaderlodPhoton$unit();@Accessor("textureType") TextureType shaderlodPhoton$type();}
}
