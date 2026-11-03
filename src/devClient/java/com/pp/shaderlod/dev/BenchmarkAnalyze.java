package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import java.nio.file.*;
import java.util.*;

/** Headless entry point: the developer runner and tuner use the identical core analyzer. */
public final class BenchmarkAnalyze {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().serializeNulls().registerTypeAdapter(Double.class,
        (JsonSerializer<Double>)(v,t,c)->Double.isFinite(v)?new JsonPrimitive(v):JsonNull.INSTANCE).create();
    private BenchmarkAnalyze(){}
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Expected analysis request JSON path");
        var request=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        var decision=analyze(request);Path output=Path.of(request.get("output").getAsString());Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output,JSON.toJson(decision));System.out.println(decision.verdict()+": "+String.join("; ",decision.reasons()));
    }
    public static BenchmarkAnalysis.Decision analyze(JsonObject request)throws Exception{
        var policy=new AnalysisPolicy(integer(request,"block_frames",32),number(request,"minimum_block_ms",50),integer(request,"minimum_blocks",2),integer(request,"minimum_brackets",2),integer(request,"minimum_repeats",1),text(request,"calibration",""),number(request,"stutter_ms",50));
        var criterion=new PracticalCriterion(request.get("minimum_benefit_ms").getAsDouble(),request.get("maximum_drift_ms").getAsDouble(),request.get("maximum_p95_regression_ms").getAsDouble(),text(request,"criterion_reason","predeclared local control diagnostic"));
        var brackets=new ArrayList<BenchmarkAnalysis.Bracket>();int i=0;
        for(var entry:request.getAsJsonArray("brackets")){
            var b=entry.getAsJsonObject();brackets.add(new BenchmarkAnalysis.Bracket("bracket-"+(i++),b.get("repeat_id").getAsString(),read(Path.of(b.get("before").getAsString()),request,policy),read(Path.of(b.get("candidate").getAsString()),request,policy),read(Path.of(b.get("return").getAsString()),request,policy)));
        }
        return BenchmarkAnalysis.analyze(new BenchmarkAnalysis.Request(text(request,"comparison_id","controls"),BenchmarkAnalysis.ControlKind.valueOf(text(request,"control_kind","KNOWN_NULL")),BenchmarkAnalysis.Availability.AVAILABLE,BenchmarkAnalysis.Activation.ACTIVE,brackets,criterion,policy,List.of()));
    }
    private static BenchmarkAnalysis.Window read(Path directory,JsonObject request,AnalysisPolicy policy)throws Exception{
        var file=directory.resolve("result.json");var manifest=JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("manifest");var window=manifest.getAsJsonArray("windows").get(0).getAsJsonObject();
        var guard=JsonParser.parseString(Files.readString(directory.resolve("guard.json"))).getAsJsonObject();if(!guard.get("valid").getAsBoolean())throw new IllegalStateException("Invalid captured window: "+directory);
        var before=JsonParser.parseString(Files.readString(directory.resolve("before.json"))).getAsJsonObject();var pins=new TreeMap<String,String>();
        String control=text(request,"control_class","unchanged-no-reload");pins.put("control_class",control);for(String key:List.of("pack","width","height","distance","gpu_renderer","gpu_driver","effective_options","dh_mode","dh_radius","dh_installed"))if(before.has(key))pins.put(key,before.get(key).toString());
        if(control.equals("unchanged-no-reload"))pins.put("pipeline_identity",before.get("pipeline_identity").toString());
        pins.put("clock","shared-FrameMeasurement-GameRenderer");pins.put("observer",window.get("observerMode").getAsString());pins.put("temporal","native");pins.put("cache","warm logical scene; OS cache uncontrolled");
        var rows=new ArrayList<FrameMeasurement.Sample>();for(var value:window.getAsJsonArray("samples")){var s=value.getAsJsonObject();rows.add(new FrameMeasurement.Sample(s.get("frameId").getAsLong(),s.get("epoch").getAsLong(),nullable(s,"totalFrameNs"),s.get("renderWallNs").getAsLong(),nullable(s,"renderThreadCpuNs"),nullable(s,"processCpuNs"),nullable(s,"gpuElapsedNs"),s.get("gpuStatus").getAsString()));}
        return new BenchmarkAnalysis.Window(directory.toAbsolutePath().normalize().toString(),new BenchmarkAnalysis.Stratum(pins),BenchmarkStatistics.summarize(rows,policy));
    }
    private static Long nullable(JsonObject o,String k){return !o.has(k)||o.get(k).isJsonNull()?null:o.get(k).getAsLong();}
    private static int integer(JsonObject o,String k,int fallback){return o.has(k)?o.get(k).getAsInt():fallback;}
    private static double number(JsonObject o,String k,double fallback){return o.has(k)?o.get(k).getAsDouble():fallback;}
    private static String text(JsonObject o,String k,String fallback){return o.has(k)?o.get(k).getAsString():fallback;}
}
