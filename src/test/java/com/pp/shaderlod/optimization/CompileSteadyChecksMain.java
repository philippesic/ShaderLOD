package com.pp.shaderlod.optimization;
import com.google.common.collect.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
/** Small real Iris parser/OptionValues inputs; dimension and admission steady-path boundaries. */
public final class CompileSteadyChecksMain {
    static int assertions;
    static void check(boolean ok,String note){assertions++;if(!ok)throw new AssertionError(note);}
    static ShaderPackOptions fixture(String key,boolean active,boolean valid)throws Exception {
        boolean bool=key.equals(CompileOptionControl.PHOTON_SPATIAL)||key.equals(CompileOptionControl.PHOTON_LIGHTING_RATE);
        boolean defaultOn=key.equals(CompileOptionControl.PHOTON_SPATIAL);
        String declaration=bool?(defaultOn?"#define ":"//#define ")+key:"#define "+key+" "+(valid?"0":"1")+" // [0 1]";
        if(bool&&!valid)declaration=(defaultOn?"//#define ":"#define ")+key;
        var path=AbsolutePackPath.fromAbsolutePath("/world0/deferred.fsh");
        var node=new FileNode(path,ImmutableList.of(declaration,bool?"#ifdef "+key:"#if "+key+" == 1","#endif"));
        var ctor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);ctor.setAccessible(true);
        var graph=ctor.newInstance(ImmutableMap.of(path,node),ImmutableMap.of());
        return new ShaderPackOptions(graph,Map.of(key,bool?Boolean.toString(active):active?"1":"0"));
    }
    public static void main(String[] args)throws Exception {
        var dimensions=List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end","custom:moon","unavailable");
        int graphs=0;
        for(var family:CompileOptionControl.Family.values())for(var key:family.keys) {
            var active=fixture(key,true,true);var nativeGraph=fixture(key,false,true);var wrongDefault=fixture(key,true,false);graphs+=3;
            for(var dim:dimensions) {
                boolean compatible=switch(family) {
                    case BSL,SILDUR,BLISS -> dim.equals("minecraft:overworld");
                    case PHOTON -> key.equals(CompileOptionControl.PHOTON_DEAD_MIP)
                        ?Set.of("minecraft:the_nether","minecraft:the_end").contains(dim)
                        :key.equals(CompileOptionControl.PHOTON_DEPTH)?dim.equals("minecraft:overworld"):dimensions.indexOf(dim)<3;
                    case ASTRALEX,SOLAS,REIMAGINED,UNBOUND -> key.endsWith("DEAD_MIP_CHAIN")
                        ?dimensions.indexOf(dim)<3:dim.equals("minecraft:overworld");
                    case MAKEUP -> key.equals(CompileOptionControl.MAKEUP_AO_HALF)
                        ?dimensions.indexOf(dim)<3:dim.equals("minecraft:overworld");
                };
                check(CompileOptionControl.loadedGraphNeedsDimensionRollback(active,dim)==!compatible,key+" active domain "+dim);
                check(!CompileOptionControl.loadedGraphNeedsDimensionRollback(nativeGraph,dim),key+" native never triggers "+dim);
                check(!CompileOptionControl.loadedGraphNeedsDimensionRollback(wrongDefault,dim),key+" unowned default does not masquerade as owned "+dim);
                // A replacement loaded graph in the same next dimension must be inspected again.
                check(!CompileOptionControl.loadedGraphNeedsDimensionRollback(nativeGraph,dim),"same dimension native graph");
                check(CompileOptionControl.loadedGraphNeedsDimensionRollback(active,dim)==!compatible,"same dimension changed graph");
            }
        }
        var bsl=CompileOptionControl.Family.BSL;var count=new AtomicInteger();
        CompileOptionControl.configureAdmission(bsl.id(),OptimizationId.AO_RENDER_SCALE,()->{count.incrementAndGet();return true;});
        var key=Set.of(CompileOptionControl.BSL_AO_HALF);
        check(!CompileOptionControl.permissions(bsl,OptimizationSnapshot.noneRequested(),key).get(OptimizationId.AO_RENDER_SCALE),"passed Off flags refuse admission");
        check(count.get()==0,"disabled flag makes zero supplier calls");
        check(CompileOptionControl.permissions(bsl,OptimizationSnapshot.allRequested(),key).get(OptimizationId.AO_RENDER_SCALE),"passed future Apply flags enable admission");
        check(count.get()==1,"enabled declared feature calls once");
        CompileOptionControl.permissions(bsl,OptimizationSnapshot.allRequested(),Set.of());
        check(count.get()==1,"undeclared graph makes zero supplier calls");
        CompileOptionControl.configureAdmission(bsl.id(),OptimizationId.AO_RENDER_SCALE,()->{throw new IllegalStateException("resource lost");});
        check(!CompileOptionControl.permissions(bsl,OptimizationSnapshot.allRequested(),key).get(OptimizationId.AO_RENDER_SCALE),"failed supplier retains strict native fallback");
        System.out.println("PASS "+assertions+" actual Iris typed/domain/admission assertions; "+graphs+" parser graphs; no client/GL/readiness claim");
    }
}
