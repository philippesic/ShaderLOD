package com.pp.shaderlod.optimization.framecopy.exchange.mixin;
import com.pp.shaderlod.optimization.framecopy.exchange.CanonicalBankExchange;
import net.irisshaders.iris.gl.texture.DepthBufferFormat;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(targets="net.irisshaders.iris.targets.RenderTargets",remap=false)
public abstract class CanonicalTargetsMixin {
    @Inject(method="onFullClear()V",at=@At("HEAD"),require=1,allow=1)
    private void exchange$clear(CallbackInfo ci){CanonicalBankExchange.beforeClear(this);}
    @Inject(method="resizeIfNeeded",at=@At("HEAD"),require=1,allow=1)
    private void exchange$resize(int version,int texture,int width,int height,DepthBufferFormat format,PackDirectives directives,CallbackInfoReturnable<Boolean> ci){CanonicalBankExchange.beforeResize(this,version,texture,width,height,format);}
    @Inject(method="destroy()V",at=@At("HEAD"),require=1,allow=1)
    private void exchange$destroy(CallbackInfo ci){CanonicalBankExchange.destroyed(this);}
}
