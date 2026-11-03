package com.pp.shaderlod.optimization.sourcecache;

import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.OptimizationId;
import com.pp.shaderlod.optimization.sourcecache.CompilationLinkEvidence;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.blending.AlphaTest;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;

/** Compilation-only joins. Absence falls back to the existing source proof. */
public final class LinkedCompilationProof {
    private final String family,programName;
    public LinkedCompilationProof(String family,String programName) {this.family=family;this.programName=programName;}
    public record Prepared(IrisCompiledSource.Certificate source, AlphaTest alpha) {}
    private final Map<ProgramSource,Prepared> PENDING = new IdentityHashMap<>();
    private final CompilationLinkEvidence<Prepared> LINK = new CompilationLinkEvidence<>();
    private String last = "not certified";
    public String status() { return last; }
    public void clear() { PENDING.clear(); LINK.clear(); last="invalidated"; }
    public void discardPreparation(ProgramSource source) { PENDING.remove(source); }
    public Prepared prepare(ProgramSource source, ProgramSet set, AlphaTest alpha,
            String vertex, String geometry, String control, String evaluation, String fragment,
            Object textures, long epoch) {
        PENDING.remove(source);
        // Invariant source proof is a compile responsibility, independent of optional cache requests.
        last="compile inputs refused";
        try {
            String selected=Iris.getIrisConfig().getShaderPackName().orElse(null);
            if (selected==null || !java.nio.file.Path.of(selected).getFileName().toString().equals(selected)) return null;
            var certificate=IrisCompiledSource.certify(source,set,Iris.getShaderpacksDirectory().resolve(selected),selected,epoch,family,programName);
            if (certificate==null) { last="compile inputs refused: "+IrisCompiledSource.status(); return null; }
            if (!certificate.stages().arguments(vertex,geometry,control,evaluation,fragment)
                    || textures!=set.getPackDirectives().getTextureMap()) { last="patch arguments/texture map refused"; return null; }
            var prepared=new Prepared(certificate,alpha);
            PENDING.put(source,prepared);
            last="verified raw graph/properties/options/defines/stages";
            return prepared;
        } catch (RuntimeException|LinkageError unavailable) { last="compile input "+unavailable.getClass().getSimpleName(); }
        return null;
    }
    public void transformed(ProgramSource source, ProgramSet set, AlphaTest alpha,
            Map<PatchShaderType,String> output, boolean accepted) {
        var prepared=PENDING.remove(source);
        // Iris may return the identical cached map for a later attempt. A failed raw or
        // transformed observation must revoke that map's old proof before compilation.
        boolean valid=prepared!=null && prepared.source().set()==set && prepared.alpha().equals(alpha) && accepted;
        LINK.transformed(output,valid?prepared:null);
    }
    public void compiled(Map<?,?> input, Map<?,?> output) { LINK.compiled(input,output); }
    public IrisCompiledSource.Certificate linked(ProgramSource source, AlphaTest alpha, Map<?,?> shaders) {
        var prepared=LINK.linked(shaders);
        if (prepared==null || prepared.source().source()!=source || !prepared.alpha().equals(alpha)) return null;
        last="verified successful shader-object/program link";
        return prepared.source();
    }
}
