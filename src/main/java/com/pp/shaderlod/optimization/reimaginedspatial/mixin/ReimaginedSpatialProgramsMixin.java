package com.pp.shaderlod.optimization.reimaginedspatial.mixin;
import com.pp.shaderlod.optimization.reimaginedspatial.*;import com.llamalad7.mixinextras.injector.ModifyReturnValue;import com.llamalad7.mixinextras.sugar.Local;import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;import net.irisshaders.iris.pipeline.IrisRenderingPipeline;import net.irisshaders.iris.pipeline.programs.SodiumPrograms;import net.irisshaders.iris.shaderpack.programs.ProgramSource;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.injection.At;
@Mixin(value=SodiumPrograms.class,remap=false)public abstract class ReimaginedSpatialProgramsMixin {
 @ModifyReturnValue(method="createShader",at=@At("RETURN"),require=1)
 private GlProgram<?> own(GlProgram<?> p,@Local(argsOnly=true) IrisRenderingPipeline owner,@Local(argsOnly=true) SodiumPrograms.Pass pass,@Local(argsOnly=true) ProgramSource source){if(p!=null){ReimaginedExactSsrRuntime.own(owner,pass,source,p.handle());ReimaginedWorldPixelRuntime.own(owner,pass,source,p.handle());ReimaginedSpatialRuntime.own(owner,pass,source,p.handle());}return p;}
}
