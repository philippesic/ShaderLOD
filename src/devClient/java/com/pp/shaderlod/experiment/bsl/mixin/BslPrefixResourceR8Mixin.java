package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslPrefixSustainedR8;
import net.minecraft.server.packs.resources.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ReloadableResourceManager.class)
public abstract class BslPrefixResourceR8Mixin {
 @Inject(method="createReload",at=@At("HEAD"),require=1)private void reload(CallbackInfoReturnable<ReloadInstance> ci){BslPrefixSustainedR8.resourceReloading(this);}
}
