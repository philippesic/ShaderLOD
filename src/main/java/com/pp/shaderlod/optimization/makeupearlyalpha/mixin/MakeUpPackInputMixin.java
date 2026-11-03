package com.pp.shaderlod.optimization.makeupearlyalpha.mixin;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpCompiledSource;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=ShaderPack.class,remap=false)
public abstract class MakeUpPackInputMixin implements MakeUpCompiledSource.PackView {
 @Shadow @Final private ShaderPackOptions shaderPackOptions;
 @Shadow @Final private Function<AbsolutePackPath,String> sourceProvider;
 @Shadow @Final private ShaderProperties shaderProperties;
 @Unique private MakeUpCompiledSource.PackInput shaderlod$makeupInput;
 @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="CTOR_HEAD",args="enforce=POST_DELEGATE",unsafe=true),require=1,allow=1)
 private void begin(Path path,Map<String,String> changed,ImmutableList<StringPair> defines,boolean flag,CallbackInfo ci){MakeUpCompiledSource.begin(this,path);}
 @Inject(method="readProperties(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;",at=@At("RETURN"),require=3,allow=3)
 private static void read(Path path,String name,CallbackInfoReturnable<String> ci){MakeUpCompiledSource.propertyRead(name,ci.getReturnValue());}
 @Inject(method="lambda$new$2",at=@At("RETURN"),require=1,allow=1)
 private void properties(List<StringPair> defines,String text,CallbackInfoReturnable<ShaderProperties> ci){MakeUpCompiledSource.properties(this,shaderPackOptions,defines,text,ci.getReturnValue());}
 @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At("RETURN"),require=1,allow=1)
 private void finish(Path path,Map<String,String> changed,ImmutableList<StringPair> defines,boolean flag,CallbackInfo ci){
  try{shaderlod$makeupInput=MakeUpCompiledSource.finish(this,shaderPackOptions,sourceProvider,shaderProperties,defines);}
  catch(RuntimeException|LinkageError unavailable){shaderlod$makeupInput=null;}
 }
 public MakeUpCompiledSource.PackInput shaderlod$makeupPackInput(){return shaderlod$makeupInput;}
}
