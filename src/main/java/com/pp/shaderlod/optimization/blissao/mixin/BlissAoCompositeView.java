package com.pp.shaderlod.optimization.blissao.mixin;
import com.google.common.collect.ImmutableList;import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public interface BlissAoCompositeView {@Accessor("passes")ImmutableList<?> shaderlod$passes();}
