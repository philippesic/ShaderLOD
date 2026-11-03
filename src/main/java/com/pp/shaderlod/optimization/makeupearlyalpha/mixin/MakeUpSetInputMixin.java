package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpCompiledSource;
import java.util.function.Function;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=ProgramSet.class,remap=false)
public abstract class MakeUpSetInputMixin implements MakeUpCompiledSource.SetView {
 @Unique private MakeUpCompiledSource.SetInput shaderlod$makeupInput;
 @Inject(method="<init>",at=@At("RETURN"),require=1,allow=1)
 private void capture(AbsolutePackPath directory,Function<AbsolutePackPath,String> provider,ShaderProperties properties,ShaderPack pack,CallbackInfo ci){shaderlod$makeupInput=new MakeUpCompiledSource.SetInput(directory,provider,properties,pack);}
 public MakeUpCompiledSource.SetInput shaderlod$makeupSetInput(){return shaderlod$makeupInput;}
}
