package com.pp.shaderlod.optimization.sourcecache.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import java.util.Map;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ShaderPackOptions.class,remap=false)
public abstract class SourceOptionsInputMixin implements IrisCompiledSource.OptionsView {
 @Unique private IrisCompiledSource.OptionsInput shaderlod$makeupInput;
 @Inject(method="<init>(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;Ljava/util/Map;)V",at=@At("RETURN"),require=1,allow=1)
 private void capture(IncludeGraph raw,Map<String,String> changed,CallbackInfo ci){
  try{com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.capture((ShaderPackOptions)(Object)this);shaderlod$makeupInput=IrisCompiledSource.OptionsInput.capture((ShaderPackOptions)(Object)this,raw);}
  catch(RuntimeException|LinkageError unavailable){shaderlod$makeupInput=null;}
 }
 public IrisCompiledSource.OptionsInput shaderlod$makeupOptionsInput(){return shaderlod$makeupInput;}
}
