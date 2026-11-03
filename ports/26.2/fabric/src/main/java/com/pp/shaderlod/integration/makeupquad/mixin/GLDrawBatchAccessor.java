package com.pp.shaderlod.integration.makeupquad.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.caffeinemc.mods.sodium.client.gpu.device.batch.GLDrawBatch",remap=false)
public interface GLDrawBatchAccessor {
    @Accessor("pElementPointer") long shaderlod$pointers();
    @Accessor("pElementCount") long shaderlod$counts();
    @Accessor("pBaseVertex") long shaderlod$bases();
}
