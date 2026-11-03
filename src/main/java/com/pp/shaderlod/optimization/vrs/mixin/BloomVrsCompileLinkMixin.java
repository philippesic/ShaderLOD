package com.pp.shaderlod.optimization.vrs.mixin;
import com.pp.shaderlod.optimization.vrs.BloomVrsPrograms;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.gl.shader.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ProgramCreator.class,remap=false)
public abstract class BloomVrsCompileLinkMixin {
    @WrapMethod(method="create(Ljava/lang/String;[Lnet/irisshaders/iris/gl/shader/GlShader;)I")
    private static int shaderlod$link(String name,GlShader[] shaders,Operation<Integer> original){
        var before=shaders.clone();BloomVrsPrograms.linking(name,before);
        int id=original.call(name,shaders);
        if(!java.util.Arrays.equals(before,shaders))BloomVrsPrograms.linking("foreign",shaders);
        BloomVrsPrograms.linked(before,id);return id;
    }
}
