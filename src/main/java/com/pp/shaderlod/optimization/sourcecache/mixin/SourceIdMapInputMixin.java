package com.pp.shaderlod.optimization.sourcecache.mixin;

import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** ID-map properties affect transformed shader inputs as well as material fallback. */
@Mixin(targets="net.irisshaders.iris.shaderpack.IdMap",remap=false)
public abstract class SourceIdMapInputMixin {
 @Inject(method="readProperties(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;",at=@At("RETURN"),require=3,allow=3)
 private static void read(Path path,String name,CallbackInfoReturnable<String> ci) {
  IrisCompiledSource.propertyRead(name,ci.getReturnValue());
 }
}
