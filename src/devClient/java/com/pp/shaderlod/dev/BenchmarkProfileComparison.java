package com.pp.shaderlod.dev;

import com.pp.shaderlod.*;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.integration.PackSourceReader;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.optimization.*;
import com.pp.shaderlod.packs.PackSelectionService;
import com.pp.shaderlod.tuning.AutoTuner;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Bounded development caller of normal mod clocks: global Off / exact saved result / Off return. */
final class BenchmarkProfileComparison {
    private static Job job;
    private static String last="Idle";
    private static Path output;
    static boolean active(){return job!=null;}
    static String status(){return job==null?last:job.status();}
    static Path output(){return output;}
    static void start() throws Exception {
        if(job!=null||AutoTuner.active()||RuntimeMeasurement.requested()||RuntimeMeasurement.pair()!=null&&RuntimeMeasurement.pair().restorationPending())
            throw new IllegalStateException("Another measurement or restoration owns the client");
        if(!BenchmarkWorld.ready())throw new IllegalStateException("A ready dedicated benchmark scene is required");
        job=new Job();try{job.start();}catch(Exception failure){job.finish(failure.toString());throw failure;}
    }
    static void tick(){
        if(job==null)return;
        try{job.tick();}catch(Exception failure){job.finish(failure.toString());}
    }
    static void cancel(){if(job!=null)job.finish("Cancelled");}
    private static String selected(){return Iris.getIrisConfig().getShaderPackName().orElseThrow();}
    private static String csv(String s){return '"'+s.replace("\"","\"\"").replace('\n',' ')+'"';}
    private static String value(Long n){return n==null?"":n.toString();}
    private static final class Job {
        final Minecraft client=Minecraft.getInstance();
        final Object world=client.level;
        final long started=System.nanoTime();
        final boolean enabled=ShaderLodConfig.ENABLED.get(),vsync=client.options.enableVsync().get();
        final LodPreset preset=ShaderLodConfig.PRESET.get();
        final OptimizationSnapshot flags=OptimizationConfig.snapshot();
        final String family=OptimizationConfig.activeFamily();
        final int fps=client.options.framerateLimit().get();
        final int windowFps=client.getWindow().getFramerateLimit();
        final int[] poses={0,5,7};
        final String[] phases={"OFF","TUNED","OFF_RETURN"};
        final Properties metadata=new Properties();
        final List<FrameMeasurement.Sample> rows=new ArrayList<>(),warmRows=new ArrayList<>(),tail=new ArrayList<>();
        PackSelectionService.SelectionIdentity source;
        String originalDigest,expectedScene;
        Object expectedPack;
        SceneLease lease;
        Path directory;
        BufferedWriter raw;
        int phase,pose,warm;
        long settleAt,windowAt,drainAt,sampleAt;
        boolean settling,draining,finished,clockOwned;
        double x,y,z;float yaw,pitch,rain,thunder;
        long dayTime;int clearWeatherTime,rainTime,thunderTime;boolean raining,thundering;
        String status(){return phases[phase]+" pose="+poses[pose]+" warm="+warm+" samples="+rows.size()+" elapsed="+(System.nanoTime()-started)/1_000_000_000L+"s; "+directory;}
        String settings(){return "enabled="+ShaderLodConfig.ENABLED.get()+";preset="+ShaderLodConfig.PRESET.get()+";requests="+OptimizationConfig.snapshot().flags();}
        void start() throws Exception {
            if(!client.isWindowActive()||client.screen!=null)throw new IllegalStateException("Focused game with no GUI is required");
            source=PackSelectionService.planSelection(Iris.getShaderpacksDirectory(),selected(),false,LodPreset.QUALITY);
            if(source==null)throw new IllegalStateException("No verified source pair");
            originalDigest=PackSourceReader.read(Iris.getShaderpacksDirectory().resolve(source.originalFile())).sourceSha256();
            var parent=Iris.getShaderpacksDirectory().resolveSibling("shaderlod-profile-comparisons");Files.createDirectories(parent);
            directory=Files.createTempDirectory(parent,"compare-"+Instant.now().toString().replace(':','-')+"-");output=directory;
            metadata.setProperty("started_at",Instant.now().toString());metadata.setProperty("result","RUNNING");
            metadata.setProperty("saved_core_enabled",Boolean.toString(enabled));metadata.setProperty("saved_preset",preset.name());
            metadata.setProperty("saved_requests",flags.flags().toString());metadata.setProperty("saved_vsync",Boolean.toString(vsync));metadata.setProperty("saved_frame_limit",Integer.toString(fps));
            metadata.setProperty("saved_window_frame_limit",Integer.toString(windowFps));
            metadata.setProperty("interpretation",enabled?"TUNED is the exact saved enabled profile":"TUNED is global Off because the successful tune saved native Off; no forced enabled result");
            metadata.setProperty("scope","ShaderLOD global Off comparison; mod remains installed in all phases");
            metadata.setProperty("scene_version",BenchmarkWorld.version());metadata.setProperty("warmup_frames","120");metadata.setProperty("samples_per_window","600");
            metadata.setProperty("window_policy","warmup >=4 seconds and120frames; measurement >=8seconds and600frames");
            for(int p:poses)metadata.setProperty("pose."+p,BenchmarkWorld.poseName(p));
            var camera=client.getCameraEntity();x=camera.getX();y=camera.getY();z=camera.getZ();yaw=camera.getYRot();pitch=camera.getXRot();
            rain=client.level.getRainLevel(1);thunder=client.level.getThunderLevel(1);
            lease=SceneLease.prepare();lease.freeze();
            client.getSingleplayerServer().submit(()->{var level=client.getSingleplayerServer().overworld();var data=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();dayTime=level.getDayTime();clearWeatherTime=data.getClearWeatherTime();rainTime=data.getRainTime();thunderTime=data.getThunderTime();raining=data.isRaining();thundering=data.isThundering();}).get(2,TimeUnit.SECONDS);
            TimingVideoSettings.apply(260,false);
            RuntimeMeasurement.request(true);clockOwned=true;RuntimeMeasurement.pauseWindow();
            raw=Files.newBufferedWriter(directory.resolve("samples.csv"));raw.write("phase,window,pose,frame,epoch,frame_wall_ns,render_wall_ns,render_cpu_ns,process_cpu_ns,gpu_ns,gpu_status\n");
            metadata.setProperty("owned_simulation",lease.provenance());apply();setPose();save();
        }
        void requireSource() throws Exception {
            var current=PackSelectionService.planSelection(Iris.getShaderpacksDirectory(),selected(),false,LodPreset.QUALITY);
            if(!source.equals(current)||!originalDigest.equals(PackSourceReader.read(Iris.getShaderpacksDirectory().resolve(source.originalFile())).sourceSha256()))throw new IllegalStateException("Source identity changed; refusing graph mutation");
        }
        void apply() throws Exception {
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();requireSource();long at=System.nanoTime();
            boolean target=phase==1&&enabled;
            if(ShaderLodConfig.ENABLED.get()!=target||ShaderLodConfig.PRESET.get()!=preset)
                if(!ShaderLodSettings.apply(target,preset))throw new IllegalStateException(ShaderLodSettings.failureMessage());
            var result=OptimizationEditTransaction.apply(flags,Iris::reload,Iris::isFallback,CompileOptionControl::open);
            if(!result.success())throw new IllegalStateException(result.message());
            TimingVideoSettings.apply(260,false);
            expectedPack=Iris.getCurrentPack().orElseThrow();metadata.setProperty(phases[phase]+".apply_wall_ns",Long.toString(System.nanoTime()-at));
            MeasurementPins.capture(settings()).forEach((k,v)->metadata.setProperty(phases[phase]+".pins."+k,v));
        }
        void setPose() throws Exception {
            RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();BenchmarkWorld.pose(poses[pose]);
            settleAt=System.nanoTime();settling=true;draining=false;warm=0;sampleAt=0;rows.clear();warmRows.clear();tail.clear();
            metadata.setProperty("current",status());save();
        }
        void tick() throws Exception {
            if(System.nanoTime()-started>600_000_000_000L)throw new IllegalStateException("Comparison exceeded ten-minute deadline");
            if(!client.isWindowActive())throw new IllegalStateException("Comparison lost window focus");
            if(client.screen!=null||client.level!=world||!BenchmarkWorld.ready()||lease==null||!lease.valid())throw new IllegalStateException("Scene ownership changed or GUI opened");
            if(Iris.isFallback()||Iris.getCurrentPack().orElse(null)!=expectedPack||!OptimizationConfig.activeFamily().equals(family))throw new IllegalStateException("Shader/profile changed externally");
            if(ShaderLodConfig.ENABLED.get()!=(phase==1&&enabled)||ShaderLodConfig.PRESET.get()!=preset||!OptimizationConfig.snapshot().equals(flags))throw new IllegalStateException("Requested profile changed externally");
            if(client.options.enableVsync().get()||client.options.framerateLimit().get()!=260||client.getWindow().getFramerateLimit()!=260)throw new IllegalStateException("Video settings changed externally");
            if(settling){
                if(System.nanoTime()-settleAt>20_000_000_000L)throw new IllegalStateException("Pose/mesh settle exceeded twenty seconds");
                if(!BenchmarkWorld.poseReady()||!client.levelRenderer.hasRenderedAllSections())return;
                settling=false;expectedScene=MeasurementPins.scene();windowAt=System.nanoTime();
                metadata.setProperty(prefix()+".settle_wall_ns",Long.toString(windowAt-settleAt));metadata.setProperty(prefix()+".scene",expectedScene);metadata.setProperty(prefix()+".focused","true");RuntimeMeasurement.resumeWindow();return;
            }
            if(!MeasurementPins.scene().equals(expectedScene))throw new IllegalStateException("Fixed camera changed externally");
            RuntimeMeasurement.pollSamples();var samples=RuntimeMeasurement.drain();
            if(draining){tail.addAll(samples);if(RuntimeMeasurement.pendingQueries()==0){completeWindow();return;}if(System.nanoTime()-drainAt>2_000_000_000L)throw new IllegalStateException("GPU tail did not drain");return;}
            for(var sample:samples){
                if(warm<120||System.nanoTime()-windowAt<4_000_000_000L){warm++;warmRows.add(sample);}
                else if(sample.frameWallNs()!=null&&sample.frameWallNs()>0){if(sampleAt==0)sampleAt=System.nanoTime();rows.add(sample);}
                else tail.add(sample);
            }
            if(rows.size()>=600&&sampleAt!=0&&System.nanoTime()-sampleAt>=8_000_000_000L){RuntimeMeasurement.pauseWindow();draining=true;drainAt=System.nanoTime();return;}
            if(System.nanoTime()-windowAt>60_000_000_000L)throw new IllegalStateException("Measurement window exceeded sixty seconds");
        }
        String prefix(){return phases[phase]+".pose-"+poses[pose];}
        void writeRows(String label,List<FrameMeasurement.Sample> samples) throws IOException {
            for(var s:samples)raw.write(phases[phase]+","+label+","+poses[pose]+","+s.frame()+","+s.epoch()+","+value(s.frameWallNs())+","+s.renderWallNs()+","+value(s.renderCpuNs())+","+value(s.processCpuNs())+","+value(s.gpuNs())+","+csv(s.gpuStatus())+"\n");
        }
        void completeWindow() throws Exception {
            writeRows("WARMUP_EXCLUDED",warmRows);writeRows("MEASURED",rows);writeRows("TAIL_EXCLUDED",tail);raw.flush();
            metadata.setProperty(prefix()+".window_wall_ns",Long.toString(System.nanoTime()-windowAt));
            metadata.setProperty(prefix()+".valid_frames",Integer.toString(rows.size()));
            metadata.setProperty(prefix()+".mean_frame_ms",Double.toString(rows.stream().mapToLong(s->s.frameWallNs()).average().orElseThrow()/1e6));
            metadata.setProperty(prefix()+".fps_from_mean_frame",Double.toString(1e9/rows.stream().mapToLong(s->s.frameWallNs()).average().orElseThrow()));save();
            if(++pose<poses.length){setPose();return;}
            pose=0;if(++phase<phases.length){apply();setPose();return;}
            phase=2;finish(null);
        }
        void restore() throws Exception {
            try{
                if(clockOwned){try{if(RuntimeMeasurement.requested())RuntimeMeasurement.pauseWindow();RuntimeMeasurement.invalidate();}finally{RuntimeMeasurement.request(false);clockOwned=false;}}
                if(source!=null){requireSource();if(ShaderLodConfig.ENABLED.get()!=enabled||ShaderLodConfig.PRESET.get()!=preset)if(!ShaderLodSettings.apply(enabled,preset))throw new IllegalStateException(ShaderLodSettings.failureMessage());
                    var result=OptimizationEditTransaction.apply(flags,Iris::reload,Iris::isFallback,CompileOptionControl::open);if(!result.success())throw new IllegalStateException(result.message());}
            } finally {
                TimingVideoSettings.apply(fps,vsync,windowFps);client.options.save();
                if(lease!=null){
                    try{
                        if(client.level==world&&client.player!=null){
                            client.player.setPos(x,y,z);client.player.setYRot(yaw);client.player.setXRot(pitch);client.level.setRainLevel(rain);client.level.setThunderLevel(thunder);
                            client.getSingleplayerServer().submit(()->{var level=client.getSingleplayerServer().overworld();var data=(net.minecraft.world.level.storage.ServerLevelData)level.getLevelData();level.setDayTime(dayTime);data.setClearWeatherTime(clearWeatherTime);data.setRainTime(rainTime);data.setThunderTime(thunderTime);data.setRaining(raining);data.setThundering(thundering);level.setRainLevel(rain);level.setThunderLevel(thunder);var player=client.getSingleplayerServer().getPlayerList().getPlayer(client.player.getUUID());if(player!=null)player.teleportTo(level,x,y,z,Set.of(),yaw,pitch);}).get(2,TimeUnit.SECONDS);
                        }
                    }finally{lease.close();lease=null;}
                }
            }
        }
        void finish(String failure){
            if(finished)return;finished=true;
            try{restore();metadata.setProperty("restoration","exact saved core/preset/flags/video and prior camera/environment/simulation restored");}
            catch(Exception recovery){failure=(failure==null?"":failure+"; ")+"Restoration failed: "+recovery;metadata.setProperty("restoration","FAILED; inspect live settings");}
            try{
                metadata.setProperty("result",failure==null?"COMPLETE":"FAILED");metadata.setProperty("finished_at",Instant.now().toString());
                if(failure!=null)metadata.setProperty("failure",failure);if(directory!=null)save();if(raw!=null)raw.close();
            }catch(IOException export){failure=(failure==null?"":failure+"; ")+export;}
            last=(failure==null?"COMPLETE":"FAILED: "+failure)+"; "+directory;job=null;
        }
        void save() throws IOException {try(var writer=Files.newBufferedWriter(directory.resolve("metadata.properties"))){metadata.store(writer,"ShaderLOD dev-only saved profile comparison; raw normal mod clocks");}}
    }
}
