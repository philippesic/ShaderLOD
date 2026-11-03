package com.pp.shaderlod.optimization.makeupao.mixin;

import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.Map;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.*;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=CompositeRenderer.class,remap=false)
public abstract class MakeUpAoCompileRendererMixin {
    @Shadow @Final private WorldRenderingPipeline pipeline;
    @Shadow @Final private TextureStage textureStage;
    @Shadow @Final private RenderTargets renderTargets;

    @WrapMethod(method="createProgram(Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lcom/google/common/collect/ImmutableSet;Lcom/google/common/collect/ImmutableSet;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/program/Program;")
    private Program shaderlod$compile(ProgramSource source,ImmutableSet<Integer> first,ImmutableSet<Integer> second,
            Supplier<ShadowRenderTargets> shadows,Operation<Program> original){
        try(var scope=IrisCompositeCompiledInputs.open(this,pipeline,renderTargets,source,textureStage)){
            Program program=original.call(source,first,second,shadows);scope.complete(program);return program;
        }
    }
    @WrapOperation(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchComposite(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/shaderpack/texture/TextureStage;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
    private Map<PatchShaderType,String> shaderlod$patch(String name,String vertex,String geometry,String fragment,
            TextureStage stage,Object2ObjectMap<?,?> textures,Operation<Map<PatchShaderType,String>> original){
        IrisCompositeCompiledInputs.raw(name,vertex,geometry,fragment,stage,textures);
        var result=original.call(name,vertex,geometry,fragment,stage,textures);
        IrisCompositeCompiledInputs.transformed(result);return result;
    }
    @WrapOperation(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/ProgramBuilder;begin(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/google/common/collect/ImmutableSet;)Lnet/irisshaders/iris/gl/program/ProgramBuilder;"),require=1,allow=1)
    private ProgramBuilder shaderlod$begin(String name,String vertex,String geometry,String fragment,ImmutableSet<Integer> reserved,Operation<ProgramBuilder> original){
        IrisCompositeCompiledInputs.begin(name,vertex,geometry,fragment);
        var builder=original.call(name,vertex,geometry,fragment,reserved);
        IrisCompositeCompiledInputs.builder(builder,((MakeUpAoCompileBuilderView)(Object)builder).shaderlod$aoProgramId());return builder;
    }
    @WrapOperation(method="createProgram",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/gl/program/ProgramBuilder;build()Lnet/irisshaders/iris/gl/program/Program;"),require=1,allow=1)
    private Program shaderlod$built(ProgramBuilder builder,Operation<Program> original){
        Program program=original.call(builder);
        IrisCompositeCompiledInputs.built(builder,program,((MakeUpAoCompileBuilderView)(Object)builder).shaderlod$aoProgramId());return program;
    }
    @Inject(method="destroy()V",at=@At("HEAD"),require=1)
    private void shaderlod$destroy(CallbackInfo ci){IrisCompositeCompiledInputs.destroyRenderer(this);}
}
