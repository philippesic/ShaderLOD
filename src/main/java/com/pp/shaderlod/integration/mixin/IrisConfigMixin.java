package com.pp.shaderlod.integration.mixin;

import com.pp.shaderlod.storage.FileJournal;
import com.pp.shaderlod.storage.NativeWriteScope;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import net.irisshaders.iris.config.IrisConfig;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IrisConfig.class, remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class IrisConfigMixin {
  @Inject(method = "load()V", at = @At("HEAD"), require = 1, remap = false)
  private void shaderlod$recoverBeforeNativeLoad(
      @SuppressWarnings("UnusedVariable") CallbackInfo callback) throws IOException {
    if (!NativeWriteScope.active()) {
      Path root = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
      Path journal = root.resolve("shaderlod-recovery.json");
      if (Files.exists(journal)) {
        FileJournal.recover(root, journal).restore();
      }
    }
  }

  @Redirect(
      method = "save()V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/nio/file/Files;newOutputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"),
      require = 1,
      remap = false)
  private OutputStream shaderlod$journalConfig(Path path, OpenOption[] options) throws IOException {
    return NativeWriteScope.output(path, options);
  }
}
