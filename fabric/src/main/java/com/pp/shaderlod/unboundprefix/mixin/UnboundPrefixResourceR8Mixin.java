package com.pp.shaderlod.unboundprefix.mixin;
import com.pp.shaderlod.unboundprefix.UnboundPrefixRuntime;
import net.minecraft.server.packs.resources.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ReloadableResourceManager.class)
public abstract class UnboundPrefixResourceR8Mixin {
 @Inject(method="createReload",at=@At("HEAD"),require=1)private void reload(CallbackInfoReturnable<ReloadInstance> ci){UnboundPrefixRuntime.resourceReloading(this);}
}
