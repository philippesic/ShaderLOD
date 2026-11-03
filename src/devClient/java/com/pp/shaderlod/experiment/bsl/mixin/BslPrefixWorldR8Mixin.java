package com.pp.shaderlod.experiment.bsl.mixin;
import com.pp.shaderlod.experiment.bsl.BslPrefixSustainedR8;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class BslPrefixWorldR8Mixin {
 @Inject(method="setLevel",at=@At("HEAD"),require=1)private void world(CallbackInfo ci){BslPrefixSustainedR8.allMutating();}
}
