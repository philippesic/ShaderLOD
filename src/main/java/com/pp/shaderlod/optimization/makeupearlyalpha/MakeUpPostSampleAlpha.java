package com.pp.shaderlod.optimization.makeupearlyalpha;

import java.util.EnumMap;
import java.util.Map;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource;
import com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.gl.blending.AlphaTestFunction;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.neoforged.fml.ModList;
import org.lwjgl.opengl.GL11C;

/** Setup-only normal public G03 lowering for finite qualified native100 tuples.
 * Complete source matching preserves all native mutable GL inputs. No draw owner,
 * permission uniform or sampler query is introduced. Other tuples stay native. */
public final class MakeUpPostSampleAlpha {
    private static long rewrittenPrograms;
    private static String last = "not consumed";
    private MakeUpPostSampleAlpha() {}
    public static String status() {
        return "Post-sample source setup; rewrites=" + rewrittenPrograms + "; last=" + last;
    }
    public static void invalidate() { last = "invalidated"; }
    public static Map<String,Object> setupSnapshot() {
        return Map.of("rewritten_programs", rewrittenPrograms,
            "rewritten", "complete native CUTOUT rewritten after all samples; no draw permission".equals(last),
            "scope", "Source setup only; linked consumption and avoided fragments unobserved");
    }
    /** Only complete qualified optimizer tuples; unsupported peers remain native. */
    public static boolean stackMatches(Map<String,String> loaded) {
        if (loaded == null) return false;
        var remaining = new java.util.TreeMap<>(loaded);
        var optimized = Map.of("modernfix", "5.27.24+mc1.21.1", "ferritecore", "7.0.3",
            "immediatelyfast", "1.6.14+1.21.1");
        boolean any = optimized.keySet().stream().anyMatch(remaining::containsKey);
        if (any) for (var entry : optimized.entrySet())
            if (!entry.getValue().equals(remaining.remove(entry.getKey()))) return false;
        return MakeUpQuadRuntime.stackMatches(remaining, false);
    }
    public static Map<PatchShaderType,String> adapt(ProgramSource source, AlphaTest alpha,
            ProgramSet set, Map<PatchShaderType,String> stages) {
        if (!MakeUpPostSampleAlphaSource.platform(System.getProperty("os.name"), System.getProperty("os.arch"))
                || alpha == null || alpha.function() != AlphaTestFunction.GREATER
                || source == null || !"gbuffers_terrain".equals(source.getName())
                || !MakeUpEarlyAlphaCompilation.enabled(source, set)) return stages;
        last = "native: qualification refused";
        if (!MakeUpEarlyAlphaPlugin.exactApi() || Iris.isFallback()
                || !MakeUpPostSampleAlphaSource.host(System.getProperty("os.name"), System.getProperty("os.arch"),
                    GL11C.glGetString(GL11C.GL_RENDERER), GL11C.glGetString(GL11C.GL_VERSION))
                || !stackMatches(MakeUpQuadRuntime.loadedStack())
                || ModList.get().isLoaded("distanthorizons") || ModList.get().isLoaded("voxy")) return stages;
        var pack = set.getPack();
        if (Iris.getCurrentPack().orElse(null) != pack || !(pack instanceof IrisCompiledSource.PackView view)) return stages;
        var input = view.shaderlod$makeupPackInput();
        if (input == null || !input.options().current(pack.getShaderPackOptions())
                || !MakeUpEarlyAlphaLeanGraph.profile(input.options().values())
                || !MakeUpEarlyAlphaRuntime.source(pack)) return stages;
        String next = MakeUpPostSampleAlphaSource.rewrite(stages.get(PatchShaderType.FRAGMENT), alpha);
        if (next == null) {
            last = "native: complete stage/alpha pin refused";
            return stages;
        }
        var copy = new EnumMap<PatchShaderType,String>(PatchShaderType.class);
        copy.putAll(stages);
        copy.put(PatchShaderType.FRAGMENT, next);
        MakeUpPostSampleAlphaWitness.transformed(source,input,copy);
        rewrittenPrograms++;
        last = "complete native CUTOUT rewritten after all samples; no draw permission";
        return copy;
    }
}
