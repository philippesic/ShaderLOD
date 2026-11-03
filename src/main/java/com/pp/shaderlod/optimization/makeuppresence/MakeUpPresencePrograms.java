package com.pp.shaderlod.optimization.makeuppresence;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.LodSettings;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.optimization.makeupshadow.MakeUpShadowRuntime;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.sourcecache.LinkedCompilationProof;
import java.util.*;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.platform.IrisPlatformHelpers;
import net.irisshaders.iris.shaderpack.programs.*;

/** Actual CUTOUT compiler consumer. Presence is source specialization, never draw permission. */
public final class MakeUpPresencePrograms {
    private MakeUpPresencePrograms() {}
    private record Request(OptimizationSnapshot flags, LodSettings settings, LodPreset preset,
            String selected, long generation, long epoch) {}
    public static final class Prepared {
        private final IrisCompiledSource.Certificate raw;
        private final AlphaTest alpha;
        private final Request request;
        private final String fragment;
        private Prepared(IrisCompiledSource.Certificate raw, AlphaTest alpha, Request request, String fragment) {
            this.raw = raw; this.alpha = alpha; this.request = request; this.fragment = fragment;
        }
        public String fragment() { return fragment; }
    }
    private record Owner(Object pipeline, GlProgram<?> program, int handle, Prepared proof) {}
    private static final MakeUpPresenceJoin<Prepared> JOIN = new MakeUpPresenceJoin<>();
    private static final Map<ProgramSource, Prepared> ARGUMENTS = new IdentityHashMap<>();
    private static final Set<ProgramSource> ATTEMPTED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Map<?, ?>, Boolean> COMPILE_KINDS = new IdentityHashMap<>();
    private static final Map<GlProgram<?>, Owner> OWNERS = new IdentityHashMap<>();
    private static long prepared, transformed, linked;
    private static String last = "not observed";

    private static Request capture() {
        var core = OptimizationRuntimeContext.core();
        String selected = Iris.getIrisConfig().getShaderPackName().orElse(null);
        if (core == null || selected == null || !Iris.getShaderPackOptionQueue().isEmpty()) return null;
        synchronized (OptimizationConfig.class) {
            return new Request(OptimizationConfig.snapshot(), core.settings(), core.preset(), selected,
                OptimizationConfig.generation(), MakeUpShadowRuntime.epoch());
        }
    }

    private static boolean current(Prepared proof) {
        try {
            return proof != null && proof.request.equals(capture())
                && proof.raw.current(proof.raw.set().getPack(), proof.request.selected, proof.request.epoch);
        } catch (RuntimeException | LinkageError refused) { return false; }
    }

    /** Called with the independent shadow RAW receipt before any fragment argument is changed. */
    public static Prepared prepare(LinkedCompilationProof.Prepared raw, ProgramSource source, ProgramSet set,
            AlphaTest alpha, String fragment) {
        JOIN.begin(source, null);
        ARGUMENTS.remove(source);
        ATTEMPTED.remove(source);
        OWNERS.values().removeIf(owner -> owner.proof.raw.source() == source);
        try {
            if (raw == null || alpha == null || alpha.function() != AlphaTestFunction.GREATER
                    || !Float.isFinite(alpha.reference()) || alpha.reference() < 0 || alpha.reference() > 1
                    || raw.source().source() != source || raw.source().set() != set || !alpha.equals(raw.alpha())
                    || !Objects.equals(raw.source().stages().fragment(), fragment)) return null;
            var platform = IrisPlatformHelpers.getInstance();
            if (platform.isModLoaded("distanthorizons") || platform.isModLoaded("voxy")) return null;
            var request = capture();
            if (request == null || MakeUpTerrainPresence.mask(request.flags) != 0) return null;
            String next = MakeUpTerrainPresence.specialize(fragment, 0,
                raw.source().input().options().values().get(CompileOptionControl.MAKEUP_EARLY_ALPHA));
            if (next == null) return null;
            var receipt = new Prepared(raw.source(), alpha, request, next);
            if (!current(receipt)) return null;
            JOIN.begin(source, receipt); ARGUMENTS.put(source, receipt); ATTEMPTED.add(source);
            prepared++; last = "prepared authenticated CUTOUT mask0";
            return receipt;
        } catch (RuntimeException | LinkageError refused) { last = "prepare refused"; return null; }
    }

