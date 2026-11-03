package com.pp.shaderlod.tuning;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.benchmark.core.*;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.benchmark.BenchmarkScenario;
import com.pp.shaderlod.benchmark.BenchmarkScenario;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.measurement.FrameMeasurement;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Durable original state is committed before the first mutation. Every run gets a fresh receiver. */
final class TuningJournal {
    record Original(String family,boolean enabled,LodPreset preset,OptimizationSnapshot flags,boolean vsync,int fps,int windowFps,String pack,TuningSourceIdentity source) {}
    final Path directory,activeFile; final Properties metadata=new Properties();
    private final BufferedWriter raw,decisions;
    private final List<BenchmarkResult.Window> resultWindows=new ArrayList<>();
    private final List<BenchmarkAnalysis.Decision> resultDecisions=new ArrayList<>();
    private final List<String> windowLinks=new ArrayList<>();
    TuningConfirmation confirmation;
    TuningQualification.Evidence qualificationEvidence=TuningQualification.Evidence.untested();
    String sessionId="one-live-JVM-session";
    private int windowOrder;
    String lastWindowId(){return "window-"+(windowOrder-1);}
    private Map<String,String> latestPins=Map.of();
    TuningJournal(Path parent,Original original,Map<String,String> pins,int budget,boolean full) throws IOException {
        Files.createDirectories(parent);directory=Files.createTempDirectory(parent,"tune-"+Instant.now().toString().replace(':','-')+"-");
        activeFile=parent.resolve("undo.properties");
        latestPins=Map.copyOf(pins);metadata.putAll(pins);metadata.setProperty("started_at",Instant.now().toString());
        metadata.setProperty("budget_seconds",Integer.toString(budget));metadata.setProperty("full",Boolean.toString(full));
        metadata.setProperty("canonical_requests",Integer.toString(OptimizationId.values().length));
        metadata.setProperty("quality","Frame-time evidence only; native AA retained; visual quality not automatically certified");
        var undo=new Properties();undo.setProperty("family",original.family);undo.setProperty("enabled",Boolean.toString(original.enabled));
        undo.setProperty("preset",original.preset.name());undo.setProperty("vsync",Boolean.toString(original.vsync));undo.setProperty("fps",Integer.toString(original.fps));
        undo.setProperty("window_fps",Integer.toString(original.windowFps));
        undo.setProperty("pack",original.pack);undo.setProperty("run_directory",directory.toString());undo.setProperty("state","PENDING");
        original.source.store(undo);
        for(var id:OptimizationId.values())undo.setProperty(id.key(),Boolean.toString(original.flags.enabled(id)));
        for(var entry:pins.entrySet())undo.setProperty("original_"+entry.getKey(),entry.getValue());
        atomic(activeFile,undo);atomic(directory.resolve("undo-original.properties"),undo);
        raw=Files.newBufferedWriter(directory.resolve("samples.csv"));
        raw.write("trial,window,pose,frame,epoch,frame_wall_ns,render_wall_ns,render_cpu_ns,process_cpu_ns,gpu_ns,gpu_status\n");
        decisions=Files.newBufferedWriter(directory.resolve("decisions.csv"));decisions.write("candidate,verdict,gain_ms,drift_ms,threshold_ms,reason\n");
        save();
    }
    static Original read(Path file) throws IOException {
        return read(properties(file));
    }
    static Original read(Properties p) throws IOException {
        var flags=OptimizationSnapshot.noneRequested();
        for(var id:OptimizationId.values())flags=flags.with(id,Boolean.parseBoolean(p.getProperty(id.key(),"false")));
        return new Original(p.getProperty("family"),Boolean.parseBoolean(p.getProperty("enabled")),LodPreset.valueOf(p.getProperty("preset")),flags,Boolean.parseBoolean(p.getProperty("vsync")),Integer.parseInt(p.getProperty("fps")),Integer.parseInt(p.getProperty("window_fps",p.getProperty("fps"))),p.getProperty("pack"),TuningSourceIdentity.read(p));
    }
    static Properties properties(Path file) throws IOException {var p=new Properties();try(var r=Files.newBufferedReader(file)){p.load(r);}return p;}
    static void atomic(Path file,Properties data) throws IOException {
        var temp=Files.createTempFile(file.toAbsolutePath().getParent(),"tuner-",".tmp");
        try {try(var w=Files.newBufferedWriter(temp)){data.store(w,"ShaderLOD automatic tuner recovery/evidence");}
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    void samples(String trial,String window,int pose,List<FrameMeasurement.Sample> samples)throws IOException {
        if(samples.isEmpty())return;
        String prefix="window."+trial+"."+window+".pose-"+pose;
        var stats=TuningPolicy.stats(samples);metadata.setProperty(prefix+".frame_wall_valid",Integer.toString(stats.count()));
        if(stats.count()>=2){metadata.setProperty(prefix+".frame_wall_mean_ms",Double.toString(stats.meanMs()));metadata.setProperty(prefix+".frame_wall_p95_ms",Double.toString(stats.p95Ms()));
            metadata.setProperty(prefix+".frame_wall_median_ms",Double.toString(stats.summary().medianMs()));
            metadata.setProperty(prefix+".frame_wall_p99_ms",Double.toString(stats.summary().p99Ms()));
            metadata.setProperty(prefix+".tail_qualification",stats.summary().tailStatus()+"; n="+stats.count()+" duration_ms="+stats.summary().durationMs());}
        for(String metric:List.of("render_cpu","process_cpu","gpu")) {
            var values=samples.stream().map(s->switch(metric){case "render_cpu"->s.renderCpuNs();case "process_cpu"->s.processCpuNs();default->s.gpuNs();}).filter(Objects::nonNull).mapToLong(Long::longValue).summaryStatistics();
            metadata.setProperty(prefix+"."+metric+"_valid",Long.toString(values.getCount()));
            if(values.getCount()>0)metadata.setProperty(prefix+"."+metric+"_mean_ms",Double.toString(values.getAverage()/1e6));
        }
        for(var s:samples)raw.write(csv(trial)+","+csv(window)+","+pose+","+s.frame()+","+s.epoch()+","+value(s.frameWallNs())+","+s.renderWallNs()+","+value(s.renderCpuNs())+","+value(s.processCpuNs())+","+value(s.gpuNs())+","+csv(s.gpuStatus())+"\n");raw.flush();
    }
    void decision(String name,TuningPolicy.Decision d)throws IOException {decisions.write(csv(name)+","+d.verdict()+","+d.gainMs()+","+d.driftMs()+","+d.thresholdMs()+","+csv(d.reason())+"\n");decisions.flush();}
    void note(String name,TuningPolicy.Verdict verdict,String reason)throws IOException {decision(name,new TuningPolicy.Decision(verdict,Double.NaN,Double.NaN,Double.NaN,reason));}
    void pins(String prefix,Map<String,String> pins)throws IOException {latestPins=Map.copyOf(pins);pins.forEach((k,v)->metadata.setProperty(prefix+"."+k,v));save();}
    void decision(String name,BenchmarkAnalysis.Decision d)throws IOException {
        resultDecisions.add(d);decision(name,TuningPolicy.facade(d));
        metadata.setProperty("decision."+name+".availability",d.availability().name());
        metadata.setProperty("decision."+name+".activation",d.activation().name());
        metadata.setProperty("decision."+name+".validity",d.validity().name());
        metadata.setProperty("decision."+name+".effect",d.verdict().name());
        metadata.setProperty("decision."+name+".uncertainty",d.uncertaintyMethod());
        metadata.setProperty("decision."+name+".policy",d.policy().toString());
        metadata.setProperty("decision."+name+".criterion",d.criterion().toString());
    }
    /** One post-drain file per window keeps raw chronology without quadratic whole-run rewrites. */
    void capture(String trial,int phase,int pose,List<FrameMeasurement.Sample> rows,TuningPolicy.CasePolicy policy,
            BenchmarkAnalysis.Stratum stratum,BenchmarkResult.Stage stage,boolean motion,boolean enabled,
            OptimizationSnapshot flags,long reloadCount,String dimension,Map<String,String> effectiveOptions,Integer simulationDistance,String captureIssue)throws IOException {
        var summary=BenchmarkStatistics.summarize(rows,policy.analysis());
        int order=windowOrder++;String id="window-"+order;
        var profile=new TreeMap<String,String>();
        if(flags!=null)for(var flag:OptimizationId.values())profile.put(flag.key(),Boolean.toString(flags.enabled(flag)));
        var pins=latestPins;
        var size=java.util.regex.Pattern.compile("Dimensions\\[width=([0-9]+), height=([0-9]+)\\]").matcher(pins.getOrDefault("resolution",""));
        Integer width=null,height=null;if(size.matches()){width=Integer.valueOf(size.group(1));height=Integer.valueOf(size.group(2));}
        var temporal=new TreeMap<String,String>();
        temporal.put("policy","native shader animation/frame clocks; native AA; simulation owned by SceneLease");
        if(!"private-user-scene".equals(metadata.getProperty("scene_version"))&&pose>=0&&pose<BenchmarkWorld.poseCount()){
            var authored=BenchmarkScenario.authoredEnvironment(pose);temporal.put("authored_pose",BenchmarkWorld.poseName(pose));
            temporal.put("authored_day_time",Long.toString(authored.dayTime()));temporal.put("authored_rain",Boolean.toString(authored.weather()==BenchmarkScenario.Weather.RAIN));
        }
        var scenario=new BenchmarkResult.Scenario("TUNER-"+BenchmarkScenario.VERSION,metadata.getProperty("scene_version","unavailable"),
            motion?BenchmarkScenario.ROUTE_VERSION+"@80ticks":"fixed-pose",stratum.pins().get("case"),dimension,temporal);
        var config=new BenchmarkResult.Configuration(pins.getOrDefault("source_path","unavailable"),pins.getOrDefault("source_sha256","unavailable"),
            effectiveOptions,
            Map.of("selected",metadata.getProperty("initial.selected_resource_ids","unavailable")),"native; see effective options",width,height,
            integer(pins.get("render_distance")),simulationDistance,"UNTESTED: DH activation/readiness not observed","within-session warmed; cache reset unproved");
        var clocks=List.of(new BenchmarkResult.Clock("total-frame","interval between actual render starts","total frame wall","System.nanoTime",
            summary.count()>0?BenchmarkAnalysis.Availability.AVAILABLE:BenchmarkAnalysis.Availability.UNAVAILABLE,null,"every frame",
            "capture paused; asynchronous tail excluded",rows.size(),summary.count(),summary.missingCount(),summary.invalidCount(),List.of("GameRenderer render-start intervals"),summary.reasons()));
        var warmup=new BenchmarkResult.Warmup(null,null,List.of("bounded vanilla pose/mesh acknowledgment and native-history dwell"),
            List.of("DH queues/draw geometry, pipeline-history convergence, requested-work execution not joined"),List.of(),List.of());
        var reasons=new ArrayList<>(List.of("Exploratory capture: readiness/work activation/quality unqualified; no independent JVM repeats",
            "Short p99 is descriptive only; no tail inference",policy.calibrationScope()));
        if(!captureIssue.isBlank())reasons.add(captureIssue);
        var arm=phase==2?BenchmarkResult.Arm.RETURN:!enabled?BenchmarkResult.Arm.MASTER_OFF:
            flags==null||flags.equals(OptimizationSnapshot.noneRequested())?BenchmarkResult.Arm.MASTER_ON_NO_REQUESTS:BenchmarkResult.Arm.FEATURE;
        var window=new BenchmarkResult.Window(BenchmarkResult.SCHEMA_VERSION,id,trial,sessionId,order,arm,profile,null,null,
            reloadCount<=Integer.MAX_VALUE?(int)reloadCount:null,BenchmarkResult.Temperature.WARM,stage,
            motion?BenchmarkResult.Motion.MOTION:BenchmarkResult.Motion.STILL,scenario,config,"PERFORMANCE",
            Map.of("calibration","UNTESTED"),clocks,warmup,stratum.pins(),
            List.of(new BenchmarkResult.Work("requested-feature-work","unavailable",null,null,null,BenchmarkAnalysis.Availability.UNTESTED,
                "Requests/compiled declarations do not establish executed work",List.of(),List.of("No runtime work observer joined"))),
            rows.isEmpty()?null:rows.getFirst().frame(),rows.isEmpty()?null:rows.getLast().frame(),
            rows.stream().map(BenchmarkResult.Sample::from).toList(),summary,
            summary.count()>0?BenchmarkAnalysis.Availability.AVAILABLE:BenchmarkAnalysis.Availability.UNAVAILABLE,
            BenchmarkAnalysis.Activation.UNKNOWN,captureIssue.isBlank()&&summary.chronologyValid()&&summary.invalidCount()==0?BenchmarkAnalysis.Validity.VALID:BenchmarkAnalysis.Validity.INVALID,reasons);
        String link=id+".json";
        BenchmarkResultWriter.write(directory.resolve(link),manifest(List.of(window),BenchmarkResult.RecoveryStatus.PENDING,false));
        windowLinks.add(link);
        // Raw samples live in the linked per-window file; the final manifest retains summaries/index.
        resultWindows.add(new BenchmarkResult.Window(window.schemaVersion(),window.id(),window.comparisonId(),window.repeatId(),window.order(),window.arm(),
            window.featureProfile(),window.sourceGeneration(),window.pipelineGeneration(),window.reloadCount(),window.temperature(),window.stage(),window.motion(),
            window.scenario(),window.configuration(),window.observerMode(),window.observerCost(),window.clocks(),window.warmup(),window.hostObservations(),window.work(),
            window.firstRawFrameId(),window.lastRawFrameId(),List.of(),window.statistics(),window.availability(),window.activation(),window.validity(),window.reasons()));
    }
    private static Integer integer(String value){try{return value==null?null:Integer.valueOf(value);}catch(NumberFormatException ignored){return null;}}
    private BenchmarkResult.Manifest manifest(List<BenchmarkResult.Window> windows,BenchmarkResult.RecoveryStatus recovery,boolean released){
        var artifacts=new TreeMap<String,String>();
        for(var key:metadata.stringPropertyNames())if(key.startsWith("mod_artifact"))artifacts.put(key,metadata.getProperty(key));
        var environment=new BenchmarkResult.Environment(metadata.getProperty("loaded_mods","unavailable"),artifacts,
            metadata.getProperty("gpu_renderer","unavailable"),metadata.getProperty("os","unavailable"),metadata.getProperty("gl_version","unavailable"),metadata.getProperty("java","unavailable"));
        return new BenchmarkResult.Manifest(BenchmarkResult.SCHEMA_VERSION,BenchmarkAnalysis.ANALYSIS_VERSION,directory.getFileName().toString(),metadata.getProperty("started_at"),
            environment,windows,resultDecisions,new BenchmarkResult.Evidence(
                qualificationEvidence.has("still-quality")&&qualificationEvidence.has("motion-quality")?BenchmarkResult.EvidenceVerdict.PASS:BenchmarkResult.EvidenceVerdict.UNTESTED,
                qualificationEvidence.has("DH-PBR-compatibility")?BenchmarkResult.EvidenceVerdict.PASS:BenchmarkResult.EvidenceVerdict.UNTESTED,
                qualificationEvidence.references().getOrDefault("still-quality",List.of()),qualificationEvidence.references().getOrDefault("motion-quality",List.of()),
                qualificationEvidence.references().getOrDefault("DH-PBR-compatibility",List.of()),List.of("Native AA retained; scoped external evidence is required for quality/compatibility")),
            new BenchmarkResult.Recovery(recovery,activeFile.toString(),released,List.of(metadata.getProperty("recovery_failure","See durable Undo journal"))),
            new BenchmarkResult.SearchCoverage(Integer.parseInt(metadata.getProperty("attempted_candidates","0")),Integer.parseInt(metadata.getProperty("retry_count","0")),
                confirmation==null?List.of():confirmation.searchedCases(),confirmation==null?List.of():List.of(confirmation.heldOutCase()),
                confirmation!=null&&confirmation.frozen(),List.of("holdout_consumed="+(confirmation!=null&&confirmation.consumed()),"holdout_timing_passed="+(confirmation!=null&&confirmation.passed()),
                    "one live JVM session; blocks/repeated brackets are not independent JVM repeats")),windowLinks,
            List.of("Raw chronology is in linked per-window JSON and legacy samples.csv; index manifest omits duplicate raw rows",
                "No case pooling; all required final cases must pass separately; no headline aggregate",
                "Qualification references, when supplied, are externally asserted for the exact frozen scope; unobserved fields remain unavailable"));
    }
    private void result(BenchmarkResult.RecoveryStatus recovery,boolean released)throws IOException {
        BenchmarkResultWriter.write(directory.resolve("benchmark-result.json"),manifest(resultWindows,recovery,released));
    }
    void save()throws IOException {atomic(directory.resolve("metadata.properties"),metadata);}
    void finish(String state,OptimizationSnapshot profile)throws IOException {
        metadata.setProperty("finished_at",Instant.now().toString());metadata.setProperty("result",state);
        for(var id:OptimizationId.values())metadata.setProperty("final_profile."+id.key(),Boolean.toString(profile.enabled(id)));save();
        result(BenchmarkResult.RecoveryStatus.RESTORED,true);
        var p=properties(activeFile);p.setProperty("state",state);atomic(activeFile,p);raw.close();decisions.close();
    }
    void parkRecovery(String reason,int attempts)throws IOException {
        metadata.setProperty("result","RESTORATION_PENDING");metadata.setProperty("recovery_attempts",Integer.toString(attempts));metadata.setProperty("recovery_failure",reason);save();
        var pending=properties(activeFile);pending.setProperty("state","PENDING");pending.setProperty("recovery_failure",reason);atomic(activeFile,pending);
        result(BenchmarkResult.RecoveryStatus.FAILED,false);raw.close();decisions.close();
    }
    static String csv(String s){return "\""+s.replace("\"","\"\"").replace('\n',' ')+"\"";}
    private static String value(Long n){return n==null?"":n.toString();}
}
