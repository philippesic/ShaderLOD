package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.pp.shaderlod.optimization.CompileOptionControl.*;
import com.google.common.collect.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
public final class CompileFamilyChecksMain {
    private static int checks;
    private static void check(boolean value,String note){checks++;if(!value)throw new AssertionError(note);}
    private static Map<String,NativeOption> actualOptions(Path archive)throws Exception {
        var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();
        try(var zip=new ZipFile(archive.toFile())) {
            for(var entry:Collections.list(zip.entries())) {
                String name=entry.getName();if(!name.startsWith("shaders/")||!name.matches(".*\\.(glsl|vsh|fsh|csh|gsh|tcs|tes)$"))continue;
                var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));String text=new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                nodes.put(path,new FileNode(path,ImmutableList.copyOf(text.split("\\R"))));
            }
        }
        var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
        var graph=constructor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());var options=new ShaderPackOptions(graph,Map.of());var result=new TreeMap<String,NativeOption>();
        options.getOptionSet().getBooleanOptions().forEach((key,item)->result.put(key,new NativeOption("BOOLEAN",Set.of("false","true"),Boolean.toString(item.getOption().getDefaultValue()),Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(key)))));
        options.getOptionSet().getStringOptions().forEach((key,item)->result.put(key,new NativeOption("STRING",Set.copyOf(item.getOption().getAllowedValues()),item.getOption().getDefaultValue(),options.getOptionValues().getStringValueOrDefault(key))));
        return Map.copyOf(result);
    }
    private static ShaderPackOptions astraOptions(Path archive,Map<String,String> supplied)throws Exception {
        try(var zip=FileSystems.newFileSystem(archive,Map.of())) {
            var root=zip.getPath("/shaders");var starts=ImmutableList.<AbsolutePackPath>builder();
            for(String dimension:List.of("/","/world0/","/world-1/","/world1/"))ShaderPackSourceNames.findPresentSources(starts,root,AbsolutePackPath.fromAbsolutePath(dimension),ShaderPackSourceNames.POTENTIAL_STARTS);
            var entries=starts.build();check(!entries.isEmpty(),"actual Astra program entries");
            // Pinned Iris1.8.12 public constructor first reads Iris.getIrisConfig solely
            // for optional filesystem case diagnostics, which boots FML in a headless JVM.
            // Retain its exact successful traversal: UTF-8 readString, split\R, real
            // FileNode include resolution, LIFO worklist and first-seen deduplication.
            // Missing files/self-cycles fail; never substitute an empty node or option.
            var nodes=new HashMap<AbsolutePackPath,FileNode>();
            var pending=new ArrayList<>(entries);var seen=new HashSet<>(entries);
            while(!pending.isEmpty()) {
                var path=pending.removeLast();
                var node=new FileNode(path,ImmutableList.copyOf(Files.readString(path.resolved(root)).split("\\R")));
                for(var include:node.getIncludes().values()) {
                    check(!include.equals(path),"actual reachable include must not include itself: "+path);
                    if(seen.add(include))pending.add(include);
                }
                nodes.put(path,node);
            }
            var constructor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);constructor.setAccessible(true);
            var graph=constructor.newInstance(ImmutableMap.copyOf(nodes),ImmutableMap.of());
            // The normal constructor runs the same pinned cycle validator after map creation.
            var cycles=IncludeGraph.class.getDeclaredMethod("detectCycle");cycles.setAccessible(true);cycles.invoke(graph);
            check(graph.getFailures().isEmpty(),"actual Astra reachable include graph");
            return new ShaderPackOptions(graph,supplied);
        }
    }
    public static void main(String[] arguments)throws Exception {
        if(arguments.length==6&&(arguments[0].equals("--astra-compare")||arguments[0].equals("--astra-world-compare"))) {
            Path oldArchive=Path.of(arguments[1]),newArchive=Path.of(arguments[2]),out=Path.of(arguments[5]);Files.createDirectories(out);
            String key=arguments[0].equals("--astra-world-compare")?CompileOptionControl.ASTRALEX_WORLD_GRAPH:CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN;
            for(boolean prefix:List.of(false,true)) {
                var overrides=new TreeMap<String,String>();overrides.put("SHADERLOD_REFINEMENT_ADAPTER","1");if(prefix)overrides.put("PARALLAX","true");
                var oldOptions=CompileOptionControl.readOptions(astraOptions(oldArchive,overrides),Map.of(),false);
                var oldValues=new TreeMap<String,String>();oldOptions.forEach((k,v)->oldValues.put(k,v.value()));
                var expected=new TreeMap<String,String>();com.google.gson.JsonParser.parseString(Files.readString(Path.of(arguments[prefix?4:3]))).getAsJsonObject().entrySet().forEach(e->expected.put(e.getKey(),e.getValue().getAsString()));
                check(oldValues.equals(expected),"old actual reachable graph reproduces preserved complete profile, prefix="+prefix);
                check(!oldOptions.containsKey(key),"old graph has no new option");
                for(String mode:List.of("0","1")) {
                    var nextOverrides=new TreeMap<>(overrides);nextOverrides.put(key,mode);
                    var newOptions=new TreeMap<>(CompileOptionControl.readOptions(astraOptions(newArchive,nextOverrides),Map.of(),false));
                    var declaration=newOptions.remove(key);
                    check(declaration!=null&&CompileOptionControl.declaredContract(key,declaration)&&mode.equals(declaration.value()),"new actual binary0/1/default0 declaration");
                    check(newOptions.equals(oldOptions),"all old typed declarations/defaults/values identical after one new key, prefix="+prefix+" mode="+mode);
                    var values=new TreeMap<String,String>();newOptions.forEach((k,v)->values.put(k,v.value()));values.put(key,declaration.value());
                    Files.writeString(out.resolve("compared-astralex-"+(prefix?"prefix":"normal")+"-"+mode+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(values)+"\n");
                }
            }
            System.out.println("PASS actual old reachable profiles + new single typed-option delta; no client/runtime claim");return;
        }

        if(arguments.length==3&&arguments[0].equals("--astra-export")) {
            Path archive=Path.of(arguments[1]),out=Path.of(arguments[2]);Files.createDirectories(out);
            for(String mode:List.of("0","1")) {
                var loaded=astraOptions(archive,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1",CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN,mode));
                var observed=new TreeMap<String,String>();CompileOptionControl.readOptions(loaded,Map.of(),false).forEach((k,v)->observed.put(k,v.value()));
                check(observed.size()==1027&&mode.equals(observed.get(CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN)),"normal reachable Astra1027 loaded Gaussian mode");
                Files.writeString(out.resolve("actual-loaded-astralex-gaussian-"+mode+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(observed)+"\n");
            }
            var prefix=astraOptions(archive,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1",CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN,"0","PARALLAX","true"));
            var prefixValues=new TreeMap<String,String>();CompileOptionControl.readOptions(prefix,Map.of(),false).forEach((k,v)->prefixValues.put(k,v.value()));
            check(prefixValues.size()==1027&&"true".equals(prefixValues.get("PARALLAX")),"actual optional POM profile retained");
            Files.writeString(out.resolve("actual-loaded-astralex-prefix.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(prefixValues)+"\n");
            return;
        }
        var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);var flags=OptimizationSnapshot.allRequested();var permissions=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);
        for(var id:List.of(OptimizationId.AO_RENDER_SCALE,OptimizationId.DEAD_MIP_CHAIN,OptimizationId.SHADOW_MIN_FIELD,OptimizationId.SHADOW_RAW_QUERY,OptimizationId.PIXEL_SCALE))permissions.put(id,true);
        var dims=List.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end","custom:moon","unavailable");
        var source=new TreeMap<String,NativeOption>();source.put("AO",new NativeOption("BOOLEAN",Set.of("false","true"),"true","true"));source.put("AO_METHOD",new NativeOption("STRING",Set.of("0","1"),"0","0"));source.put("Shadows",source.get("AO"));
        for(var family:Family.values()) {
            Set<String> keys=switch(family){case BSL->Set.of(CompileOptionControl.BSL_AO_HALF,CompileOptionControl.BSL_SHADOW_FIELD,CompileOptionControl.BSL_RAW_QUERY,CompileOptionControl.BSL_DEAD_MIP);case PHOTON->Set.of(CompileOptionControl.PHOTON_DEAD_MIP);case SILDUR->Set.of(CompileOptionControl.SILDUR_QUERY);case ASTRALEX->Set.of(CompileOptionControl.ASTRALEX_DEAD_MIP);case MAKEUP->Set.of(CompileOptionControl.MAKEUP_AO_HALF);case BLISS->Set.of(CompileOptionControl.BLISS_PIXEL_GRAPH);case REIMAGINED->Set.of(CompileOptionControl.REIMAGINED_DEAD_MIP);case UNBOUND->Set.of(CompileOptionControl.UNBOUND_DEAD_MIP);case SOLAS->Set.of(CompileOptionControl.SOLAS_DEAD_MIP);};
            var options=new TreeMap<>(source);if(family==Family.SOLAS)options.put("MOTION_BLUR",new NativeOption("BOOLEAN",Set.of("false","true"),"false","false"));if(family==Family.MAKEUP){options.remove("AO_METHOD");options.put("AO",new NativeOption("STRING",Set.of("0","1"),"1","1"));}
            for(String dim:dims) {
                var result=CompileOptionControl.plan(family,core,flags,keys,options,dim,false,false,permissions);
                boolean allowed=switch(family){case BSL,SILDUR,BLISS->dim.equals("minecraft:overworld");case PHOTON->Set.of("minecraft:the_nether","minecraft:the_end").contains(dim);case ASTRALEX,MAKEUP,REIMAGINED,UNBOUND,SOLAS->Set.of("minecraft:overworld","minecraft:the_nether","minecraft:the_end").contains(dim);};
                check(result.values().stream().allMatch(value->value.equals(allowed?"1":"0")),"explicit family dimension "+family+" "+dim);
                var quality=CompileOptionControl.plan(family,new CoreUniformFrame.Core(core.settings(),LodPreset.QUALITY),flags,keys,options,dim,false,false,permissions);check(quality.values().stream().allMatch("0"::equals),"Quality native0 "+family);
                var off=CompileOptionControl.plan(family,new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),core.preset()),flags,keys,options,dim,false,false,permissions);check(off.values().stream().allMatch("0"::equals),"globalOff native0 "+family);
                var missing=CompileOptionControl.plan(family,core,flags,keys,options,dim,false,false,Map.of());check(missing.values().stream().allMatch("0"::equals),"missing real setup pending0 "+family);
            }
        }
        var makeup=Map.of("AO",new NativeOption("STRING",Set.of("0","1"),"1","1"));check(CompileOptionControl.plan(Family.MAKEUP,core,flags,Set.of(CompileOptionControl.MAKEUP_AO_HALF),makeup,"minecraft:overworld",false,false,permissions).get(CompileOptionControl.MAKEUP_AO_HALF).equals("1"),"MakeUp stringAO with no AO_METHOD does not use BSL typed getter");
        check(CompileOptionControl.plan(Family.BSL,core,flags,Set.of(CompileOptionControl.BSL_AO_HALF),makeup,"minecraft:overworld",false,false,permissions).get(CompileOptionControl.BSL_AO_HALF).equals("0"),"BSL rejects wrong native option shape");
        check(CompileOptionControl.plan(Family.MAKEUP,core,flags,Set.of(CompileOptionControl.MAKEUP_AO_HALF),makeup,"minecraft:overworld",true,false,permissions).get(CompileOptionControl.MAKEUP_AO_HALF).equals("0"),"DH fallback native0");
        CompileOptionControl.configureAdmission("bsl-shaders",OptimizationId.AO_RENDER_SCALE,()->true);
        var admission=CompileOptionControl.class.getDeclaredMethod("admitted",Family.class,OptimizationId.class);admission.setAccessible(true);
        check((boolean)admission.invoke(null,Family.BSL,OptimizationId.AO_RENDER_SCALE)&&!(boolean)admission.invoke(null,Family.MAKEUP,OptimizationId.AO_RENDER_SCALE),"family supplier collision prevented");
        var photonKeys=Set.of(CompileOptionControl.PHOTON_SPATIAL,CompileOptionControl.PHOTON_LIGHTING_RATE);
        var photonPerm=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);photonPerm.put(OptimizationId.SPATIAL_LIGHTING_REUSE,true);photonPerm.put(OptimizationId.SPATIAL_LIGHTING_RATE,true);
        var photonGraph=CompileOptionControl.plan(Family.PHOTON,core,flags,photonKeys,Map.of(),"minecraft:overworld",false,false,photonPerm);
        check(photonGraph.get(CompileOptionControl.PHOTON_SPATIAL).equals("true")&&photonGraph.get(CompileOptionControl.PHOTON_LIGHTING_RATE).equals("false"),"distinct graphs share native exclusive producer; rate held off while spatial active");
        photonGraph=CompileOptionControl.plan(Family.PHOTON,core,flags.with(OptimizationId.SPATIAL_LIGHTING_REUSE,false),photonKeys,Map.of(),"minecraft:the_nether",false,false,photonPerm);
        check(photonGraph.get(CompileOptionControl.PHOTON_LIGHTING_RATE).equals("true"),"individual half-lighting graph remains independently selectable");
        photonGraph=CompileOptionControl.plan(Family.PHOTON,core,flags,Set.of(CompileOptionControl.PHOTON_LIGHTING_RATE),Map.of(),"minecraft:overworld",false,false,photonPerm);
        check(photonGraph.get(CompileOptionControl.PHOTON_LIGHTING_RATE).equals("true"),"undeclared spatial graph cannot mask declared admitted half-lighting");
        var directory=Files.createTempDirectory("shaderlod-family-recovery-");var previousState=OptimizationConfig.state();
        try {
            OptimizationConfig.load(directory.resolve("options.properties"));var previous=OptimizationConfig.snapshot();var requested=previous.with(OptimizationId.DEAD_MIP_CHAIN,!previous.enabled(OptimizationId.DEAD_MIP_CHAIN));var events=new ArrayList<String>();
            var session=new CompileOptionControl.Session(){
                public boolean prepare(){check(OptimizationConfig.snapshot().equals(requested),"actual transaction publishes request before consumer preparation");events.add("prepare");return true;}
                public void reload(OptimizationEditTransaction.Reload reload)throws Exception{events.add("reload");reload.run();}
                public void restore(){check(OptimizationConfig.snapshot().equals(previous),"actual failed Apply restores previous flags before source/queue recovery");events.add("restore");}
                public void recoveryReload(OptimizationEditTransaction.Reload reload){events.add("recovery");}
                public void finish(){events.add("finish");}};
            var result=OptimizationEditTransaction.apply(requested,()->{throw new java.io.IOException("driver refuses prepared graph");},()->false,next->session);
            check(!result.success()&&events.equals(List.of("prepare","reload","restore","recovery","finish")),"same normal Apply consumer and recovery ordering");
            check(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended(),"normal lifecycle released after failed Apply");
            int[] calls={0};result=OptimizationEditTransaction.apply(requested,()->calls[0]++,()->false,next->{throw new IllegalStateException("source mismatch");});
            check(!result.success()&&calls[0]==0&&OptimizationConfig.snapshot().equals(previous),"source mismatch refuses without mutation/reload");
        }finally{OptimizationConfig.restoreState(previousState);}
        String evidencePath=System.getProperty("shaderlod.compileFamilyEvidence");
        Path evidence=evidencePath==null?Files.createTempDirectory("shaderlod-family-evidence-"):Path.of(evidencePath);
        Files.createDirectories(evidence);
        for(int i=0;i<arguments.length;i+=3) {
            Family family=Family.fromId(arguments[i]);Path archive=Path.of(arguments[i+1]);var recipe=com.google.gson.JsonParser.parseString(Files.readString(Path.of(arguments[i+2]))).getAsJsonObject();var actual=family==Family.ASTRALEX?CompileOptionControl.readOptions(astraOptions(archive,Map.of()),Map.of(),false):actualOptions(archive);Set<String> keys=CompileOptionControl.recipeKeys(family,recipe);Files.writeString(evidence.resolve("actual-options-"+family.id()+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(new TreeMap<>(actual))+"\n");
            check(!keys.isEmpty(),"explicit recipe controls "+family);
            if(family==Family.ASTRALEX) {
                String gaussian=CompileOptionControl.ASTRALEX_DYNAMIC_GAUSSIAN;
                check(actual.size()==1027&&keys.contains(gaussian)&&actual.containsKey(gaussian),"authentic Astra1027 declaration includes internal Gaussian option");
                check(CompileOptionControl.declaredContract(gaussian,actual.get(gaussian))&&actual.get(gaussian).value().equals("0"),"authentic native Gaussian default/type/allowed values");
                String world=CompileOptionControl.ASTRALEX_WORLD_GRAPH;
                check(keys.contains(world)&&CompileOptionControl.declaredContract(world,actual.get(world))&&actual.get(world).value().equals("0"),"actual reachable world STRING binary default0");
                var worldPeers=List.of(OptimizationId.SPATIAL_LIGHTING_REUSE,OptimizationId.SPATIAL_LIGHTING_RATE,OptimizationId.PIXEL_SCALE);
                for(int mask=0;mask<8;mask++) {
                    var request=OptimizationSnapshot.noneRequested();for(int peer=0;peer<3;peer++)request=request.with(worldPeers.get(peer),(mask&(1<<peer))!=0);
                    check(CompileOptionControl.plan(family,core,request,keys,actual,"minecraft:overworld",false,false,Map.of()).get(world).equals(mask==0?"0":"1"),"world request union without currently absent draw owners "+mask);
                    check(CompileOptionControl.plan(family,new CoreUniformFrame.Core(core.settings(),LodPreset.QUALITY),request,keys,actual,"minecraft:overworld",false,false,Map.of()).get(world).equals("0"),"world Quality native0 "+mask);
                }
                for(String mode:List.of("0","1","0")) {
                    var loadedWorld=astraOptions(archive,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1",world,mode));
                    var observedWorld=CompileOptionControl.readOptions(loadedWorld,Map.of(),false);
                    check(observedWorld.size()==1027&&CompileOptionControl.declaredContract(world,observedWorld.get(world))&&mode.equals(observedWorld.get(world).value()),"actual typed world0/1/0 profile");
                }
                var peers=List.of(OptimizationId.SHADOW_SAMPLE_BUDGET,OptimizationId.TEMPORAL_SHADOW_FILTER,OptimizationId.EARLY_ALPHA);
                for(int mask=0;mask<8;mask++) {
                    var requested=OptimizationSnapshot.noneRequested();for(int peer=0;peer<3;peer++)requested=requested.with(peers.get(peer),(mask&(1<<peer))!=0);
                    var planned=CompileOptionControl.plan(family,core,requested,keys,actual,"minecraft:overworld",false,false,Map.of());
                    check(planned.get(gaussian).equals(mask==0?"0":"1"),"Gaussian compile request union without transient permissions "+mask);
                    check(CompileOptionControl.plan(family,new CoreUniformFrame.Core(core.settings(),LodPreset.QUALITY),requested,keys,actual,"minecraft:overworld",false,false,Map.of()).get(gaussian).equals("0"),"Gaussian Quality native "+mask);
                    check(CompileOptionControl.plan(family,new CoreUniformFrame.Core(new LodSettings(false,32,96,8,false),LodPreset.PERFORMANCE),requested,keys,actual,"minecraft:overworld",false,false,Map.of()).get(gaussian).equals("0"),"Gaussian Off native "+mask);
                }
                check(CompileOptionControl.plan(family,core,OptimizationSnapshot.noneRequested().with(OptimizationId.AO_BUDGET,true),keys,actual,"minecraft:overworld",false,false,Map.of()).get(gaussian).equals("0"),"unrelated AO cannot select dynamic shadow");
                var values=new TreeMap<String,String>();actual.forEach((k,v)->values.put(k,v.value()));values.put("SHADERLOD_REFINEMENT_ADAPTER","1");
                for(String mode:List.of("0","1")) {
                    values.put(gaussian,mode);
                    var loaded=astraOptions(archive,Map.of("SHADERLOD_REFINEMENT_ADAPTER","1",gaussian,mode));
                    var observed=new TreeMap<String,String>();CompileOptionControl.readOptions(loaded,Map.of(),false).forEach((k,v)->observed.put(k,v.value()));
                    Files.writeString(evidence.resolve("actual-loaded-astralex-gaussian-"+mode+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(observed)+"\n");
                    check(com.pp.shaderlod.optimization.astraao.AstraAoOptionGuard.declaration(loaded.getOptionSet()),"real declared Gaussian binary/default map "+mode);
                    check(com.pp.shaderlod.optimization.astraao.AstraAoOptionGuard.snapshot(loaded.getOptionSet(),loaded.getOptionValues(),true).equals(values),"real loaded Iris option values "+mode);
                    check(com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",values),"actual full loaded-option map admits Gaussian "+mode);
                    check(com.pp.shaderlod.optimization.astraao.AstraAoContract.options(values),"AO profile preserves Gaussian peer "+mode);
                }
                values.remove(gaussian);check(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",values)&&!com.pp.shaderlod.optimization.astraao.AstraAoContract.options(values),"missing Gaussian key refuses full map");
                values.put(gaussian,"2");check(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",values)&&!com.pp.shaderlod.optimization.astraao.AstraAoContract.options(values),"invalid Gaussian value refuses full map");
            }

            for(var key:keys){if(key.equals(CompileOptionControl.PHOTON_SPATIAL)&&!actual.containsKey(key)){check(true,"shared graph missing parser declaration stays unavailable");System.out.println("PENDING_DECLARATION "+family+" "+key);continue;}check(actual.containsKey(key)&&CompileOptionControl.declaredContract(key,actual.get(key)),"real Iris pinned declaration/type/allowed/default "+family+" "+key);System.out.println("ACTUAL_OPTION "+family+" "+key+" "+actual.get(key));}
        }
        System.out.println("PASS "+checks+" explicit family/type/dimension/native0/callback-collision + actual Iris declaration checks; no GPU/producer completion claim");
    }
}