    /** Observe arguments after all ModifyArgs handlers; a foreign edit cannot inherit the raw receipt. */
    public static Prepared consume(ProgramSource source, ProgramSet set, AlphaTest alpha, String vertex,
            String geometry, String control, String evaluation, String fragment, Object textures) {
        var proof = ARGUMENTS.remove(source);
        return proof != null && proof.raw.set() == set && proof.alpha.equals(alpha) && current(proof)
            && proof.raw.stages().arguments(vertex, geometry, control, evaluation, proof.raw.stages().fragment())
            && proof.fragment.equals(fragment) && textures == set.getPackDirectives().getTextureMap() ? proof : null;
    }

    /** The wrapper calls the pinned patcher with these exact arguments, then supplies its actual returned map. */
    public static void patched(ProgramSource source, Prepared receipt, Map<PatchShaderType, String> output) {
        JOIN.patched(source, receipt, output, MakeUpPresencePrograms::current);
    }

    public static boolean transformed(ProgramSource source, ProgramSet set, AlphaTest alpha,
            Map<PatchShaderType, String> output) {
        boolean attempted = ATTEMPTED.remove(source);
        var receipt = JOIN.transformed(source, output, proof -> current(proof) && proof.raw.set() == set
            && proof.alpha.equals(alpha) && output != null
            && MakeUpTerrainPresence.transformed(output.get(PatchShaderType.FRAGMENT)));
        if (receipt != null) { transformed++; last = "verified actual CUTOUT mask0 transformed map"; return true; }
        // Never let a rejected specialized return acquire the independent shadow certificate.
        return !attempted && (output == null || !MakeUpTerrainPresence.transformed(output.get(PatchShaderType.FRAGMENT)));
    }

    public static void compiled(Map<?, ?> input, Map<?, ?> output) {
        if (output != null) {
            COMPILE_KINDS.put(output, input != null && input.get(PatchShaderType.FRAGMENT) instanceof String fragment
                && MakeUpTerrainPresence.transformed(fragment));
        }
        JOIN.compiled(input, output, MakeUpPresencePrograms::current);
    }

    /** Only the successful native createShader RETURN hook calls this; GL names alone are not owners. */
    public static boolean linked(Object pipeline, SodiumPrograms.Pass pass, ProgramSource source, AlphaTest alpha,
            Map<?, ?> shaders, GlProgram<?> program) {
        if (program != null) {
            OWNERS.remove(program);
            OWNERS.values().removeIf(owner -> owner.handle == program.handle());
        }
        var proof = JOIN.linked(shaders, MakeUpPresencePrograms::current);
        if (proof == null || program == null || pipeline == null || program.handle() <= 0
                || pass != SodiumPrograms.Pass.TERRAIN_CUTOUT || proof.raw.source() != source
                || !proof.alpha.equals(alpha)) return Boolean.FALSE.equals(COMPILE_KINDS.get(shaders));
        OWNERS.put(program, new Owner(pipeline, program, program.handle(), proof));
        linked++; last = "verified successful CUTOUT mask0 program link";
        return true;
    }

    public static void failed(ProgramSource source) {
        JOIN.failed(source);
        ARGUMENTS.remove(source);
        ATTEMPTED.remove(source);
        OWNERS.values().removeIf(owner -> owner.proof.raw.source() == source);
        last = "failed compiler call; evidence revoked";
    }

    /** Reload/destroy and normal Apply revoke the full request chain, including completed owners. */
    public static void clear() { JOIN.clear(); ARGUMENTS.clear(); ATTEMPTED.clear(); COMPILE_KINDS.clear(); OWNERS.clear(); last = "invalidated"; }

    public static String status() {
        OWNERS.values().removeIf(owner -> !JOIN.retains(owner.proof) || !current(owner.proof) || !liveHandle(owner));
        return last + "; prepared=" + prepared + ", transformed=" + transformed + ", linked=" + linked
            + ", currentOwners=" + OWNERS.size();
    }
    private static boolean liveHandle(Owner owner) {
        try { return owner.program.handle() == owner.handle; }
        catch (RuntimeException deleted) { return false; }
    }
}
