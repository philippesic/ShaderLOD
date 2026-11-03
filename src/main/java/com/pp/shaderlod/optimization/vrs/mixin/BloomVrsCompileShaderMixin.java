package com.pp.shaderlod.optimization.vrs.mixin;
import com.pp.shaderlod.optimization.vrs.BloomVrsPrograms;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.shader.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=ProgramBuilder.class,remap=false)
public abstract class BloomVrsCompileShaderMixin {
    @Inject(method="buildShader(Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;Ljava/lang/String;)Lnet/irisshaders/iris/gl/shader/GlShader;",at=@At("RETURN"),require=1,allow=1)
    private static void shaderlod$compiled(ShaderType type,String name,String source,CallbackInfoReturnable<GlShader> ci){BloomVrsPrograms.shader(type,source,ci.getReturnValue());}
}
