package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;import com.pp.shaderlod.integration.*;import java.nio.file.*;import java.util.*;import java.util.concurrent.atomic.AtomicInteger;
/** Actual persistence/frame snapshot/public source-session checks; driver reload callback is counted, never simulated as GL evidence. */
public final class LiveUniformApplyChecksMain {
 static int checks;static void check(boolean b,String n){checks++;if(!b)throw new AssertionError(n);}
 static final class Session implements CompileOptionControl.Session{
  final List<String> events=new ArrayList<>();boolean prepared,failPrepare,failFinish;Error finishError;
  public boolean prepare(){events.add("prepare");if(failPrepare)throw new IllegalStateException("source preparation refused");return prepared;}
  public void reload(OptimizationEditTransaction.Reload r)throws Exception{events.add("reload");r.run();}
  public void restore(){events.add("restore");}
  public void recoveryReload(OptimizationEditTransaction.Reload r)throws Exception{events.add("recovery");r.run();}
  public void finish(){events.add("finish");if(finishError!=null)throw finishError;if(failFinish)throw new IllegalStateException("finish failed");}
 }
 static final class Backend implements PackSelectionTransaction.Backend{
  boolean rendererAvailable;public boolean nativeRendererAvailable(){return rendererAvailable;}final Path dir;String selected="Original.zip";final Map<String,String> queue=new LinkedHashMap<>();Backend(Path base)throws Exception{this(base,Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip"));}Backend(Path base,Path original)throws Exception{dir=Files.createTempDirectory(base,"real-source-");Files.copy(original,dir.resolve(selected));}
  public Path directory(){return dir;}public String selected(){return selected;}public Map<String,String> queue(){return queue;}public Map<String,String> effectiveOptions(){return Map.of();}public void select(String name){selected=name;}public void save(){}
 }
 public static void main(String[]args)throws Exception{
  var previous=OptimizationConfig.state();var field=OptimizationEditTransaction.class.getDeclaredField("liveUniformUpdates");field.setAccessible(true);boolean oldOptIn=field.getBoolean(null);var base=Files.createTempDirectory("shaderlod-live-apply-");Path config=base.resolve("flags.properties");
  try{
   OptimizationConfig.load(config);var none=OptimizationSnapshot.noneRequested();OptimizationConfig.replace(none);field.setBoolean(null,false);var reloads=new AtomicInteger();var s=new Session();var initial=s;
   var result=OptimizationEditTransaction.apply(none.with(OptimizationId.CLOUD_PRIMARY_DETAIL,true),reloads::incrementAndGet,()->false,flags->initial);
   check(result.success()&&reloads.get()==1&&result.message().contains("reloaded"),"default other-target behavior still reloads");
   OptimizationEditTransaction.enableLiveUniformUpdates();
   for(var id:OptimizationEditTransaction.liveUniformIds()){
    OptimizationConfig.replace(none);reloads.set(0);s=new Session();var session=s;var next=none.with(id,true);long generation=OptimizationConfig.generation();
    result=OptimizationEditTransaction.apply(next,reloads::incrementAndGet,()->false,flags->session);
    check(result.success()&&reloads.get()==0&&result.message().contains("live uniform"),"audited shader-only live request "+id);
    check(s.events.equals(List.of("prepare","finish")),"live retains source prepare/finish "+id);
    check(OptimizationConfig.snapshot().equals(next)&&OptimizationConfig.generation()>generation,"actual complete snapshot/generation publishes "+id);
    var saved=new Properties();try(var in=Files.newInputStream(config)){saved.load(in);}check(saved.getProperty(id.key()).equals("true"),"actual persisted request "+id);
    var offSession=new Session();result=OptimizationEditTransaction.apply(none,reloads::incrementAndGet,()->false,flags->offSession);check(result.success()&&reloads.get()==0&&OptimizationConfig.snapshot().equals(none),"live Off restores requested native work "+id);
   }
   for(var unsafe:OptimizationId.values())if(!OptimizationEditTransaction.liveUniformIds().contains(unsafe)){
    for(boolean removing:List.of(false,true)){
     var before=none.with(unsafe,removing);var after=before.with(OptimizationId.CLOUD_PRIMARY_DETAIL,true).with(unsafe,!removing);OptimizationConfig.replace(before);reloads.set(0);s=new Session();var session=s;
     result=OptimizationEditTransaction.apply(after,reloads::incrementAndGet,()->false,flags->session);check(result.success()&&reloads.get()==1,"new/removed graph/resource/history request forces reload "+unsafe);
    }
    var before=none.with(unsafe,true);OptimizationConfig.replace(before);reloads.set(0);s=new Session();var session=s;result=OptimizationEditTransaction.apply(before.with(OptimizationId.AO_BUDGET,true),reloads::incrementAndGet,()->false,flags->session);check(result.success()&&reloads.get()==1,"unchanged active unsafe peer forces reload "+unsafe);
   }
   OptimizationConfig.replace(none);reloads.set(0);s=new Session();s.prepared=true;var prepared=s;result=OptimizationEditTransaction.apply(none.with(OptimizationId.CLOUD_PRIMARY_DETAIL,true),reloads::incrementAndGet,()->false,flags->prepared);check(result.success()&&reloads.get()==1,"first source/changed private keys prepare=true still reloads");
   OptimizationConfig.replace(none);reloads.set(0);result=OptimizationEditTransaction.apply(none.with(OptimizationId.AO_BUDGET,true),reloads::incrementAndGet,()->false);check(result.success()&&reloads.get()==1,"absent source session cannot use live path");
   OptimizationConfig.replace(none);reloads.set(0);s=new Session();s.failPrepare=true;var prepareFailure=s;result=OptimizationEditTransaction.apply(none.with(OptimizationId.AO_BUDGET,true),reloads::incrementAndGet,()->false,flags->prepareFailure);check(!result.success()&&OptimizationConfig.snapshot().equals(none)&&s.events.equals(List.of("prepare","restore","recovery","finish")),"failed preparation restores flags/source and releases session");
   OptimizationConfig.replace(none);reloads.set(0);s=new Session();s.failFinish=true;var finishFailure=s;result=OptimizationEditTransaction.apply(none.with(OptimizationId.AO_BUDGET,true),reloads::incrementAndGet,()->false,flags->finishFailure);check(!result.success()&&OptimizationConfig.snapshot().equals(none)&&s.events.equals(List.of("prepare","finish","restore","recovery"))&&reloads.get()==1,"failed live finish restores without replaying finish");
   OptimizationConfig.replace(none);s=new Session();Error primary=new AssertionError("primary finish error");s.finishError=primary;var errorSession=s;try{OptimizationEditTransaction.apply(none.with(OptimizationId.AO_BUDGET,true),()->{},()->false,flags->errorSession);throw new AssertionError("primary Error swallowed");}catch(Error actual){check(actual==primary&&OptimizationConfig.snapshot().equals(none),"Error identity preserved after recovery");}
   check(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended(),"all paths release lifecycle suspension");
   // Cancelled staging never calls Apply or mutates actual persisted requests.
   OptimizationConfig.replace(none);byte[] beforeCancel=Files.readAllBytes(config);var staged=none.with(OptimizationId.AO_BUDGET,true);check(!staged.equals(none)&&OptimizationConfig.snapshot().equals(none)&&Arrays.equals(beforeCancel,Files.readAllBytes(config)),"staged Cancel leaves config/source untouched");
   // Authentic shared frame notifier: publication cannot split the currently rendering frame.
   var listeners=new ArrayList<Runnable>();var frame=new AtomicInteger(1);var world=new Object();var state=CoreUniformFrame.forNotifier(new Object(),listeners::add,()->new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE),()->world,frame::get);listeners.getFirst().run();var current=state.current();s=new Session();var frameSession=s;result=OptimizationEditTransaction.apply(none.with(OptimizationId.AO_BUDGET,true),()->{throw new AssertionError("unexpected reload");},()->false,flags->frameSession);check(result.success()&&state.current()==current&&!state.current().core().optimizations().enabled(OptimizationId.AO_BUDGET),"published live flags do not split current frame");listeners.getFirst().run();check(state.current()==current,"duplicate same-frame notification stays immutable");frame.incrementAndGet();listeners.getFirst().run();check(state.current().core().optimizations().enabled(OptimizationId.AO_BUDGET)&&state.current().historyReset(),"next actual notifier snapshots new flags and invalidates temporal metadata");
   // Reuse the actual public pack-selection service and actual CompileOptionControl source Session.
   OptimizationConfig.replace(none);var backend=new Backend(base);var first=new PackSelectionTransaction(backend);check(first.prepare(true,LodPreset.PERFORMANCE),"actual original-to-generated source preparation requires reload");check(!backend.selected.equals("Original.zip"),"real maintained source generated, no invented archive");first.finishRecovery();byte[] archive=Files.readAllBytes(backend.dir.resolve(backend.selected));
   var steady=new PackSelectionTransaction(backend);var core=new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE);var next=none.with(OptimizationId.CLOUD_PRIMARY_DETAIL,true);var realSession=CompileOptionControl.session(steady,core,next,false,backend.queue,Map.of());reloads.set(0);result=OptimizationEditTransaction.apply(next,reloads::incrementAndGet,()->false,flags->realSession);check(result.success()&&reloads.get()==0,"actual steady generated source Session.prepare=false admits live path");check(Arrays.equals(archive,Files.readAllBytes(backend.dir.resolve(backend.selected)))&&backend.queue.isEmpty(),"actual generated source archive and unrelated queue retained");
   // Real original source permission, separate from the uniform-only whitelist.
   OptimizationConfig.replace(none);var cpuBackend=new Backend(base);cpuBackend.rendererAvailable=true;
   var pairFlags=none.with(OptimizationId.QUAD_COMMAND_COMPACTION,true).with(OptimizationId.SHADOW_ADMISSION_CACHE,true);
   boolean certified=CompileOptionControl.cpuLiveSource(cpuBackend.dir,cpuBackend.selected,"makeup-ultra-fast-shaders",core,none,pairFlags,true);
   check(certified,"real original pair identity permits scoped CPU certificate");
   var cpuSelection=new PackSelectionTransaction(cpuBackend);var cpuSession=CompileOptionControl.session(cpuSelection,core,pairFlags,false,cpuBackend.queue,Map.of(),certified);reloads.set(0);
   result=OptimizationEditTransaction.apply(pairFlags,reloads::incrementAndGet,()->false,flags->cpuSession);
   check(result.success()&&reloads.get()==0&&result.message().contains("native CPU"),"real original source pair switches with zero driver reloads");
   check(cpuBackend.selected.equals("Original.zip")&&cpuBackend.queue.isEmpty(),"CPU switch retains actual native source and empty queue");
   check(!CompileOptionControl.cpuLiveSource(cpuBackend.dir,cpuBackend.selected,"solas-shader",core,pairFlags,none,true),"blocked families cannot acquire CPU permission");
   check(!CompileOptionControl.cpuLiveSource(cpuBackend.dir,cpuBackend.selected,"makeup-ultra-fast-shaders",core,pairFlags,none,false),"missing native availability cannot acquire CPU permission");
   check(!CompileOptionControl.cpuLiveSource(cpuBackend.dir,cpuBackend.selected,"makeup-ultra-fast-shaders",core,pairFlags,pairFlags.with(OptimizationId.AO_BUDGET,true),true),"shader peer excludes CPU permission");
   cpuBackend.queue.put("AA_TYPE","0");var queuedSelection=new PackSelectionTransaction(cpuBackend);var queuedSession=CompileOptionControl.session(queuedSelection,core,none,false,cpuBackend.queue,Map.of(),true);
   queuedSession.prepare();check(!queuedSession.allowLiveCpu(pairFlags,none),"queued native writer intent cannot skip reload");queuedSession.restore();queuedSession.finish();cpuBackend.queue.clear();
   var failureSelection=new PackSelectionTransaction(cpuBackend);var failureSession=CompileOptionControl.session(failureSelection,core,none,false,cpuBackend.queue,Map.of(),true);reloads.set(0);var fallbackCalls=new AtomicInteger();
   result=OptimizationEditTransaction.apply(none,reloads::incrementAndGet,()->fallbackCalls.getAndIncrement()==0,flags->failureSession);
   check(!result.success()&&result.recoveryComplete()&&reloads.get()==1&&OptimizationConfig.snapshot().equals(pairFlags),"failed CPU live fallback restores persisted flags/source with one recovery reload");
   var changedSelection=new PackSelectionTransaction(cpuBackend);var changedSession=CompileOptionControl.session(changedSelection,core,none,false,cpuBackend.queue,Map.of(),true);
   check(!changedSession.prepare(),"unchanged native graph does not request reload");Files.write(cpuBackend.dir.resolve("Original.zip"),new byte[]{7},StandardOpenOption.APPEND);
   boolean cpuRefused=false;try{changedSession.allowLiveCpu(pairFlags,none);}catch(java.io.IOException expected){cpuRefused=true;}check(cpuRefused,"independent original source mutation refuses live CPU publication");
   OptimizationConfig.replace(none);
   for(var input:List.of(
     new String[]{"complementary-reimagined","dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"},
     new String[]{"complementary-unbound","dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip"},
     new String[]{"bliss-shader","dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"},
     new String[]{"photon","dependencies/upstream/photon_v1.3b.zip"})){
    OptimizationConfig.replace(none);var port=new Backend(base,Path.of(input[1]));port.rendererAvailable=true;
    boolean portCertified=CompileOptionControl.cpuLiveSource(port.dir,port.selected,input[0],core,none,pairFlags,true);
    check(portCertified,"pinned original source permits CPU-live family: "+input[0]);
    check(!CompileOptionControl.cpuLiveSource(port.dir,port.selected,input[0],core,none,pairFlags,false),"unavailable hardware certificate refuses: "+input[0]);
    check(!CompileOptionControl.cpuLiveSource(port.dir,port.selected,input[0],core,pairFlags,pairFlags.with(OptimizationId.DEPTH_PRIMING,true),true),"GLSL peer excludes CPU-live: "+input[0]);
    var portSelection=new PackSelectionTransaction(port);var portSession=CompileOptionControl.session(portSelection,core,pairFlags,false,port.queue,Map.of(),portCertified);reloads.set(0);
    result=OptimizationEditTransaction.apply(pairFlags,reloads::incrementAndGet,()->false,flags->portSession);
    check(result.success()&&reloads.get()==0&&port.selected.equals("Original.zip"),"public original CPU Apply has zero reload callbacks: "+input[0]);
    var offSelection=new PackSelectionTransaction(port);var offSession=CompileOptionControl.session(offSelection,core,none,false,port.queue,Map.of(),portCertified);
    result=OptimizationEditTransaction.apply(none,reloads::incrementAndGet,()->false,flags->offSession);
    check(result.success()&&reloads.get()==0&&OptimizationConfig.snapshot().equals(none),"public original CPU requests Off remains live: "+input[0]);
   }
   var priorPersistFailure=OptimizationConfig.snapshot();Files.delete(config);Files.createDirectory(config);var persistFailure=new Session();reloads.set(0);result=OptimizationEditTransaction.apply(priorPersistFailure.with(OptimizationId.AO_BUDGET,true),reloads::incrementAndGet,()->false,flags->persistFailure);check(!result.success()&&OptimizationConfig.snapshot().equals(priorPersistFailure)&&Files.isDirectory(config),"failed persistence restores in-memory flags without overwriting external replacement");check(!persistFailure.events.contains("prepare")&&persistFailure.events.equals(List.of("restore","recovery","finish")),"failed persistence recovers before consumer preparation");
   System.out.println("PASS "+checks+" opt-in/policy/persistence/source Session/frame/recovery/Error/Cancel checks; reload callback counted, no driver/client claim");
  }finally{field.setBoolean(null,oldOptIn);OptimizationConfig.restoreState(previous);}
 }
}
