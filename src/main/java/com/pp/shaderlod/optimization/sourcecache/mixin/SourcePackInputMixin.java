package com.pp.shaderlod.optimization.sourcecache.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import net.irisshaders.iris.shaderpack.properties.ShaderProperties;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=ShaderPack.class,remap=false)
public abstract class SourcePackInputMixin implements IrisCompiledSource.PackView {
 @Shadow @Final private ShaderPackOptions shaderPackOptions;
 @Shadow @Final private Function<AbsolutePackPath,String> sourceProvider;
 @Shadow @Final private ShaderProperties shaderProperties;
 @Unique private IrisCompiledSource.PackInput shaderlod$makeupInput;
 @Unique private Path shaderlod$constructorPath;
 @ModifyVariable(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="CTOR_HEAD",args="enforce=POST_DELEGATE",unsafe=true),argsOnly=true,ordinal=0,require=1,allow=1)
 private static ImmutableList<StringPair> runtimeCapabilities(ImmutableList<StringPair> defines,Path path,Map<String,String> changed,ImmutableList<StringPair> originalDefines,boolean flag){
  var mipDefines=com.pp.shaderlod.optimization.mips.BslMipRuntimeGuard.constructorDefines(defines,path,com.pp.shaderlod.optimization.mips.DeadMipAdmission.apiTuple()!=null);
  var frameDefines=com.pp.shaderlod.optimization.framecopy.FrameCopyPropertyGuard.constructorDefines(mipDefines,com.pp.shaderlod.optimization.framecopy.FrameCopyPins.exactApi());
  var alphaDefines=com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaLeanGraph.constructorDefines(frameDefines,com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi());
  return com.pp.shaderlod.optimization.makeupexact.MakeUpExactGraph.constructorDefines(alphaDefines,com.pp.shaderlod.optimization.framecopy.FrameCopyPins.exactApi());
 }
 @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="CTOR_HEAD",args="enforce=POST_DELEGATE",unsafe=true),require=1,allow=1)
 private void begin(Path path,Map<String,String> changed,ImmutableList<StringPair> defines,boolean flag,CallbackInfo ci){shaderlod$constructorPath=path;IrisCompiledSource.begin(this,path);}
 @WrapOperation(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="NEW",target="(Lnet/irisshaders/iris/shaderpack/include/IncludeGraph;Ljava/util/Map;)Lnet/irisshaders/iris/shaderpack/option/ShaderPackOptions;"),require=1,allow=1)
 private ShaderPackOptions incomingOptions(IncludeGraph raw,Map<String,String> changed,Operation<ShaderPackOptions> original) {
  var incoming=original.call(raw,changed);
  return com.pp.shaderlod.optimization.CompileOptionControl.parsedShaderPackOptions(shaderlod$constructorPath,raw,changed,incoming);
 }
 @WrapOperation(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/shaderpack/include/ShaderPackSourceNames;findPresentSources(Lcom/google/common/collect/ImmutableList$Builder;Ljava/nio/file/Path;Lnet/irisshaders/iris/shaderpack/include/AbsolutePackPath;Lcom/google/common/collect/ImmutableList;)Z"),require=2,allow=2)
 private boolean discover(ImmutableList.Builder<AbsolutePackPath> output,Path path,AbsolutePackPath directory,ImmutableList<String> candidates,Operation<Boolean> original) {
  var consumed=ImmutableList.<AbsolutePackPath>builder();boolean present=original.call(consumed,path,directory,candidates);
  var roots=consumed.build();output.addAll(roots);IrisCompiledSource.discovered(directory,candidates,roots,present);return present;
 }
 @WrapOperation(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At(value="INVOKE",target="Ljava/nio/file/Files;exists(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z"),require=3,allow=3)
 private boolean exists(Path path,java.nio.file.LinkOption[] options,Operation<Boolean> original) {
  boolean exists=original.call(path,options);IrisCompiledSource.existence(path,exists);return exists;
 }
 @Inject(method="readProperties(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/lang/String;",at=@At("RETURN"),require=3,allow=3)
 private static void read(Path path,String name,CallbackInfoReturnable<String> ci){IrisCompiledSource.propertyRead(name,ci.getReturnValue());}
 @ModifyVariable(method="lambda$new$2",at=@At("HEAD"),argsOnly=true,ordinal=0,require=1,allow=1)
 private List<StringPair> propertyCapabilities(List<StringPair> defines){
  return com.pp.shaderlod.optimization.framecopy.FrameCopyPropertyGuard.defines(defines,com.pp.shaderlod.optimization.framecopy.FrameCopyPins.exactApi());
 }
 @Inject(method="lambda$new$2",at=@At("RETURN"),require=1,allow=1)
 private void properties(List<StringPair> defines,String text,CallbackInfoReturnable<ShaderProperties> ci){IrisCompiledSource.properties(this,shaderPackOptions,defines,text,ci.getReturnValue());}
 @Inject(method="<init>(Ljava/nio/file/Path;Ljava/util/Map;Lcom/google/common/collect/ImmutableList;Z)V",at=@At("RETURN"),require=1,allow=1)
 private void finish(Path path,Map<String,String> changed,ImmutableList<StringPair> defines,boolean flag,CallbackInfo ci){
  try{shaderlod$makeupInput=IrisCompiledSource.finish(this,shaderPackOptions,sourceProvider,shaderProperties,defines);}
  catch(RuntimeException|LinkageError unavailable){shaderlod$makeupInput=null;}
 }
 public IrisCompiledSource.PackInput shaderlod$makeupPackInput(){return shaderlod$makeupInput;}
}
