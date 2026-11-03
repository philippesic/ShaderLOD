package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;
import com.google.common.collect.*;
import net.irisshaders.iris.shaderpack.include.*;
import net.irisshaders.iris.shaderpack.option.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Actual OptionSet/OptionValues and actual changed reader; no client/GL or fabricated readiness. */
public final class LoadedQueueDimensionCheck {
    static void check(boolean ok,String note){if(!ok)throw new AssertionError(note);}
    public static void main(String[] args)throws Exception {
        var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();
        try(var zip=new ZipFile(args[0])) {
            for(var entry:Collections.list(zip.entries())) {
                String name=entry.getName();if(!name.startsWith("shaders/")||!name.matches(".*\\.(glsl|vsh|fsh|csh|gsh|tcs|tes)$"))continue;
                var path=AbsolutePackPath.fromAbsolutePath("/"+name.substring(8));
                nodes.put(path,new FileNode(path,ImmutableList.copyOf(new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R"))));
            }
        }
        var ctor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);ctor.setAccessible(true);
        var graph=ctor.newInstance(nodes.buildOrThrow(),ImmutableMap.of());
        var options=new ShaderPackOptions(graph,Map.of(CompileOptionControl.BSL_AO_HALF,"1"));
        check(options.getOptionValues().getStringValueOrDefault(CompileOptionControl.BSL_AO_HALF).equals("1"),"authentic loaded option1");
        var queue=new HashMap<String,String>();
            var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);
            var flags=OptimizationSnapshot.allRequested();
            for(String pending:List.of("0","1")) {
                queue.clear();queue.put(CompileOptionControl.BSL_AO_HALF,pending);queue.put("AO_METHOD","1");
                @SuppressWarnings("unchecked") var loaded=(Map<String,CompileOptionControl.NativeOption>)CompileOptionControl.readOptions(options,queue,false);
                @SuppressWarnings("unchecked") var effective=(Map<String,CompileOptionControl.NativeOption>)CompileOptionControl.readOptions(options,queue,true);
                check(loaded.get(CompileOptionControl.BSL_AO_HALF).value().equals("1"),"queue cannot conceal loaded graph1");
                check(effective.get(CompileOptionControl.BSL_AO_HALF).value().equals(pending),"pending-effective edits retained");
                var desired=CompileOptionControl.plan(CompileOptionControl.Family.BSL,core,flags,Set.of(CompileOptionControl.BSL_AO_HALF),effective,"minecraft:the_nether",false,false,Map.of(OptimizationId.AO_RENDER_SCALE,true));
                check(desired.get(CompileOptionControl.BSL_AO_HALF).equals("0"),"Nether requires native0");
                // Exact production unsafe predicate, fed by its actual changed reader and actual plan.
                boolean unsafe=loaded.get(CompileOptionControl.BSL_AO_HALF).value().equals("1")&&desired.get(CompileOptionControl.BSL_AO_HALF).equals("0");
                check(unsafe,"loaded1/queued"+pending+" requires pre-dimension reload");
                check(CompileOptionControl.loadedGraphNeedsDimensionRollback(options,"minecraft:the_nether"),"early actual loaded graph cannot be concealed by queue" );
                check(!CompileOptionControl.loadedGraphNeedsDimensionRollback(options,"minecraft:overworld"),"compatible loaded graph stays cheap");
                check(queue.get("AO_METHOD").equals("1")&&queue.get(CompileOptionControl.BSL_AO_HALF).equals(pending),"reader does not mutate queued intent");
                var deferred=Map.copyOf(queue);deferred.forEach((key,value)->queue.remove(key,value));queue.put(CompileOptionControl.BSL_AO_HALF,desired.get(CompileOptionControl.BSL_AO_HALF));
                deferred.forEach((key,value)->{if(Arrays.stream(CompileOptionControl.Family.values()).noneMatch(family->family.keys.contains(key)))queue.putIfAbsent(key,value);});
                check(queue.get(CompileOptionControl.BSL_AO_HALF).equals("0")&&queue.get("AO_METHOD").equals("1"),"owned option rederived, unrelated pending queue restored");
                System.out.println("PASS actual Iris loaded1/queued"+pending+" -> Nether native0 requires reload; pending-effective/unrelated intent retained");
            }
        var photon=CompileOptionControl.plan(CompileOptionControl.Family.PHOTON,core,flags,Set.of(CompileOptionControl.PHOTON_LIGHTING_RATE),Map.of(),"minecraft:overworld",false,false,Map.of(OptimizationId.SPATIAL_LIGHTING_REUSE,true,OptimizationId.SPATIAL_LIGHTING_RATE,true));
        check(photon.get(CompileOptionControl.PHOTON_LIGHTING_RATE).equals("true"),"undeclared spatial does not mask declared admitted lighting");
        System.out.println("CPU reader/dimension-decision evidence only; actual reload/pipeline/driver unverified");
    }
}
