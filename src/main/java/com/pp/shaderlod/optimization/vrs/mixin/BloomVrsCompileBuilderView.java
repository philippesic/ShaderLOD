package com.pp.shaderlod.optimization.vrs.mixin;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ProgramBuilder.class,remap=false)
public interface BloomVrsCompileBuilderView {
    @Accessor("program") int shaderlod$vrsProgramId();
}
