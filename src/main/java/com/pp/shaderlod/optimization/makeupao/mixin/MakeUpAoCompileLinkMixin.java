package com.pp.shaderlod.optimization.makeupao.mixin;
import com.pp.shaderlod.optimization.sourcecache.IrisCompositeCompiledInputs;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.gl.shader.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ProgramCreator.class,remap=false)
public abstract class MakeUpAoCompileLinkMixin {
    @WrapMethod(method="create(Ljava/lang/String;[Lnet/irisshaders/iris/gl/shader/GlShader;)I")
    private static int shaderlod$link(String name,GlShader[] shaders,Operation<Integer> original){
        var before=shaders.clone();IrisCompositeCompiledInputs.linking(name,before);
        int id=original.call(name,shaders);
        if(!java.util.Arrays.equals(before,shaders))IrisCompositeCompiledInputs.linking("foreign",shaders);
        IrisCompositeCompiledInputs.linked(before,id);return id;
    }
}
