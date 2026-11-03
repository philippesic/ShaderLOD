package com.pp.shaderlod.optimization;
import com.pp.shaderlod.measurement.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
/** Runs without a client; real persistence and pure clock/query ownership boundaries. */
public final class SharedInfrastructureChecksMain {
    private static int checks;
    private static void check(boolean test,String description) {checks++;if(!test)throw new AssertionError(description);}
    private static Map<String,String> readOptions(String resource) throws Exception {
        try(var in=SharedInfrastructureChecksMain.class.getResourceAsStream(resource)) {
            if(in==null)throw new IllegalStateException(resource);
            var m=new TreeMap<String,String>();for(var e:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());return Map.copyOf(m);
        }
    }
    private static Map<String,Map<String,String>> readOptionMaps(String resource) throws Exception {
        try(var in=SharedInfrastructureChecksMain.class.getResourceAsStream(resource)) {
            if(in==null)throw new IllegalStateException(resource);
            var maps=new TreeMap<String,Map<String,String>>();for(var family:com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().entrySet()) {
                var m=new TreeMap<String,String>();for(var e:family.getValue().getAsJsonObject().entrySet())m.put(e.getKey(),e.getValue().getAsString());maps.put(family.getKey(),Map.copyOf(m));
            }return Map.copyOf(maps);
        }
    }
    private static class Gpu implements FrameMeasurement.GpuBackend {
        int active,created,deleted,reads,calls,bits=64;boolean ready,busy;
        public String status(){calls++;return bits==0?"unsupported":"test backend";}
        public int counterBits(){calls++;return bits;}
        public int create(){calls++;return ++created;}
        public boolean busy(){calls++;return busy||active!=0;}
        public void begin(int query){calls++;check(active==0,"no overlapping query");active=query;}
        public void end(){calls++;check(active!=0,"only owner ends query");active=0;}
        public boolean available(int query){calls++;return ready;}
        public long nanoseconds(int query){calls++;reads++;return 1234;}
        public void delete(int query){calls++;deleted++;}
    }
    public static void main(String[] args) throws Exception {
        var previous=OptimizationConfig.state();
        try {OptimizationConfig.restoreState(new OptimizationConfig.State(OptimizationSnapshot.defaults(),null,false,false));runChecks();}finally {OptimizationConfig.restoreState(previous);}
    }
    private static void runChecks() throws Exception {
        // Every declared private control reaches the real recipe admission parser, including Off.
        for(var family:CompileOptionControl.Family.values()) {
            try(var input=CompileOptionControl.class.getResourceAsStream("/assets/shaderlod/adapters/"+family.recipe)) {
                check(input!=null,"packaged compile recipe exists: "+family.id);
                var recipe=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                check(CompileOptionControl.recipeKeys(family,recipe).equals(family.keys),"recipe admits every declared private control: "+family.id);
                if(family==CompileOptionControl.Family.SILDUR) {
                    recipe.getAsJsonObject("optimization_compile_controls").remove("spatial_lighting_reuse");
                    check(!CompileOptionControl.recipeKeys(family,recipe).contains(CompileOptionControl.SILDUR_SPATIAL_REUSE),"missing real spatial metadata reproduces r18 admission defect");
                }
            }
        }
        // Pipeline hooks see every family, including native packs before any private options exist.
        var spatialProfiles=new LinkedHashMap<String,java.util.function.Predicate<Map<String,String>>>();
        spatialProfiles.put("makeup-ultra-fast-shaders",com.pp.shaderlod.optimization.makeupspatial.MakeUpSpatialContract::profile);
        spatialProfiles.put("bsl-shaders",com.pp.shaderlod.optimization.bslspatial.BslSpatialRuntime::profile);
        spatialProfiles.put("astralex",com.pp.shaderlod.optimization.astraspatial.AstraSpatialRuntime::profile);
        spatialProfiles.put("complementary-reimagined",com.pp.shaderlod.optimization.reimaginedspatial.ReimaginedSpatialRuntime::profile);
        spatialProfiles.put("complementary-unbound",com.pp.shaderlod.optimization.unboundspatial.UnboundSpatialRuntime::profile);
        spatialProfiles.put("sildurs-vibrant-shaders",com.pp.shaderlod.optimization.sildurspatial.SildurSpatialContract::options);
        spatialProfiles.put("solas-shader",com.pp.shaderlod.optimization.solasspatial.SolasSpatialRuntime::profile);
        var generated=readOptionMaps("/assets/shaderlod/rendererquad/generated-main-options.json");
        // Graph0 deliberately omits Astra's world consumer; its active profile uses the declared graph1 option.
        check(!spatialProfiles.get("astralex").test(generated.get("astralex")),"Astra generated worldgraph0 keeps spatial consumer absent");
        var spatialInputs=new LinkedHashMap<String,Map<String,String>>(generated);
        var activeAstra=new TreeMap<>(generated.get("astralex"));
        activeAstra.put(CompileOptionControl.ASTRALEX_WORLD_GRAPH,"1");
        spatialInputs.put("astralex",Map.copyOf(activeAstra));
        var nativeResources=Map.of("astralex","astraquad/native1015.json","bliss-shader","blissquad/native463.json","bsl-shaders","bslquad/native384.json","complementary-reimagined","reimaginedquad/native398.json","complementary-unbound","unboundquad/native398.json","makeup-ultra-fast-shaders","makeupquad/native100.json","photon","photonquad/native366.json","sildurs-vibrant-shaders","sildurquad/native115.json","solas-shader","solasquad/native428.json");
        for(var consumer:spatialProfiles.entrySet()) {
            check(!consumer.getValue().test(null)&&!consumer.getValue().test(Map.of()),"absent profile refuses without exception: "+consumer.getKey());
            for(var input:spatialInputs.entrySet())check(consumer.getValue().test(input.getValue())==consumer.getKey().equals(input.getKey()),"real generated family isolation: "+consumer.getKey()+" <- "+input.getKey());
            for(var input:nativeResources.entrySet())check(!consumer.getValue().test(readOptions("/assets/shaderlod/"+input.getValue())),"real native family keeps private paths absent: "+consumer.getKey()+" <- "+input.getKey());
            var missing=new TreeMap<>(spatialInputs.get(consumer.getKey()));String privateKey=missing.keySet().stream().filter(k->k.startsWith("SHADERLOD_")).findFirst().orElseThrow();missing.remove(privateKey);missing.put("FOREIGN_PRIVATE_KEY","0");
            check(!consumer.getValue().test(missing),"same-size malformed profile refuses: "+consumer.getKey());
        }
        var selector=com.pp.shaderlod.packs.PackSelectionService.class.getDeclaredMethod("nativeRendererGraph",String.class,com.pp.shaderlod.LodPreset.class,boolean.class);selector.setAccessible(true);
        check(!(boolean)selector.invoke(null,"bsl-shaders",com.pp.shaderlod.LodPreset.QUALITY,true),"Quality original graph independent of loaded state");
        var directory=Files.createTempDirectory("shaderlod-shared-check-");var config=directory.resolve("flags.properties");
        check(!OptimizationConfig.loaded(),"preload uses legacy route");
        check((boolean)selector.invoke(null,"bsl-shaders",com.pp.shaderlod.LodPreset.PERFORMANCE,true),"legacy Performance renderer route retained before controls load");
        OptimizationConfig.load(config);check(OptimizationConfig.loaded(),"successful flags load initialized controls");
        check(!(boolean)selector.invoke(null,"bsl-shaders",com.pp.shaderlod.LodPreset.PERFORMANCE,true),"loaded independent controls retain generated graph for isolated toggles");
        var original=OptimizationConfig.snapshot();
        var next=original.with(OptimizationId.CLOUD_PRIMARY_DETAIL,false);
        check(original.enabled(OptimizationId.CLOUD_PRIMARY_DETAIL),"immutable snapshot");
        OptimizationConfig.replace(next);OptimizationConfig.load(config);
        check(OptimizationConfig.snapshot().equals(next),"persist actual IDs");
        var failed=OptimizationEditTransaction.apply(original,()->{throw new IllegalStateException("authentic reload refusal");},()->false);
        check(!failed.success()&&!failed.recoveryComplete()&&OptimizationConfig.snapshot().equals(next),"failed recovery reload cannot certify safe candidate continuation");
        check(Files.exists(config.resolveSibling("flags.properties.bak")),"backup before first personal edit");
        Files.writeString(config,"quad_index_fill=true\nquad_submission_copy=true\nquad_command_compaction=false\ncommand_metadata_cache=true\nshadow_admission_cache=false\n");OptimizationConfig.load(config);
        check(!OptimizationConfig.requested(OptimizationId.QUAD_INDEX_FILL)&&!OptimizationConfig.requested(OptimizationId.QUAD_SUBMISSION_COPY),"canonical key wins conflicting transform aliases");
        check(!OptimizationConfig.requested(OptimizationId.COMMAND_METADATA_CACHE),"canonical shadow cache key wins legacy value");
        check(OptimizationId.fromKey("quad_index_fill")==OptimizationId.QUAD_COMMAND_COMPACTION,"legacy Java/persistence identity maps to canonical one item");
        Files.writeString(config,"cloud_primary_detail=not_boolean\n");
        try {OptimizationConfig.load(config);throw new AssertionError("malformed config accepted");}catch(java.io.IOException expected) {}
        check(!OptimizationConfig.loaded()&&OptimizationConfig.snapshot().flags().values().stream().noneMatch(Boolean::booleanValue),"invalid flag config fails closed without partial flags");
        OptimizationConfig.load(directory.resolve("recovered.properties"));OptimizationConfig.replace(next);
        var cancel=OptimizationConfig.snapshot().with(OptimizationId.CLOUD_PRIMARY_BUDGET,false);
        check(!cancel.equals(OptimizationConfig.snapshot()),"staged cancel leaves published flags");
        check(OptimizationSnapshot.allRequested().flags().values().stream().allMatch(Boolean::booleanValue),"all-applicable request includes every ID");
        // Authentic persisted profiles must isolate two actual supported families across reload/recovery.
        var flat=OptimizationConfig.snapshot();OptimizationConfig.enableShaderProfiles();
        OptimizationConfig.selectFamily("photon");OptimizationConfig.replace(flat.with(OptimizationId.EARLY_ALPHA,true));
        OptimizationConfig.selectFamily("bsl-shaders");
        check(OptimizationConfig.snapshot().equals(flat),"new family inherits legacy requests, not previous family edits");
        OptimizationConfig.replace(flat.with(OptimizationId.DEPTH_PRIMING,true));
        OptimizationConfig.load(directory.resolve("recovered.properties"));OptimizationConfig.selectFamily("photon");
        check(OptimizationConfig.requested(OptimizationId.EARLY_ALPHA)&&!OptimizationConfig.requested(OptimizationId.DEPTH_PRIMING),"Photon persists independently across load");
        var photon=OptimizationConfig.snapshot();
        var profileFailure=OptimizationEditTransaction.apply(photon.with(OptimizationId.EARLY_ALPHA,false),()->{throw new IllegalStateException("reload refusal");},()->false);
        check(!profileFailure.success()&&OptimizationConfig.snapshot().equals(photon),"failed profile Apply restores its complete previous snapshot");
        OptimizationConfig.selectFamily("bsl-shaders");var bsl=OptimizationConfig.snapshot();
        OptimizationConfig.restoreFamily("photon",photon.with(OptimizationId.EARLY_ALPHA,false));
        check(OptimizationConfig.snapshot().equals(bsl)&&OptimizationConfig.requested(OptimizationId.DEPTH_PRIMING),"external family restoration cannot overwrite currently selected family");
        OptimizationConfig.selectFamily("photon");check(!OptimizationConfig.requested(OptimizationId.EARLY_ALPHA),"inactive original family restoration persisted");
        OptimizationConfig.selectFamily("photon");var originalPhoton=OptimizationConfig.snapshot();
        var scopedPair=PairedMeasurement.prepare(OptimizationId.EARLY_ALPHA,1,2,()->Map.of("scene","fixed"),flags->{
            try{OptimizationConfig.restoreFamily("photon",flags);return true;}catch(java.io.IOException failure){throw new RuntimeException(failure);}
        });
        scopedPair.start();OptimizationConfig.selectFamily("bsl-shaders");var selectedBsl=OptimizationConfig.snapshot();scopedPair.cancel();
        check(!scopedPair.restorationPending()&&OptimizationConfig.snapshot().equals(selectedBsl)&&OptimizationConfig.snapshot("photon").equals(originalPhoton),"paired cancellation restores original family without changing selected family");
        Files.writeString(config,"shader.photon.early_alpha=not_boolean\n");
        try{OptimizationConfig.load(config);throw new AssertionError("invalid scoped flag accepted");}catch(java.io.IOException expected){}
        OptimizationConfig.selectFamily("photon");
        check(!OptimizationConfig.loaded()&&OptimizationConfig.snapshot().flags().values().stream().noneMatch(Boolean::booleanValue),"failed scoped load stays closed when selecting family");
        // Return to the legacy mode used by the pre-existing pure caller checks.
        OptimizationConfig.restoreState(new OptimizationConfig.State(flat,null,false,true));
        var names=new HashSet<String>();for(var id:OptimizationId.values())check(names.add(id.uniform())&&OptimizationId.fromKey(id.key())==id,"stable unique IDs");
        var core=new com.pp.shaderlod.CoreUniformFrame.Core(new com.pp.shaderlod.LodSettings(true,32,96,8,false),com.pp.shaderlod.LodPreset.PERFORMANCE);
        var all=OptimizationSnapshot.allRequested();var qualified=new CompileOptionControl.Facts(true,true,true,true,false,false);
        check(CompileOptionControl.desired(core,all,qualified).equals("1"),"real compile ON option literal");
        check(CompileOptionControl.desired(core,all,new CompileOptionControl.Facts(true,true,false,true,false,false)).equals("0"),"unsupported dimension compiles native0");
        check(CompileOptionControl.desired(core,all,new CompileOptionControl.Facts(true,true,true,true,true,false)).equals("0"),"DH presence compiles native0");
        check(CompileOptionControl.desired(core,all,new CompileOptionControl.Facts(false,true,true,true,false,false)).equals("0"),"unqualified source compiles native0");
        check(CompileOptionControl.desired(new com.pp.shaderlod.CoreUniformFrame.Core(core.settings(),com.pp.shaderlod.LodPreset.QUALITY),all,qualified).equals("0"),"Quality native viewport");
        var compile=CompileOptionControl.desiredCompileOptions(core,all,qualified,true,true,false,false);
        check(compile.get(CompileOptionControl.BSL_SHADOW_FIELD).equals("0")&&compile.get(CompileOptionControl.BSL_RAW_QUERY).equals("0"),"switch does not fabricate producer admission");
        compile=CompileOptionControl.desiredCompileOptions(core,all,qualified,true,true,true,true);
        check(compile.get(CompileOptionControl.BSL_SHADOW_FIELD).equals("1")&&compile.get(CompileOptionControl.BSL_RAW_QUERY).equals("1"),"raw/minimum coexistent binding needs actual admissions");
        check(CompileOptionControl.overworld(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft","overworld"))&&!CompileOptionControl.overworld(new net.irisshaders.iris.shaderpack.materialmap.NamespacedId("minecraft","the_nether")),"actual pinned dimension caller type");
        var reloads=new java.util.concurrent.atomic.AtomicInteger();
        var refused=OptimizationEditTransaction.apply(original,()->reloads.incrementAndGet(),()->false,flags->{throw new IllegalStateException("preflight refused");});
        check(!refused.success()&&reloads.get()==0,"refusal before mutation does not reload");
        var transitions=new ArrayList<String>();
        var old=OptimizationConfig.snapshot();var applied=old.with(OptimizationId.AURORA_BUDGET,true);
        var session=new CompileOptionControl.Session() {
            public boolean prepare(){check(OptimizationConfig.snapshot().equals(applied),"producer sees authentic requested snapshot");transitions.add("prepare");return true;}
            public void reload(OptimizationEditTransaction.Reload reload) throws Exception {transitions.add("reload");reload.run();}
            public void restore(){check(OptimizationConfig.snapshot().equals(old),"flags restored before consumer recovery");transitions.add("restore");}
            public void recoveryReload(OptimizationEditTransaction.Reload reload){transitions.add("recovery");}
            public void finish(){transitions.add("finish");}
        };
        var outcome=OptimizationEditTransaction.apply(applied,()->{throw new IllegalStateException("driver rejected");},()->false,flags->session);
        check(!outcome.success()&&outcome.recoveryComplete()&&transitions.equals(List.of("prepare","reload","restore","recovery","finish")),"complete producer consumer recovery order certifies recovered candidate refusal");
        check(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended(),"failed apply releases lifecycle suspension");
        var cleanupBackend=new Gpu() {public void end(){super.end();throw new IllegalStateException("ownership error");}};
        var cleanupClock=new AtomicLong(1);var cleanupTimer=new FrameMeasurement(cleanupBackend,cleanupClock::get,()->-1,()->-1);
        cleanupTimer.enabled(true);cleanupTimer.begin();try {cleanupTimer.invalidate();throw new AssertionError("cleanup error not surfaced");}catch(IllegalStateException expected) {}
        check(cleanupBackend.deleted==1&&cleanupBackend.active==0&&cleanupTimer.pendingQueries()==0,"query cleanup continues after backend end failure");
        var gpu=new Gpu();var wall=new AtomicLong(100);var cpu=new AtomicLong(50);var process=new AtomicLong(90);
        var timer=new FrameMeasurement(gpu,wall::get,cpu::get,process::get);
        timer.begin();timer.end();check(gpu.calls==0,"disabled zero GPU calls");
        timer.enabled(true);timer.begin();wall.set(200);cpu.set(60);process.set(110);timer.end();
        check(gpu.reads==0&&timer.pendingQueries()==1,"async results never block");
        gpu.ready=true;timer.poll();var s=timer.drain().getFirst();
        check(s.gpuNs()==1234&&s.renderWallNs()==100&&s.renderCpuNs()==10&&s.processCpuNs()==20&&s.frameWallNs()==null,"independent clocks and first interval invalid");
        timer.begin();wall.set(300);timer.end();s=timer.drain().getFirst();check(s.frameWallNs()==100&&s.fps()==10_000_000,"frame interval FPS");
        gpu.busy=true;timer.begin();timer.end();s=timer.drain().getFirst();check(s.gpuNs()==null&&s.gpuStatus().contains("another elapsed query"),"foreign query not overlapped");
        gpu.busy=false;gpu.ready=false;timer.begin();timer.invalidate();check(gpu.active==0&&timer.pendingQueries()==0,"reload ends owner and deletes pending queries");
        gpu.bits=0;timer.begin();timer.end();check(timer.drain().getFirst().gpuNs()==null,"unsupported never fabricates GPU");timer.enabled(false);
        gpu.bits=64;gpu.ready=false;timer.enabled(true);
        for(int i=0;i<10;i++){wall.incrementAndGet();timer.begin();timer.end();}
        check(timer.pendingQueries()==8,"query ring is bounded");
        check(timer.drain().stream().allMatch(row->row.gpuNs()==null&&row.gpuStatus().contains("ring full")),"ring pressure explicit unavailable");
        wall.addAndGet(6_000_000_000L);timer.poll();check(timer.pendingQueries()==0&&timer.drain().size()==8,"finite query timeout retains raw invalid samples");timer.enabled(false);
        var thread=java.lang.management.ManagementFactory.getThreadMXBean();
        var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        var unsupported=new Gpu();unsupported.bits=0;
        var actual=new FrameMeasurement(unsupported,System::nanoTime,
                ()->thread.isCurrentThreadCpuTimeSupported()&&thread.isThreadCpuTimeEnabled()?thread.getCurrentThreadCpuTime():-1,
                ()->os instanceof com.sun.management.OperatingSystemMXBean bean?bean.getProcessCpuTime():-1);
        actual.enabled(true);actual.begin();long checksum=1;
        for(int i=0;i<200_000;i++)checksum=checksum*1664525+1013904223;
        actual.end();var actualSample=actual.drain().getFirst();
        check(checksum!=0&&actualSample.renderWallNs()>0,"actual System.nanoTime caller");
        check(actualSample.renderCpuNs()==null||actualSample.renderCpuNs()>=0,"actual supported ThreadMXBean caller");
        check(actualSample.processCpuNs()==null||actualSample.processCpuNs()>=0,"actual process CPU caller");
        check(actualSample.gpuNs()==null,"real clocks do not infer GPU");actual.enabled(false);
        OptimizationConfig.replace(original);var pins=new HashMap<String,String>();pins.put("scene","fixed");
        PairedMeasurement.Apply apply=flags->{try{OptimizationConfig.replace(flags);return true;}catch(Exception e){throw new RuntimeException(e);}};
        var pair=new PairedMeasurement(OptimizationId.CLOUD_PRIMARY_DETAIL,1,2,()->Map.copyOf(pins),apply);
        for(int phase=0;phase<3;phase++) {
            boolean enabled=OptimizationConfig.snapshot().enabled(OptimizationId.CLOUD_PRIMARY_DETAIL);
            for(int i=0;i<3;i++)pair.accept(new FrameMeasurement.Sample(i,phase,enabled?10_000_000L:20_000_000L,1_000_000L,500_000L,700_000L,null,"unsupported"));
            check(pair.readyToAdvance(),"finite phase denominator");pair.advance();
        }
        check(pair.phase()==PairedMeasurement.Phase.COMPLETE&&pair.rows().size()==6,"OFF ON RETURN rows");
        check(OptimizationConfig.snapshot().equals(original),"bracket restores entire snapshot");
        check(pair.summary().get("frame_wall_delta_ms")==-10&&pair.summary().get("frame_wall_baseline_drift_ms")==0&&!pair.summary().containsKey("gpu_delta_ms"),"delta drift unavailable GPU");
        var exported=MeasurementExport.write(directory,pair,List.of(),pair.status());check(Files.readString(exported.resolve("samples.csv")).lines().count()==7,"raw CSV denominator");
        pair=new PairedMeasurement(OptimizationId.CLOUD_PRIMARY_DETAIL,1,2,()->Map.copyOf(pins),apply);pins.put("scene","moved");pair.validate();
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&OptimizationConfig.snapshot().equals(original),"pin drift fails and restores");
        var observations=new java.util.concurrent.atomic.AtomicInteger();
        pair=new PairedMeasurement(OptimizationId.CLOUD_PRIMARY_DETAIL,1,2,()->{
            if(observations.incrementAndGet()>1)throw new IllegalStateException("source unreadable");return Map.of("scene","fixed");
        },apply);
        check(pair.phase()==PairedMeasurement.Phase.FAILED&&OptimizationConfig.snapshot().equals(original),"pin-read exception after apply restores");
        System.out.println("PASS "+checks+" checks; GPU behavior here uses fake backend, actual API compiled separately");
    }
}
