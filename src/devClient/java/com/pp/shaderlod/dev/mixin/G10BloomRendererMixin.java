package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.G10BloomReceipt;
import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=CompositeRenderer.class,remap=false)
public abstract class G10BloomRendererMixin {
    @Shadow @Final private WorldRenderingPipeline pipeline;
    @WrapMethod(method="createProgram(Lnet/irisshaders/iris/shaderpack/programs/ProgramSource;Lcom/google/common/collect/ImmutableSet;Lcom/google/common/collect/ImmutableSet;Ljava/util/function/Supplier;)Lnet/irisshaders/iris/gl/program/Program;")
    private Program shaderlod$g10Compile(ProgramSource source,ImmutableSet<Integer> a,ImmutableSet<Integer> b,Supplier<ShadowRenderTargets> shadows,Operation<Program> original){
        try(var scope=G10BloomReceipt.open(this,pipeline,source)){var program=original.call(source,a,b,shadows);scope.complete(program);return program;}
    }
    @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
    private void shaderlod$g10Draw(FullScreenQuadRenderer quad,Operation<Void> original){original.call(quad);G10BloomReceipt.draw(this);}
    @Inject(method="destroy()V",at=@At("HEAD"),require=1)
    private void shaderlod$g10Destroy(CallbackInfo ci){G10BloomReceipt.destroy(this);}
}
