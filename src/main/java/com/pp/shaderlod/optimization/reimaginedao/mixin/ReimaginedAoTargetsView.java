package com.pp.shaderlod.optimization.reimaginedao.mixin;
import java.util.Map;import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import org.spongepowered.asm.mixin.Mixin;import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.irisshaders.iris.targets.RenderTargets",remap=false)
public interface ReimaginedAoTargetsView {@Accessor("targetSettingsMap")Map<Integer,PackRenderTargetDirectives.RenderTargetSettings> shaderlod$settings();}
