package com.pp.shaderlod.optimization.reimaginedearlyalpha;

import java.util.*;
import java.util.regex.Pattern;
import com.pp.shaderlod.optimization.sourcecache.*;
import net.irisshaders.iris.gl.blending.*;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.shaderpack.programs.*;

/** Native temporal evidence is independent of the receiver and early-alpha capabilities. */
public final class ReimaginedTemporalCompiledPrograms {
    private ReimaginedTemporalCompiledPrograms() {}
    private static final LinkedCompilationProof PROOF=new LinkedCompilationProof("complementary-reimagined","gbuffers_terrain");
    private static final Pattern STUB=Pattern.compile("bool\\s+shaderlodReimaginedNativeAlphaReject\\s*\\(\\s*float\\s+finalAlpha\\s*\\)\\s*\\{\\s*return\\s+false\\s*;\\s*\\}");
    public static void clear(){PROOF.clear();}
    public static String status(){return PROOF.status();}
    public static boolean profile(Map<String,String> actual){
        if(actual==null || !"1".equals(actual.get("RP_MODE")) || !Set.of("2","3","4","5").contains(actual.get("SHADOW_QUALITY")))return false;
        // Admission only. Never feed this normalized copy into snapshot, replay or current checks.
        var admission=new TreeMap<>(actual);admission.put("SHADOW_QUALITY","2");
        return com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-reimagined",admission);
    }
    public static void prepare(ProgramSource source,ProgramSet set,AlphaTest alpha,
            String vertex,String geometry,String control,String evaluation,String fragment,Object textures,long epoch){
        PROOF.discardPreparation(source);
        if(source==null || set==null || source.getParent()!=set || !"gbuffers_terrain".equals(source.getName())
                || !(set.getPack() instanceof IrisCompiledSource.PackView view) || view.shaderlod$makeupPackInput()==null
                || !profile(view.shaderlod$makeupPackInput().options().values())
                || !ReimaginedEarlyAlphaRuntime.compileTemporalSource(set.getPack()))return;
        // Retains existing family source + recipe pins, then proves the actual observer/replay chain.
        PROOF.prepare(source,set,alpha,vertex,geometry,control,evaluation,fragment,textures,epoch);
    }
    public static boolean acceptsPass(SodiumPrograms.Pass pass,AlphaTest alpha){
        return alpha!=null && (pass==SodiumPrograms.Pass.TERRAIN && alpha.function()==AlphaTestFunction.ALWAYS
            || pass==SodiumPrograms.Pass.TERRAIN_CUTOUT && greater(alpha));
    }
    private static boolean greater(AlphaTest alpha){
        return alpha!=null && alpha.function()==AlphaTestFunction.GREATER && Float.isFinite(alpha.reference())
            && alpha.reference()>=0 && alpha.reference()<=1;
    }
    public static boolean acceptsTransformed(String fragment,AlphaTest alpha){
        if(fragment==null || alpha==null || !fragment.contains("shaderlodReimaginedTemporalShadowSamplingQualified")
                || !fragment.contains("shaderlodOptTemporalShadowFilter"))return false;
        boolean nativeForm=!fragment.contains("shaderlodReimaginedNativeAlphaReject") || STUB.matcher(fragment).results().count()==1;
        if(alpha.function()==AlphaTestFunction.ALWAYS)return nativeForm;
        if(!greater(alpha))return false;
        String compact=fragment.replaceAll("\\s+","");
        String late=alpha.toExpression("iris_FragData0.a","iris_currentAlphaTest","").replaceAll("\\s+","");
        return compact.endsWith(late+"}") && fragment.matches("(?s).*\\buniform\\s+float\\s+iris_currentAlphaTest\\s*;.*")
            && (nativeForm || ReimaginedAlphaInterface.transformed(fragment,alpha));
    }
    public static void transformed(ProgramSource source,ProgramSet set,AlphaTest alpha,Map<PatchShaderType,String> output){
        PROOF.transformed(source,set,alpha,output,output!=null && acceptsTransformed(output.get(PatchShaderType.FRAGMENT),alpha));
    }
    public static void compiled(Map<?,?> input,Map<?,?> output){PROOF.compiled(input,output);}
    /** Caller publishes only at successful createShader RETURN with the actual GlProgram object. */
    public static IrisCompiledSource.Certificate linked(ProgramSource source,AlphaTest alpha,Map<?,?> shaders){
        return PROOF.linked(source,alpha,shaders);
    }
}
