package com.pp.shaderlod.optimization.makeupearlyalpha;

import java.util.regex.Pattern;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;

/** Compile-time removal of a permission path which native ALWAYS programs cannot own. */
public final class MakeUpOpaqueAlphaSpecialization {
    private MakeUpOpaqueAlphaSpecialization() {}
    private static final String SIGNATURE = "bool\\s+shaderlodMakeupEarlyAlphaActive\\s*\\(\\s*\\)\\s*\\{";
    private static final Pattern HEADER = Pattern.compile(SIGNATURE);
    private static final Pattern ORIGINAL = Pattern.compile(SIGNATURE
        + "\\s*return\\s+shaderlod_optimization_enabled\\s*\\(\\s*shaderlodOptEarlyAlpha\\s*\\)"
        + "\\s*&&\\s*shaderlodPreset\\s*!=\\s*2\\s*&&\\s*shaderlodMakeupEarlyAlphaPermission\\s*==\\s*1\\s*;\\s*\\}");
    private static final Pattern SPECIALIZED = Pattern.compile(SIGNATURE + "\\s*return\\s+false\\s*;\\s*\\}");

    /** Null means unchanged: failed proof, unfamiliar source or another native alpha policy. */
    public static String specialize(String source, AlphaTest alpha, boolean rawProof) {
        if (!rawProof || source == null || alpha == null || alpha.function() != AlphaTestFunction.ALWAYS
                || !MakeUpAlphaInterface.candidate(source) || HEADER.matcher(source).results().count() != 1) return null;
        var matcher = ORIGINAL.matcher(source);
        if (!matcher.find()) return null;
        return source.substring(0, matcher.start())
            + "bool shaderlodMakeupEarlyAlphaActive() { return false; }"
            + source.substring(matcher.end());
    }

    /** This accepts the opaque reduction only; it never grants early-alpha eligibility. */
    public static boolean transformed(String source) {
        return source != null && HEADER.matcher(source).results().count() == 1
            && SPECIALIZED.matcher(source).find();
    }
}
