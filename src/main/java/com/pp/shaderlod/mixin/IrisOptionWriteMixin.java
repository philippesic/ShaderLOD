package com.pp.shaderlod.mixin;

import com.pp.shaderlod.integration.PackSelectionTransaction;
import java.nio.file.Path;
import java.util.Properties;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Iris 1.8.12 NeoForge / 1.8.8 Fabric: this writer returns before GPU compilation. */
@Mixin(value = Iris.class, remap = false)
public abstract class IrisOptionWriteMixin {
    @Inject(method = "tryUpdateConfigPropertiesFile(Ljava/nio/file/Path;Ljava/util/Properties;)V",
            at = @At("HEAD"), remap = false, require = 1)
    private static void shaderlod$startOwnedOptions(Path path, Properties values, CallbackInfo ci) {
        PackSelectionTransaction.irisOptionsWriteStarted(path, values);
    }
    @Inject(method = "tryUpdateConfigPropertiesFile(Ljava/nio/file/Path;Ljava/util/Properties;)V",
            at = @At("RETURN"), remap = false, require = 1)
    private static void shaderlod$witnessOwnedOptions(Path path, Properties values, CallbackInfo ci) {
        PackSelectionTransaction.irisOptionsWritten(path, values);
    }
}
