package com.pp.shaderlod.optimization.depthprime;

import java.util.Objects;
import net.irisshaders.iris.gl.blending.AlphaTest;

/** Exact allowed fragment substitutions between raw-source capture and the native Iris transform call. */
public final class DepthPatchArguments {
    private DepthPatchArguments() {}
    public static boolean fragment(String family,String raw,String consumed,AlphaTest alpha) {
        return fragment(family,raw,consumed,alpha,()->false);
    }
    public static boolean fragment(String family,String raw,String consumed,AlphaTest alpha,
            java.util.function.BooleanSupplier specializedReceipt) {
        if(raw==null || consumed==null || alpha==null)return false;
        if("sildurs-vibrant-shaders".equals(family) && specializedReceipt.getAsBoolean()
            && consumed.equals(com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaInterface.absent(raw)))return true;
        if(Objects.equals(raw,consumed))return true;
        String expected=switch(family) {
            case "makeup-ultra-fast-shaders" -> com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpOpaqueAlphaSpecialization.specialize(raw,alpha,true);
            case "complementary-reimagined" -> com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedAlphaInterface.prepare(raw,alpha);
            case "complementary-unbound" -> com.pp.shaderlod.optimization.unboundearlyalpha.UnboundAlphaInterface.prepare(raw,alpha);
            case "sildurs-vibrant-shaders" -> com.pp.shaderlod.optimization.sildurearlyalpha.SildurAlphaInterface.prepare(raw,alpha);
            case "bsl-shaders" -> com.pp.shaderlod.optimization.bslearlyalpha.BslAlphaInterface.prepare(raw,alpha);
            case "astralex" -> com.pp.shaderlod.optimization.astraearlyalpha.AstraAlphaInterface.prepare(raw,alpha);
            case "bliss-shader" -> com.pp.shaderlod.optimization.blissearlyalpha.BlissAlphaInterface.prepare(raw,alpha);
            case "solas-shader" -> com.pp.shaderlod.optimization.solasearlyalpha.SolasAlphaInterface.prepare(raw,alpha);
            case "photon" -> com.pp.shaderlod.optimization.photonearlyalpha.PhotonAlphaInterface.prepare(raw,alpha);
            default -> null;
        };
        return expected!=null && expected.equals(consumed);
    }
}
