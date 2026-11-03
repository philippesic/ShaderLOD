package com.pp.shaderlod.sildurprefix.mixin;
import com.pp.shaderlod.sildurprefix.SildurPrefixRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class SildurPrefixWorldR8Mixin {
 @Inject(method="setLevel",at=@At("HEAD"),require=1)private void world(CallbackInfo ci){SildurPrefixRuntime.allMutating();}
}
