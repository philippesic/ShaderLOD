package com.pp.shaderlod.optimization.unboundspatial.mixin;
import com.pp.shaderlod.optimization.unboundspatial.*;import com.llamalad7.mixinextras.injector.ModifyReturnValue;import com.llamalad7.mixinextras.sugar.Local;import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.pipeline.programs.SodiumPrograms;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=SodiumPrograms.class,remap=false)public abstract class UnboundSpatialProgramsMixin {
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> p,@Local(argsOnly=true) IrisRenderingPipeline owner,@Local(argsOnly=true) SodiumPrograms.Pass pass,@Local(argsOnly=true) ProgramSource source){if(p!=null){UnboundExactSsrRuntime.own(owner,pass,source,p.handle());UnboundWorldPixelRuntime.own(owner,pass,source,p.handle());UnboundSpatialRuntime.own(owner,pass,source,p.handle());}return p;}
}
