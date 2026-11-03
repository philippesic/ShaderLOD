package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(targets="net.irisshaders.iris.pipeline.CompositeRenderer",remap=false)
public abstract class CanonicalCompositeMixin {
    @WrapOperation(method="renderAll()V",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V"),require=1,allow=1)
    private void exchange$quad(FullScreenQuadRenderer renderer,Operation<Void> original){boolean owned=CanonicalBankExchange.beginQuad(this);try{original.call(renderer);}catch(RuntimeException|Error failure){CanonicalBankExchange.unexpectedFrameFailure(failure);throw failure;}finally{CanonicalBankExchange.endQuad(owned);}}
}
