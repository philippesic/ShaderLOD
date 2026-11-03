package com.pp.shaderlod.optimization.makeupao.mixin;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=ProgramBuilder.class,remap=false)
public interface MakeUpAoCompileBuilderView {
    @Accessor("program") int shaderlod$aoProgramId();
}
