package com.pp.shaderlod.optimization.makeupspatial.mixin;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=GlProgram.Builder.class,remap=false)
public interface SpatialBuilderAccess {
 @Accessor("program") int shaderlod$spatialProgramId();
}
