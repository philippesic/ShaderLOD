package com.pp.shaderlod.optimization.makeupearlyalpha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;

/** Unused by public policy. Exact current post-sample output, removing only its duplicate final test. */
public final class MakeUpSingleAlphaSource {
    public static final String CURRENT_SHA="7ab346f06a8a7d892588fa9a09eb7deae9f105b18890a0d574e06917334df074";
    private MakeUpSingleAlphaSource() {}
    public static String rewrite(String current,AlphaTest alpha) {
        if(current==null||alpha==null||alpha.function()!=AlphaTestFunction.GREATER
            ||Float.compare(alpha.reference(),.1f)!=0||!CURRENT_SHA.equals(sha(current)))return null;
        String late="if (!(iris_FragData0.a > iris_currentAlphaTest)) {\ndiscard;\n}";
        int at=current.lastIndexOf(late);
        if(at<0||current.indexOf(late)!=at||!current.substring(at+late.length()).isBlank()
            &&!current.substring(at+late.length()).strip().equals("}"))return null;
        // The complete pinned stage proves all post-seam blockColor writes are RGB-only,
        // followed by unchanged native clamp/output. Early clamped alpha equals final output alpha.
        return current.substring(0,at)+current.substring(at+late.length());
    }
    private static String sha(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
