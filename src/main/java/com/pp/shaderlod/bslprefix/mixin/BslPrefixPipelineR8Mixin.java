package com.pp.shaderlod.bslprefix.mixin;
import com.pp.shaderlod.bslprefix.BslPrefixRuntime;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pbr.format.TextureFormat;
import net.irisshaders.iris.pbr.texture.PBRType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=IrisRenderingPipeline.class,remap=false)
public abstract class BslPrefixPipelineR8Mixin {
 @WrapOperation(method="onSetShaderTexture",at=@At(value="INVOKE",target="Lnet/irisshaders/iris/pbr/format/TextureFormat;setupTextureParameters(Lnet/irisshaders/iris/pbr/texture/PBRType;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V"),require=2,allow=2)
 private void format(TextureFormat format,PBRType type,AbstractTexture texture,Operation<Void> original){BslPrefixRuntime.normalSetup(format,type,texture);original.call(format,type,texture);}
 @Inject(method="onSetShaderTexture",at=@At("RETURN"),require=1)private void binding(CallbackInfo ci){BslPrefixRuntime.bindingChanged();}
 @Inject(method="destroy()V",at=@At("HEAD"),require=1)private void destroy(CallbackInfo ci){BslPrefixRuntime.pipelineDestroying(this);}
}
