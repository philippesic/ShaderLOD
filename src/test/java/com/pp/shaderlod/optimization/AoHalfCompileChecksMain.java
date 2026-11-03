package com.pp.shaderlod.optimization;

import com.google.common.collect.*;
import com.pp.shaderlod.*;
import com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import java.nio.file.*;
import java.util.*;

/** Actual expanded family wrappers enter the pinned Iris parser; no client or invented admission evidence. */
public final class AoHalfCompileChecksMain {
    private static int checks;
    private static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    public static void main(String[] args)throws Exception {
        for(int row=0;row<args.length;row+=3)run(args[row],Path.of(args[row+1]),Path.of(args[row+2]));
        if(args.length==0)throw new IllegalArgumentException("family actual-expanded-wrapper-directory recipe.json required");
        System.out.println("PASS "+checks+" actual Iris AO graph declarations/planner/profile/dimension-recovery checks; no GPU/quality claim");
    }
    private static void run(String familyName,Path sourceDirectory,Path recipePath)throws Exception {
        var family=CompileOptionControl.Family.fromId(familyName);
        String key=switch(family){
            case REIMAGINED->CompileOptionControl.REIMAGINED_AO_HALF;case BLISS->CompileOptionControl.BLISS_AO_HALF;
            case UNBOUND->CompileOptionControl.UNBOUND_AO_HALF;case SOLAS->CompileOptionControl.SOLAS_AO_HALF;
            case ASTRALEX->CompileOptionControl.ASTRALEX_AO_HALF;case SILDUR->CompileOptionControl.SILDUR_AO_HALF;
            default->throw new IllegalArgumentException("Not a new half-AO family: "+familyName);
        };
        IncludeGraph graph;
        Path shaderRoot=sourceDirectory.resolve("shaders");
        if(Files.isDirectory(shaderRoot)){
            // Reuse the actual ShaderPack constructor discovery, as in SolasParserProbeR3.
            var starts=ImmutableList.<AbsolutePackPath>builder();
            for(String dimension:List.of("/","/world0/","/world-1/","/world1/"))ShaderPackSourceNames.findPresentSources(starts,shaderRoot,AbsolutePackPath.fromAbsolutePath(dimension),ShaderPackSourceNames.POTENTIAL_STARTS);
            var roots=starts.build();check(!roots.isEmpty(),familyName+" actual Iris program roots discovered");
            graph=new IncludeGraph(shaderRoot,roots,false);check(graph.getFailures().isEmpty(),familyName+" actual reachable includes complete: "+graph.getFailures());
            System.out.println("ENTRY_DISCOVERY "+familyName+" roots="+roots.size()+" actual ShaderPackSourceNames/IncludeGraph");
        }else{
            // Existing owner-provided fully expanded normal wrappers are also genuine parser inputs.
            var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();
            try(var sources=Files.list(sourceDirectory)){for(var source:sources.filter(Files::isRegularFile).filter(p->p.toString().matches(".*\\.(glsl|fsh|vsh|gsh|csh|tcs|tes|settings)$")).sorted().toList()){
                var path=AbsolutePackPath.fromAbsolutePath("/"+source.getFileName());nodes.put(path,new FileNode(path,ImmutableList.copyOf(Files.readString(source).split("\\R"))));
            }}
            var ctor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);ctor.setAccessible(true);graph=ctor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());
        }
        var options=new ShaderPackOptions(graph,Map.of());
        var actual=new TreeMap<>(CompileOptionControl.readOptions(options,Map.of(),false));
        check(actual.containsKey(key)&&CompileOptionControl.declaredContract(key,actual.get(key)),familyName+" actual STRING {0,1} default0");
        var recipe=com.google.gson.JsonParser.parseString(Files.readString(recipePath)).getAsJsonObject();
        var keys=CompileOptionControl.recipeKeys(family,recipe);
        check(keys.contains(key),familyName+" actual recipe advertises own half graph");
        var selected=new TreeMap<String,String>();for(var entry:actual.entrySet())selected.put(entry.getKey(),entry.getValue().value());
        String adapter=Set.of(CompileOptionControl.Family.BLISS,CompileOptionControl.Family.SILDUR).contains(family)?"SHADERLOD_EFFECT_ADAPTER":"SHADERLOD_REFINEMENT_ADAPTER";
        check(actual.containsKey(adapter)&&actual.get(adapter).allowed().contains("1"),"actual adapter declares selected1");selected.put(adapter,"1");
        String censusDirectory=System.getProperty("shaderlod.aoCompileCensusDirectory");
        if(censusDirectory!=null){Path receiver=Path.of(censusDirectory);Files.createDirectories(receiver);Files.writeString(receiver.resolve(familyName+"-actual-selected-options.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(selected)+"\n");}
        check(GeneratedRendererOptionContract.accepts(familyName,selected),familyName+" actual selected profile admitted "+profileDiff(familyName,selected));
        var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
        var flags=OptimizationSnapshot.noneRequested().with(OptimizationId.AO_RENDER_SCALE,true);
        var admissions=Map.of(OptimizationId.AO_RENDER_SCALE,true);
        check(plan(family,core,flags,keys,actual,"minecraft:overworld",false,false,admissions).get(key).equals("1"),familyName+" native AO + setup permits requested half");
        for(String dim:List.of("minecraft:the_nether","minecraft:the_end","custom:moon","unavailable"))
            check(plan(family,core,flags,keys,actual,dim,false,false,admissions).get(key).equals("0"),familyName+" dimension native0 "+dim);
        check(plan(family,core,flags,keys,actual,"minecraft:overworld",true,false,admissions).get(key).equals("0"),"DH native0");
        check(plan(family,core,flags,keys,actual,"minecraft:overworld",false,true,admissions).get(key).equals("0"),"Voxy native0");
        check(plan(family,core,flags,keys,actual,"minecraft:overworld",false,false,Map.of()).get(key).equals("0"),"absent actual setup remains0");
        check(plan(family,core,OptimizationSnapshot.noneRequested(),keys,actual,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"requestOff native0");
        check(plan(family,new CoreUniformFrame.Core(core.settings(),LodPreset.QUALITY),flags,keys,actual,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"Quality native0");
        check(plan(family,new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),core.preset()),flags,keys,actual,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"globalOff native0");
        var restrictions=switch(family){
            case REIMAGINED,UNBOUND->Map.of("RP_MODE","0","SSAO_QUALI_DEFINE","0","TAA","false","GENERATED_NORMALS","true","COATED_TEXTURES","true");
            case BLISS->Map.of("indirect_effect","0","TAA","false","TAA_UPSCALING","true",CompileOptionControl.BLISS_PIXEL_GRAPH,"1");
            case SOLAS->Map.of("SSAO","false");
            case ASTRALEX->Map.of("AO","false","AO_QUALITY","0","AO_FOVSCALE","0");
            case SILDUR->Map.of("SSDO","false","TAA","false","nMap","1",CompileOptionControl.SILDUR_QUERY,"1");
            default->throw new IllegalArgumentException("No AO restrictions for "+familyName);
        };
        for(var restriction:restrictions.entrySet()){
            var changed=new TreeMap<>(actual);var nativeOption=changed.get(restriction.getKey());check(nativeOption!=null,"actual native restriction declared "+restriction.getKey());
            changed.put(restriction.getKey(),new CompileOptionControl.NativeOption(nativeOption.type(),nativeOption.allowed(),nativeOption.defaultValue(),restriction.getValue()));
            check(plan(family,core,flags,keys,changed,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"native restriction retains0 "+restriction.getKey());
            changed.remove(restriction.getKey());check(plan(family,core,flags,keys,changed,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"missing native declaration retains0 "+restriction.getKey());
            changed.put(restriction.getKey(),new CompileOptionControl.NativeOption(nativeOption.type().equals("BOOLEAN")?"STRING":"BOOLEAN",nativeOption.allowed(),nativeOption.defaultValue(),nativeOption.value()));
            check(plan(family,core,flags,keys,changed,"minecraft:overworld",false,false,admissions).get(key).equals("0"),"wrong typed native getter shape retains0 "+restriction.getKey());
        }
        if(family==CompileOptionControl.Family.BLISS){
            var pixelFlags=flags.with(OptimizationId.PIXEL_SCALE,true);var both=plan(family,core,pixelFlags,keys,actual,"minecraft:overworld",false,false,Map.of(OptimizationId.AO_RENDER_SCALE,true,OptimizationId.PIXEL_SCALE,true));
            check("1".equals(both.get(CompileOptionControl.BLISS_PIXEL_GRAPH))&&"0".equals(both.get(key)),"cannot combine AO half and requested admitted whole-frame scale");
        }
        if(family==CompileOptionControl.Family.SILDUR){
            var queryFlags=flags.with(OptimizationId.SHADOW_MIN_FIELD,true);var both=plan(family,core,queryFlags,keys,actual,"minecraft:overworld",false,false,Map.of(OptimizationId.AO_RENDER_SCALE,true,OptimizationId.SHADOW_MIN_FIELD,true));
            check("1".equals(both.get(CompileOptionControl.SILDUR_QUERY))&&"0".equals(both.get(key)),"cannot combine AO half and admitted shadow-query graph");
        }
        var loaded=new ShaderPackOptions(graph,Map.of(key,"1"));
        check(!CompileOptionControl.loadedGraphNeedsDimensionRollback(loaded,"minecraft:overworld"),"own OW loaded graph retained");
        for(String dim:List.of("minecraft:the_nether","minecraft:the_end","custom:moon","unavailable"))check(CompileOptionControl.loadedGraphNeedsDimensionRollback(loaded,dim),"loaded half graph rolls back before "+dim);
        selected.put(key,"1");check(GeneratedRendererOptionContract.accepts(familyName,selected),"enabled graph profile coexists with peers");
        String depthKey=switch(family){
            case REIMAGINED->CompileOptionControl.REIMAGINED_DEPTH;case BLISS->CompileOptionControl.BLISS_DEPTH;
            case UNBOUND->CompileOptionControl.UNBOUND_DEPTH;case SOLAS->CompileOptionControl.SOLAS_DEPTH;
            case ASTRALEX->CompileOptionControl.ASTRALEX_DEPTH;case SILDUR->CompileOptionControl.SILDUR_DEPTH;
            default->throw new IllegalArgumentException("No depth control for "+familyName);
        };
        selected.put(depthKey,"1");check(GeneratedRendererOptionContract.accepts(familyName,selected),"depth and AO controls normalize independently");
        if(family==CompileOptionControl.Family.ASTRALEX){selected.put(CompileOptionControl.ASTRALEX_DEAD_MIP,"1");check(GeneratedRendererOptionContract.accepts(familyName,selected),"AO, depth and late dead-mip private controls coexist");}
        selected.put("unrecognized_native_option","false");check(!GeneratedRendererOptionContract.accepts(familyName,selected),"extra native setting still refuses exact guard");selected.remove("unrecognized_native_option");
        selected.put(key,"2");check(!GeneratedRendererOptionContract.accepts(familyName,selected),"invalid half value refused");
        selected.remove(key);check(!GeneratedRendererOptionContract.accepts(familyName,selected),"missing half value refused");
        System.out.println("ACTUAL "+familyName+" parserOptions="+actual.size()+" key="+key);
    }
    private static String profileDiff(String family,Map<String,String> actual)throws Exception {
        try(var in=AoHalfCompileChecksMain.class.getResourceAsStream("/assets/shaderlod/rendererquad/generated-main-options.json")){
            var expected=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject(family);
            var missing=new TreeSet<>(expected.keySet());missing.removeAll(actual.keySet());var extra=new TreeSet<>(actual.keySet());extra.removeAll(expected.keySet());
            var changed=new TreeMap<String,String>();for(var entry:actual.entrySet())if(expected.has(entry.getKey())&&!expected.get(entry.getKey()).getAsString().equals(entry.getValue()))changed.put(entry.getKey(),expected.get(entry.getKey()).getAsString()+" -> "+entry.getValue());
            return "parser="+actual.size()+" expected="+expected.size()+" missing="+missing+" extra="+extra+" changed="+changed;
        }
    }
    private static Map<String,String> plan(CompileOptionControl.Family family,CoreUniformFrame.Core core,OptimizationSnapshot flags,Set<String> keys,Map<String,CompileOptionControl.NativeOption> actual,String dimension,boolean dh,boolean voxy,Map<OptimizationId,Boolean> admissions){return CompileOptionControl.plan(family,core,flags,keys,actual,dimension,dh,voxy,admissions);}
}
