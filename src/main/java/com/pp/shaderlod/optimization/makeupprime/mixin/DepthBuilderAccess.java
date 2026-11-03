package com.pp.shaderlod.optimization.makeupprime.mixin;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GlProgram.Builder.class,remap=false)
public interface DepthBuilderAccess {
 @Accessor("program") int shaderlod$makeupdepthprogramId();
}
