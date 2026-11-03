package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.G10BloomReceipt;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.irisshaders.iris.gl.shader.*;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(value=ProgramCreator.class,remap=false)
public abstract class G10BloomLinkMixin {
    @WrapMethod(method="create(Ljava/lang/String;[Lnet/irisshaders/iris/gl/shader/GlShader;)I")
    private static int shaderlod$g10Link(String name,GlShader[] shaders,Operation<Integer> original){
        if(!G10BloomReceipt.wanted(name))return original.call(name,shaders);
        var before=shaders.clone();var sources=G10BloomReceipt.sources(before);
        int id=original.call(name,shaders);
        G10BloomReceipt.linked(sources,id,java.util.Arrays.equals(before,shaders));return id;
    }
}
