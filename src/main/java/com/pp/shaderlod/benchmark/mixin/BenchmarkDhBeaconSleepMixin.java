package com.pp.shaderlod.benchmark.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.benchmark.BenchmarkDhMaintenance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** DH3.3.3: the file executor remains occupied during this known 2000ms beacon cooldown. */
@Pseudo
@Mixin(targets="com.seibel.distanthorizons.core.render.QuadTree.LodQuadTree", remap=false)
public abstract class BenchmarkDhBeaconSleepMixin {
    @WrapOperation(method="lambda$tryRefreshRenderingBeaconsAsync$0", at=@At(value="INVOKE", target="Ljava/lang/Thread;sleep(J)V"),
            require=1, allow=1, remap=false)
    private void shaderlod$beaconCooldown(long millis, Operation<Void> original) {
        // Preserve native argument, blocking, interruption and exceptions exactly.
        var ticket = millis == 2000 ? BenchmarkDhMaintenance.beginCooldown() : null;
        try { original.call(millis); }
        finally { BenchmarkDhMaintenance.endCooldown(ticket); }
    }
}
