package com.pp.shaderlod.dev.mixin;
import com.pp.shaderlod.dev.MipFamilyProbe;
import com.pp.shaderlod.dev.SolasLeanAlphaProbe;
import com.pp.shaderlod.dev.SildurLeanAlphaProbe;
import net.irisshaders.iris.pipeline.programs.SodiumShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SodiumShader.class,remap=false)
public abstract class MipFamilySodiumMixin {
 @Inject(method="setupState()V",at=@At("HEAD"),require=1)
 private void shaderlod$privateAlphaBegin(CallbackInfo ci){SolasLeanAlphaProbe.revoke();SildurLeanAlphaProbe.beginSetup();}
 @Inject(method="setupState()V",at=@At("RETURN"),require=1)
 private void shaderlod$familyTerrainReady(CallbackInfo ci){if(MipFamilyProbe.observing())MipFamilyProbe.terrainReady(this);SolasLeanAlphaProbe.ready(this);SildurLeanAlphaProbe.ready(this);}
 @Inject(method="resetState()V",at=@At("HEAD"),require=1)
 private void shaderlod$privateAlphaReset(CallbackInfo ci){SolasLeanAlphaProbe.revoke();SildurLeanAlphaProbe.beginSetup();}
}
