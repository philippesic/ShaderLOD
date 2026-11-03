package com.pp.shaderlod.optimization.sildurao.mixin;
import net.irisshaders.iris.gl.program.Program;import net.irisshaders.iris.gl.framebuffer.ViewportData;import com.google.common.collect.ImmutableSet;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer$Pass",remap=false)
public interface SildurAoPassView {
 @Accessor("name")String shaderlod$name();@Accessor("program")Program shaderlod$program();@Accessor("drawBuffers")int[] shaderlod$drawBuffers();
 @Accessor("viewportScale")ViewportData shaderlod$scale();@Accessor("mipmappedBuffers")ImmutableSet<Integer> shaderlod$mips();
}
