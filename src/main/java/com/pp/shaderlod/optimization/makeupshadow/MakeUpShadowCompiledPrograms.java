package com.pp.shaderlod.optimization.makeupshadow;

import java.util.Map;
import java.util.IdentityHashMap;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpAlphaInterface;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpOpaqueAlphaSpecialization;
import com.pp.shaderlod.optimization.makeupao.MakeUpAoSourceProof;
import com.pp.shaderlod.optimization.makeuppresence.MakeUpPresencePrograms;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/** Independent shadow evidence: solid terrain does not require early-alpha admission. */
public final class MakeUpShadowCompiledPrograms {
    private MakeUpShadowCompiledPrograms() {}
    private static final LinkedCompilationProof PROOF =
        new LinkedCompilationProof("makeup-ultra-fast-shaders", "gbuffers_terrain");
    private static final Map<ProgramSource,LinkedCompilationProof.Prepared> OPAQUE = new IdentityHashMap<>();
    private static long opaquePrepared, opaqueTransformed;
    private static final Map<ProgramSource,LinkedCompilationProof.Prepared> RAW = new IdentityHashMap<>();
    public static String status() { return PROOF.status()+"; opaquePrepared="+opaquePrepared+", opaqueTransformed="+opaqueTransformed+"; presence="+MakeUpPresencePrograms.status(); }
    public static void clear() { PROOF.clear(); OPAQUE.clear(); RAW.clear(); MakeUpPresencePrograms.clear(); opaquePrepared=opaqueTransformed=0; }
    public static void failed(ProgramSource source) { PROOF.discardPreparation(source); OPAQUE.remove(source); RAW.remove(source); }
    public static boolean recipeMatches(byte[] bytes) {
        if(bytes==null)return false;
        try{return MakeUpAoSourceProof.RECIPE_SHA.equals(java.util.HexFormat.of().formatHex(
            java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));}
        catch(java.security.NoSuchAlgorithmException unavailable){return false;}
    }
    private static boolean recipePinned() {
        try(var input=MakeUpShadowCompiledPrograms.class.getResourceAsStream(
                "/assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")) {
            return input!=null && recipeMatches(input.readAllBytes());
        }catch(java.io.IOException|LinkageError unavailable){return false;}
    }
    public static LinkedCompilationProof.Prepared prepare(ProgramSource source, ProgramSet set, AlphaTest alpha,
            String vertex, String geometry, String control, String evaluation, String fragment,
            Object textures, long epoch) {
        PROOF.discardPreparation(source);
        OPAQUE.remove(source); RAW.remove(source);
        if(source == null || set == null || source.getParent()!=set || !"gbuffers_terrain".equals(source.getName())
                || !(set.getPack() instanceof IrisCompiledSource.PackView view) || view.shaderlod$makeupPackInput()==null) return null;
        var values=view.shaderlod$makeupPackInput().options().values();
        if(!MakeUpShadowRuntime.coloredBudgetProfile(values) && !MakeUpShadowRuntime.profile(values)) return null;
        if(!recipePinned())return null;
        var receipt=PROOF.prepare(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,epoch);
        if(receipt!=null)RAW.put(source,receipt);
        return receipt;
    }
    public static String specializeOpaque(LinkedCompilationProof.Prepared receipt, ProgramSource source,
            ProgramSet set, AlphaTest alpha, String fragment) {
        if(receipt==null || receipt.source().source()!=source || receipt.source().set()!=set
                || !receipt.alpha().equals(alpha) || !java.util.Objects.equals(receipt.source().stages().fragment(),fragment)) return fragment;
        var specialized=MakeUpOpaqueAlphaSpecialization.specialize(fragment,alpha,true);
        if(specialized==null)return fragment;
        OPAQUE.put(source,receipt);opaquePrepared++;
        return specialized;
    }
    public static boolean acceptsPass(SodiumPrograms.Pass pass, AlphaTest alpha) {
        if (alpha == null) return false;
        return pass == SodiumPrograms.Pass.TERRAIN && alpha.function() == AlphaTestFunction.ALWAYS
            || pass == SodiumPrograms.Pass.TERRAIN_CUTOUT && alpha.function() == AlphaTestFunction.GREATER
                && Float.isFinite(alpha.reference()) && alpha.reference() >= 0 && alpha.reference() <= 1;
    }
    public static boolean acceptsTransformed(String fragment, AlphaTest alpha) {
        return acceptsTransformed(fragment,alpha,"1");
    }
    public static boolean acceptsTransformed(String fragment, AlphaTest alpha, String compiledPresence) {
        if (!java.util.Set.of("0","1").contains(java.util.Objects.toString(compiledPresence,"")) || fragment == null || alpha == null
                || !fragment.contains("shaderlodMakeupColoredShadowBudgetPermission")
                || !fragment.contains("shaderlodOptShadowSampleBudget")) return false;
        return alpha.function() == AlphaTestFunction.ALWAYS && (!"0".equals(compiledPresence)||MakeUpAlphaInterface.nativeInactiveTransformed(fragment,alpha))
            || ("0".equals(compiledPresence) ? MakeUpAlphaInterface.nativeInactiveTransformed(fragment,alpha) : MakeUpAlphaInterface.transformed(fragment,alpha));
    }
    public static void transformed(ProgramSource source, ProgramSet set, AlphaTest alpha,
            Map<PatchShaderType,String> output) {
        transformed(source,set,alpha,output,true);
    }
    public static void transformed(ProgramSource source, ProgramSet set, AlphaTest alpha,
            Map<PatchShaderType,String> output, boolean presenceAccepted) {
        var raw=RAW.remove(source);
        var opaque=OPAQUE.remove(source);
        boolean specialized=opaque==null || opaque.source().source()==source && opaque.source().set()==set
            && opaque.alpha().equals(alpha) && output!=null
            && MakeUpOpaqueAlphaSpecialization.transformed(output.get(PatchShaderType.FRAGMENT));
        if(opaque!=null && specialized)opaqueTransformed++;
        PROOF.transformed(source,set,alpha,output,
            presenceAccepted && specialized && raw!=null && raw.source().source()==source && raw.source().set()==set && raw.alpha().equals(alpha)
                && output != null && acceptsTransformed(output.get(PatchShaderType.FRAGMENT),alpha,
                    raw.source().input().options().values().get(com.pp.shaderlod.optimization.CompileOptionControl.MAKEUP_EARLY_ALPHA)));
    }
    public static void compiled(Map<?,?> input, Map<?,?> output) { PROOF.compiled(input,output); }
    public static IrisCompiledSource.Certificate linked(ProgramSource source, AlphaTest alpha, Map<?,?> shaders) {
        return PROOF.linked(source,alpha,shaders);
    }
}
