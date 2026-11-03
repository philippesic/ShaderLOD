package com.pp.shaderlod.optimization.reimaginedearlyalpha;

import java.util.*;
import java.util.regex.Pattern;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import com.pp.shaderlod.optimization.ComplementaryReceiverAdmission;
import com.pp.shaderlod.optimization.sourcecache.*;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.ShaderPack;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

/** Independent full-rate native receiver evidence; no opaque specialization or alpha permission. */
public final class ReimaginedReceiverCompiledPrograms {
    private ReimaginedReceiverCompiledPrograms() {}
    private static final LinkedCompilationProof PROOF =
        new LinkedCompilationProof("complementary-reimagined", "gbuffers_terrain");
    private static final Map<ProgramSource,LinkedCompilationProof.Prepared> RAW = new IdentityHashMap<>();
    public record Native(Object pipeline, ShaderPack pack, long epoch, SodiumPrograms.Pass pass,
                         IrisCompiledSource.Certificate source, Object context) {}
    private static final LinkedProgramLocations<GlProgram<?>,Native> COMPILED = new LinkedProgramLocations<>(GlProgram::handle);
    // Retained even after lifetime rejection, so a dead/rejected native link cannot fall back to ZIP proof.
    private static final Int2ObjectOpenHashMap<Object> NATIVE = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet BOUNDARIES = new IntOpenHashSet();
    private static final IntOpenHashSet LEGACY = new IntOpenHashSet();
    private static long compileCalls, compileNanos;
    private static final Int2ObjectOpenHashMap<String> REGISTRATIONS=new Int2ObjectOpenHashMap<>();
    private static void registration(int id,String reason){if(REGISTRATIONS.size()>=32)REGISTRATIONS.clear();REGISTRATIONS.put(id,reason);}
    private static final Pattern STUB = Pattern.compile("bool\\s+shaderlodReimaginedNativeAlphaReject\\s*\\(\\s*float\\s+finalAlpha\\s*\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");
    public static String status() { return PROOF.status()+"; registrations="+REGISTRATIONS+"; receiver compileCalls="+compileCalls+", compileNs="+compileNanos; }
    public static long compileCalls() { return compileCalls; }
    public static long compileNanos() { return compileNanos; }
    public static void clear() { PROOF.clear(); RAW.clear(); COMPILED.clear(); REGISTRATIONS.clear(); NATIVE.clear(); BOUNDARIES.clear(); LEGACY.clear(); }
    public static void destroy(Object pipeline) {
        PROOF.clear(); RAW.clear(); COMPILED.removeIf(c -> c.pipeline()==pipeline);
        NATIVE.entrySet().removeIf(e -> { if(e.getValue()!=pipeline)return false; BOUNDARIES.remove(e.getKey()); return true; });
        // Legacy ownership is checked by the runtime and invalidated with its pipeline.
    }
    public static void legacy(int id) { registration(id,"integer derivative registration"); COMPILED.forget(id); NATIVE.remove(id); BOUNDARIES.remove(id); if(id>0)LEGACY.add(id); }
    public static void forget(int id) { registration(id,"explicit derived cleanup"); COMPILED.forget(id); NATIVE.remove(id); BOUNDARIES.remove(id); LEGACY.remove(id); }
    public static boolean receiverBoundary(SodiumPrograms.Pass pass, String name, String fragment) {
        return (pass==SodiumPrograms.Pass.TERRAIN || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT)
            && "gbuffers_terrain".equals(name) && fragment!=null
            && fragment.contains("shaderlodReimaginedTemporalShadowSamplingQualified")
            && fragment.contains("shaderlodComplementaryReceiverPermission");
    }
    public static boolean nativeRegistration(int id) { return NATIVE.containsKey(id); }
    public static boolean ownsBoundary(int id) { return BOUNDARIES.contains(id); }
    public static boolean requiresNative(int id, ShaderPack pack) {
        return NATIVE.containsKey(id) || !LEGACY.contains(id) && pack instanceof IrisCompiledSource.PackView;
    }
    public static LinkedProgramLocations.Entry<GlProgram<?>,Native> current(int id, Object pipeline,
            ShaderPack pack, long epoch) {
        var entry=COMPILED.current(id);
        if(entry==null)return null;
        var nativeOwner=entry.certificate();
        return nativeOwner.context()==org.lwjgl.opengl.GL.getCapabilities() && nativeOwner.pipeline()==pipeline && nativeOwner.pack()==pack && nativeOwner.epoch()==epoch
            && nativeOwner.source().current(pack,Iris.getIrisConfig().getShaderPackName().orElse(null),epoch) ? entry : null;
    }
    /** Locations are a link-lifetime property; dynamic source admission is separate at the draw boundary. */
    public static Map<String,Integer> locations(int id) {
        var entry=COMPILED.current(id);
        return entry==null || entry.certificate().context()!=org.lwjgl.opengl.GL.getCapabilities() ? null : entry.locations();
    }
    public static boolean recipeMatches(byte[] bytes) {
        if(bytes==null)return false;
        try { return "7eb35919b4c5781ebd2a6bf4dac58b5b7b40b2e9979ec6afa9b7529e371bd158".equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))); }
        catch(java.security.NoSuchAlgorithmException unavailable) { return false; }
    }
    private static boolean recipePinned() {
        try(var in=ReimaginedReceiverCompiledPrograms.class.getResourceAsStream(
                "/assets/shaderlod/adapters/patches/adapters/complementary-reimagined.json")) {
            return in!=null && recipeMatches(in.readAllBytes());
        } catch(java.io.IOException|LinkageError unavailable) { return false; }
    }
    public static boolean acceptsAlpha(AlphaTest alpha) {
        return alpha!=null && (alpha.function()==AlphaTestFunction.ALWAYS
            || alpha.function()==AlphaTestFunction.GREATER && Float.isFinite(alpha.reference())
                && alpha.reference()>=0 && alpha.reference()<=1);
    }
    public static boolean acceptsPass(SodiumPrograms.Pass pass, AlphaTest alpha) {
        return acceptsAlpha(alpha) && (pass==SodiumPrograms.Pass.TERRAIN && alpha.function()==AlphaTestFunction.ALWAYS
            || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT && alpha.function()==AlphaTestFunction.GREATER);
    }
    public static void prepare(ProgramSource source, ProgramSet set, AlphaTest alpha,
            String vertex, String geometry, String control, String evaluation, String fragment,
            Object textures, long epoch) {
        PROOF.discardPreparation(source); RAW.remove(source);
        if(source==null || set==null || source.getParent()!=set || !"gbuffers_terrain".equals(source.getName())
                || !acceptsAlpha(alpha) || !(set.getPack() instanceof IrisCompiledSource.PackView view)
                || view.shaderlod$makeupPackInput()==null)return;
        var values=view.shaderlod$makeupPackInput().options().values();
        // Actual quality 2, never the temporal reader's normalized 2..5 profile.
        if(!ComplementaryReceiverAdmission.validate(values.get("SHADOW_QUALITY"),false,
                () -> ReimaginedEarlyAlphaRuntime.profile(values)))return;
        long start=System.nanoTime(); compileCalls++;
        try {
            if(!recipePinned())return;
            var receipt=PROOF.prepare(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,epoch);
            if(receipt!=null)RAW.put(source,receipt);
        } finally { compileNanos+=System.nanoTime()-start; }
    }
    public static boolean acceptsTransformed(String fragment, AlphaTest alpha) {
        if(fragment==null || !acceptsAlpha(alpha)
                || !fragment.contains("shaderlodComplementaryReceiverPermission")
                || !fragment.contains("shaderlodOptShadowReceiverFootprint"))return false;
        var stub=STUB.matcher(fragment); boolean nativeStub=stub.find() && !stub.find();
        if(alpha.function()==AlphaTestFunction.ALWAYS)return nativeStub;
        String compact=fragment.replaceAll("\\s+","");
        String late=alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","").replaceAll("\\s+","");
        boolean nativeLate=compact.endsWith(late+"}")
            && fragment.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*");
        // Native stub+late discard or the already-reviewed CUTOUT rewrite, never a broader alpha contract.
        return nativeLate && (nativeStub || ReimaginedAlphaInterface.transformed(fragment,alpha));
    }
    public static void transformed(ProgramSource source, ProgramSet set, AlphaTest alpha,
            Map<PatchShaderType,String> output) {
        var raw=RAW.remove(source);
        PROOF.transformed(source,set,alpha,output,raw!=null && raw.source().source()==source
            && raw.source().set()==set && Objects.equals(raw.alpha(),alpha) && output!=null
            && acceptsTransformed(output.get(PatchShaderType.FRAGMENT),alpha));
    }
    public static void compiled(Map<?,?> input, Map<?,?> output) { PROOF.compiled(input,output); }
    /** Called only at successful createShader RETURN, with its actual shader objects and actual program. */
    public static void linked(IrisRenderingPipeline pipeline, SodiumPrograms.Pass pass, ProgramSource source,
            AlphaTest alpha, Map<?,?> shaders, GlProgram<?> program, long epoch, Map<String,Integer> locations) {
        if(program==null)return;
        int id=program.handle(); COMPILED.forget(id); LEGACY.remove(id); NATIVE.put(id,pipeline);
        BOUNDARIES.remove(id);
        if(source!=null && receiverBoundary(pass,source.getName(),source.getFragmentSource().orElse(null)))BOUNDARIES.add(id);
        var proof=PROOF.linked(source,alpha,shaders);
        if(proof==null || pipeline==null || !acceptsPass(pass,alpha) || proof.epoch()!=epoch
                || proof.source()!=source || !proof.current(source.getParent().getPack(),
                    Iris.getIrisConfig().getShaderPackName().orElse(null),epoch)) {
            registration(id,pass+"/"+(alpha==null?"null":alpha.function())+": "+(proof==null?"link evidence absent ("+PROOF.status()+")":pipeline==null?"pipeline absent":!acceptsPass(pass,alpha)?"alpha/pass refused":proof.epoch()!=epoch?"epoch mismatch":proof.source()!=source?"source identity mismatch":"captured source no longer current at publication"));
            return;
        }
        COMPILED.put(id,program,new Native(pipeline,source.getParent().getPack(),epoch,pass,proof,org.lwjgl.opengl.GL.getCapabilities()),locations);
        registration(id,pass+"/"+alpha.function()+": "+(COMPILED.current(id)==null?"handle rejected":"published"));
    }
}
