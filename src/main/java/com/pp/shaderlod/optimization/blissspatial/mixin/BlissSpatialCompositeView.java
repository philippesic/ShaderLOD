package com.pp.shaderlod.optimization.blissspatial.mixin;
import com.google.common.collect.ImmutableList;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public interface BlissSpatialCompositeView {@Accessor("passes")ImmutableList<?> shaderlod$passes();}
