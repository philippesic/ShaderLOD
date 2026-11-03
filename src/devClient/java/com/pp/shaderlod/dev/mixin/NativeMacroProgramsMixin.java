package com.pp.shaderlod.dev.mixin;

import com.pp.shaderlod.dev.NativeMacroInspection;
import com.llamalad7.mixinextras.sugar.Local;
import net.caffeinemc.mods.sodium.client.gl.shader.*;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.shaderpack.programs.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import java.util.Map;

/** Read-only late argument observer. No replacement source, resolver lookup or generated certificate. */
@Mixin(value=SodiumPrograms.class,remap=false)
public abstract class NativeMacroProgramsMixin {
    @ModifyArgs(method="transformShaders",order=2000,at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pipeline/transform/TransformPatcher;patchSodium(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/irisshaders/iris/gl/blending/AlphaTest;Lit/unimi/dsi/fastutil/objects/Object2ObjectMap;)Ljava/util/Map;"),require=1,allow=1)
    private void observe(Args a,@Local(argsOnly=true)ProgramSource source,@Local(argsOnly=true)ProgramSet set){
        NativeMacroInspection.prepare(this,source,set,a.get(6),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5),a.get(7));
    }
    @Inject(method="transformShaders",at=@At("RETURN"),order=2000,require=1)
    private void transformed(ProgramSource source,AlphaTest alpha,ProgramSet set,CallbackInfoReturnable<Map<PatchShaderType,String>> ci){NativeMacroInspection.transformed(this,source,set,alpha,ci.getReturnValue());}
    @Inject(method="createGlShaders",at=@At("RETURN"),order=2000,require=1)
    private void compiled(String name,Map<PatchShaderType,String> input,CallbackInfoReturnable<Map<PatchShaderType,GlShader>> ci){NativeMacroInspection.compiled(input,ci.getReturnValue());}
    @Inject(method="createShader",at=@At("RETURN"),order=2000,require=1)
    private void linked(CallbackInfoReturnable<GlProgram<?>> ci,@Local(argsOnly=true)IrisRenderingPipeline pipeline,
            @Local(argsOnly=true)SodiumPrograms.Pass pass,@Local(argsOnly=true)ProgramSource source,
            @Local(argsOnly=true)AlphaTest alpha,@Local(argsOnly=true)Map<PatchShaderType,GlShader> shaders){
        var value=ci.getReturnValue();NativeMacroInspection.linked(this,pipeline,pass,source,alpha,shaders,value,value==null?0:value.handle());
    }
}
