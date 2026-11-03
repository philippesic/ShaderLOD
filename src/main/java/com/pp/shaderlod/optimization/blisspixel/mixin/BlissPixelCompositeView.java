package com.pp.shaderlod.optimization.blisspixel.mixin;
import com.google.common.collect.ImmutableList;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public interface BlissPixelCompositeView {@Accessor("passes")ImmutableList<?> shaderlod$passes();}
