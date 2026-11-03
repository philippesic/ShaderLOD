package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(GameRenderer.class)
public abstract class CanonicalFrameMixin {
    @WrapMethod(method="renderLevel",require=1)
    private void exchange$frame(DeltaTracker tracker,Operation<Void> original){
        try{original.call(tracker);CanonicalBankExchange.frameReturned();}
        catch(RuntimeException|Error failure){CanonicalBankExchange.unexpectedFrameFailure(failure);throw failure;}
    }
    @WrapMethod(method="render",require=1)
    private void exchange$outerFrame(DeltaTracker tracker,boolean renderLevel,Operation<Void> original){
        CanonicalBankExchange.frameStarting();
        try{original.call(tracker,renderLevel);}
        catch(CanonicalBankExchange.Discard controlled){CanonicalBankExchange.recoverDiscard(controlled);}
        catch(CanonicalBankExchange.FatalStop stopped){CanonicalBankExchange.stopAfterUnwind(stopped);}
    }

}
