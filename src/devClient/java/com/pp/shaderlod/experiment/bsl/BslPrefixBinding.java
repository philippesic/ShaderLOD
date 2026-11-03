package com.pp.shaderlod.experiment.bsl;

import java.util.function.Supplier;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Register ONLY from the existing Sodium buildUniforms local-builder seam.
 * Staging is intentionally inactive: current image/source/lifecycle/driver joins
 * have not run. A certificate fixture must never arm a rendering path. */
public final class BslPrefixBinding {
    public static void register(UniformHolder holder) {
        var f=UniformUpdateFrequency.PER_FRAME;
        holder.uniform1i(f,"shaderlodBslPrefixVersion",()->BslPrefixCertificate.VERSION);
        holder.uniform1i(f,"shaderlodBslPrefixActive",()->0);
        holder.uniform2f(f,"shaderlodBslPrefixImageSize",Vector2f::new);
        Supplier<Vector4f> absent=()->new Vector4f(-1,-1,-2,-2);
        holder.uniform4f(f,"shaderlodBslPrefixRect0",absent);
        holder.uniform4f(f,"shaderlodBslPrefixRect1",absent);
        holder.uniform4f(f,"shaderlodBslPrefixRect2",absent);
        holder.uniform3f(f,"shaderlodBslPrefixHeights",()->new Vector3f(1,1,1));
    }
    private BslPrefixBinding() {}
}
