package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.pp.shaderlod.dev.timing.ShaderInputTracker;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

/** Pinned Iris 1.8.12: only the wetness registration, between wetness and skyColor constants. */
@Mixin(value = CommonUniforms.class, remap = false)
public abstract class WorldShaderInputsMixin {
    @WrapOperation(method = "generalCommonUniforms",
            at = @At(value = "INVOKE", target = "Lnet/irisshaders/iris/gl/uniform/UniformHolder;uniform1f(Lnet/irisshaders/iris/gl/uniform/UniformUpdateFrequency;Ljava/lang/String;Lnet/irisshaders/iris/gl/uniform/FloatSupplier;)Lnet/irisshaders/iris/gl/uniform/UniformHolder;"),
            slice = @Slice(from = @At(value = "CONSTANT", args = "stringValue=wetness"),
                    to = @At(value = "CONSTANT", args = "stringValue=skyColor")), require = 1, expect = 1, remap = false)
    private static UniformHolder shaderlod$trackWetness(UniformHolder holder, UniformUpdateFrequency frequency,
                                                       String name, FloatSupplier supplier, Operation<UniformHolder> original) {
        if (!name.equals("wetness")) throw new IllegalStateException("Pinned wetness registration changed");
        return original.call(holder, frequency, name, ShaderInputTracker.wrapWetness(supplier, holder.getClass().getName() + " / " + frequency.name()));
    }
}
