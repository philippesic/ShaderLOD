package com.pp.shaderlod.dev.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.pp.shaderlod.dev.timing.GpuTimers;
import com.pp.shaderlod.dev.timing.ShadowInputObserver;
import net.irisshaders.iris.uniforms.MatrixUniforms;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Observes and returns original matrices; no callback allocations or repeated supplier evaluation. */
@Mixin(value = MatrixUniforms.class, remap = false)
public abstract class ShadowMatrixObserverMixin {
    @ModifyReturnValue(method = "lambda$addMatrixUniforms$0(Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Lorg/joml/Matrix4fc;",
            at = @At("RETURN"), require = 1, expect = 1, remap = false)
    private static Matrix4fc shaderlod$observeModelView(Matrix4fc original) {
        shaderlod$observe(false, original);
        return original;
    }

    @ModifyReturnValue(method = "lambda$addMatrixUniforms$1(Lnet/irisshaders/iris/shaderpack/properties/PackDirectives;)Lorg/joml/Matrix4fc;",
            at = @At("RETURN"), require = 1, expect = 1, remap = false)
    private static Matrix4fc shaderlod$observeProjection(Matrix4fc original) {
        shaderlod$observe(true, original);
        return original;
    }

    @Unique private static void shaderlod$observe(boolean projection, Matrix4fc matrix) {
        if (!ShadowInputObserver.enabled()) return;
        long frame = GpuTimers.frameId();
        if (matrix == null || !ShadowInputObserver.claimConsumerMatrix(frame, projection)) return;
        ShadowInputObserver.matrix(frame, true, projection,
                matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
                matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
                matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
                matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33());
    }
}
