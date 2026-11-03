package com.pp.shaderlod.dev;

import java.util.function.IntSupplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;

/** UNWIRED root integration seam. No source/binding observation or GL mutation.
 * Root's per-frame supplier must return 0 on unknowns, reloads, stale resources,
 * missing complete shadow execution or changed pipeline/options. Return 1 only
 * after the exact producer/consumer/source/sampler/error certificate is joined.
 * A flag readback alone is not that certificate. Existing core bridge owns the
 * enabled/preset fields; this adds no substitute mod configuration.
 */
public final class BslShadowFieldUniformStaging {
    private BslShadowFieldUniformStaging() {}
    public static void register(UniformHolder holder, IntSupplier qualifiedCurrentFrame) {
        holder.uniform1i(UniformUpdateFrequency.PER_FRAME,
            "shaderlodBslShadowFieldQualified", qualifiedCurrentFrame);
    }

    /** Exact source gate: ShadowCompositeRenderer.createProgram invokes only
     * CommonUniforms.addDynamicUniforms, which does NOT reach the existing
     * generalCommonUniforms mod hook or register frameCounter. Root calls this
     * ONLY on the exact field ProgramBuilder before build; the actual pipeline
     * notifier/directives must match those used by forward consumers.
     * generalCommonUniforms invokes the existing production mod bridge mixin;
     * no invented enabled/preset uniforms or duplicate forward registration.
     */
    public static void registerProducer(UniformHolder fieldBuilder, FrameUpdateNotifier notifier,
            PackDirectives directives, IntSupplier qualifiedCurrentFrame) {
        CommonUniforms.generalCommonUniforms(fieldBuilder, notifier, directives);
        SystemTimeUniforms.addSystemTimeUniforms(fieldBuilder);
        register(fieldBuilder, qualifiedCurrentFrame);
    }
}
