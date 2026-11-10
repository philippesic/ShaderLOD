package com.pp.shaderlod.integration.mixin;

import com.pp.shaderlod.storage.NativeWriteScope;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Iris.class, remap = false)
@SuppressWarnings("UnusedMethod")
public abstract class IrisOptionsMixin {
  @Redirect(
      method = "tryUpdateConfigPropertiesFile(Ljava/nio/file/Path;Ljava/util/Properties;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Ljava/nio/file/Files;newOutputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"),
      require = 1,
      remap = false)
  private static OutputStream shaderlod$journalOptions(Path path, OpenOption[] options)
      throws IOException {
    return NativeWriteScope.output(path, options);
  }

  @Redirect(
      method = "tryUpdateConfigPropertiesFile(Ljava/nio/file/Path;Ljava/util/Properties;)V",
      at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;delete(Ljava/nio/file/Path;)V"),
      require = 1,
      remap = false)
  private static void shaderlod$journalDelete(Path path) throws IOException {
    NativeWriteScope.delete(path);
  }
}
