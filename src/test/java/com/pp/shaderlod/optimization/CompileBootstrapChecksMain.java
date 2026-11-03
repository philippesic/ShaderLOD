package com.pp.shaderlod.optimization;
import com.pp.shaderlod.*;import com.pp.shaderlod.integration.*;import com.pp.shaderlod.packs.*;
import com.google.common.collect.*;import net.irisshaders.iris.shaderpack.include.*;import net.irisshaders.iris.shaderpack.option.*;
import java.nio.file.*;import java.nio.file.attribute.*;import java.util.*;import java.util.zip.*;import java.io.*;
/** Real public pack selection/files and actual Iris parser/OptionValues application. No client/driver impersonation. */
public final class CompileBootstrapChecksMain {
 private static int checks;private static void check(boolean ok,String note){checks++;if(!ok)throw new AssertionError(note);}
 private static ShaderPackOptions options(Path archive,Map<String,String> supplied)throws Exception{
  var nodes=ImmutableMap.<AbsolutePackPath,FileNode>builder();try(var zip=new ZipFile(archive.toFile())){for(var entry:Collections.list(zip.entries())){String n=entry.getName();if(!n.startsWith("shaders/")||!n.matches(".*\\.(glsl|vsh|fsh|gsh|csh|tcs|tes|settings)$"))continue;var path=AbsolutePackPath.fromAbsolutePath("/"+n.substring(8));nodes.put(path,new FileNode(path,ImmutableList.copyOf(new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split("\\R"))));}}
  var ctor=IncludeGraph.class.getDeclaredConstructor(ImmutableMap.class,ImmutableMap.class);ctor.setAccessible(true);return new ShaderPackOptions(ctor.newInstance(nodes.buildOrThrow(),ImmutableMap.of()),supplied);
 }
 private static ShaderPackOptions reachableOptions(Path archive,Map<String,String> supplied)throws Exception{
  // Same real ShaderPack entry discovery used by AoHalfCompileChecksMain. An all-ZIP
  // inventory can merge declarations outside the actual reachable program graph;
  // conflicting defaults then initialize Iris's NeoForge logger/platform.
  try(var zip=FileSystems.newFileSystem(archive,Map.of())){
   var root=zip.getPath("/shaders");var starts=ImmutableList.<AbsolutePackPath>builder();
   for(String dimension:List.of("/","/world0/","/world-1/","/world1/"))ShaderPackSourceNames.findPresentSources(starts,root,AbsolutePackPath.fromAbsolutePath(dimension),ShaderPackSourceNames.POTENTIAL_STARTS);
   var entries=starts.build();check(!entries.isEmpty(),"actual Iris program entry points discovered: "+archive);
   var graph=new IncludeGraph(root,entries,false);check(graph.getFailures().isEmpty(),"actual reachable includes complete: "+graph.getFailures());
   return new ShaderPackOptions(graph,supplied);
  }
 }
 private static Map<String,String> effective(ShaderPackOptions opts){var out=new TreeMap<String,String>();var values=opts.getOptionValues();opts.getOptionSet().getBooleanOptions().keySet().forEach(k->out.put(k,Boolean.toString(values.getBooleanValueOrDefault(k))));opts.getOptionSet().getStringOptions().keySet().forEach(k->out.put(k,values.getStringValueOrDefault(k)));return out;}
 private static Properties properties(Path p)throws Exception{var result=new Properties();if(Files.exists(p))try(var in=Files.newInputStream(p)){result.load(in);}return result;}
 private static final class StagingQueue extends LinkedHashMap<String,String> {
  boolean failNextOwnedPut;final IllegalStateException stagingFailure=new IllegalStateException("partial staging failure");
  @Override public String put(String key,String value){String prior=super.put(key,value);if(failNextOwnedPut&&key.startsWith("SHADERLOD_")){failNextOwnedPut=false;throw stagingFailure;}return prior;}
 }
 static final class Backend implements PackSelectionTransaction.Backend,CompileOptionControl.ReloadInputs {
  final Path dir;final CompileOptionControl.Family family;final String key;final StagingQueue queue=new StagingQueue();final Map<String,String> originalValues;String selected="Original.zip";ShaderPackOptions current;int loads;boolean injectMatchingIntent,partialStagingFailure,pixelQueueAdmission,admitted=true,ignoreCompile,secondFailure,secondAdmissionLoss,firstIntent,sourceChange,archiveChange,modeChange;LodPreset preset=LodPreset.PERFORMANCE;
  boolean g03FirstLoad,failFirst,ignoreFirstCompile,staleFirstGeneration,staleFirstReceipt;
  boolean routeExact=true,routeOverworld=true;
  Object sourceReceipt=new Object();
  public Object receipt(){return sourceReceipt;}
  public boolean requireFreshReceipt(){return g03FirstLoad;}
  public com.pp.shaderlod.optimization.mips.BslLeanGraph.Route routingContext(){return route();}
  public com.pp.shaderlod.optimization.mips.BslLeanGraph.Route route(){return g03FirstLoad?new com.pp.shaderlod.optimization.mips.BslLeanGraph.Route(OptimizationConfig.snapshot(),true,family.id(),routeExact,routeOverworld):com.pp.shaderlod.optimization.mips.BslLeanGraph.LEGACY;}
  public CoreUniformFrame.Core core(){return g03FirstLoad?new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),preset):null;}
  Backend(Path base,String family,String key,Path original)throws Exception{this.dir=Files.createTempDirectory(base,"case-");this.family=CompileOptionControl.Family.fromId(family);this.key=key;Files.copy(original,dir.resolve(selected));current=parseOptions(dir.resolve(selected),Map.of());originalValues=Map.copyOf(effective(current));Files.writeString(dir.resolve(selected+".txt"),"# personal AA preserved\nAA_TYPE=2\n");Files.setPosixFilePermissions(dir.resolve(selected+".txt"),PosixFilePermissions.fromString("rw-r-----"));}
  private ShaderPackOptions parseOptions(Path archive,Map<String,String> supplied)throws Exception{return Set.of(CompileOptionControl.Family.REIMAGINED,CompileOptionControl.Family.UNBOUND,CompileOptionControl.Family.BLISS).contains(family)?reachableOptions(archive,supplied):options(archive,supplied);}
  public Path directory(){return dir;}public String selected(){return selected;}public Map<String,String> queue(){return queue;}public Map<String,String> effectiveOptions(){return effective(current);}public Map<String,String> declaredDefaultOptions(){return effective(optionsUnchecked());}private ShaderPackOptions optionsUnchecked(){try{return parseOptions(dir.resolve(selected),Map.of());}catch(Exception e){throw new RuntimeException(e);}}
  public boolean nativeRendererAvailable(){return true;}public void select(String s){selected=s;}public void save(){}
  public Map<String,CompileOptionControl.NativeOption> loaded(){return CompileOptionControl.readOptions(current,queue,false);}public boolean fallback(){return false;}
  public String admissionDetails(Set<String> keys){return keys.contains(key)?"native derived construction refused: regression producer":"unexpected control";}
  public Map<String,String> desired(){
   var actual=loaded();var keys=new HashSet<String>();if(actual.containsKey(key)&&CompileOptionControl.declaredContract(key,actual.get(key)))keys.add(key);
   boolean source=family.id().equals(PackSelectionService.verifyGeneratedRendererGraph(dir.resolve(selected),effective(current)));try{var fingerprint=PackSourceReader.read(dir.resolve(selected));var recognized=SupportedPackCatalog.recognize(fingerprint.memberSha256());source &= recognized.isPresent()&&recognized.get().stage()==SupportedPackCatalog.Stage.ASSEMBLED_ADAPTER&&recognized.get().pack().id().equals(family.id())&&SupportedPackCatalog.shaderSourceDigest(fingerprint.memberSha256()).equals(recognized.get().pack().adapterSourceDigest());}catch(Exception unavailable){source=false;}boolean nativeEqual=true;for(var entry:originalValues.entrySet())if(!entry.getValue().equals(effective(current).get(entry.getKey())))nativeEqual=false;
   var permissions=new EnumMap<OptimizationId,Boolean>(OptimizationId.class);OptimizationId id=key.equals(CompileOptionControl.MAKEUP_EARLY_ALPHA)?OptimizationId.EARLY_ALPHA:key.equals(CompileOptionControl.SILDUR_PIXEL_GRAPH)?OptimizationId.PIXEL_SCALE:key.endsWith("_DEPTH_PRIMING")?OptimizationId.DEPTH_PRIMING:key.endsWith("_AO_HALF_RES")?OptimizationId.AO_RENDER_SCALE:OptimizationId.DEAD_MIP_CHAIN;permissions.put(id,source&&nativeEqual&&admitted&&(!pixelQueueAdmission||queue.isEmpty()));
   String dim=key.equals(CompileOptionControl.PHOTON_DEAD_MIP)?"minecraft:the_nether":"minecraft:overworld";
   var plan=CompileOptionControl.plan(family,new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),preset),OptimizationSnapshot.noneRequested().with(id,true),keys,actual,dim,false,false,permissions);
   if(g03FirstLoad&&(!source||!nativeEqual||!admitted))plan=keys.contains(key)?Map.of(key,"0"):Map.of();
   if(loads==1&&injectMatchingIntent&&"1".equals(plan.get(key))){injectMatchingIntent=false;queue.put(key,"1");}
   if(loads==1&&partialStagingFailure&&"1".equals(plan.get(key))){partialStagingFailure=false;queue.failNextOwnedPut=true;}
   return plan;
  }
  void load()throws Exception{
   if(pixelQueueAdmission||g03FirstLoad)CompileOptionControl.synchronizeBeforeReload(queue,this::desired,this::loaded);
   loads++;var sidecar=dir.resolve(selected+".txt");var supplied=new TreeMap<String,String>();properties(sidecar).forEach((k,v)->supplied.put((String)k,(String)v));supplied.putAll(queue);
   if(ignoreCompile&&loads==2||ignoreFirstCompile&&loads==1)supplied.remove(key);current=parseOptions(dir.resolve(selected),supplied);
   // Actual Iris OptionValues application determines canonical sparse writer values; never inject generatedOptions(1).
   var defaults=effective(parseOptions(dir.resolve(selected),Map.of()));var result=new Properties();effective(current).forEach((k,v)->{if(!v.equals(defaults.get(k)))result.setProperty(k,v);});
   PackSelectionTransaction.irisOptionsWriteStarted(sidecar,result);if(result.isEmpty())Files.deleteIfExists(sidecar);else try(var out=Files.newOutputStream(sidecar)){result.store(out,null);}PackSelectionTransaction.irisOptionsWritten(sidecar,result);queue.clear();
   if(loads==1&&firstIntent)queue.put("NATIVE_UNRELATED","future");
   if(loads==1&&sourceChange)Files.writeString(sidecar,"# independent write\nAO=0\n");
   if(loads==1&&archiveChange)Files.write(dir.resolve(selected),new byte[]{7},StandardOpenOption.APPEND);
   if(loads==1&&modeChange)Files.setPosixFilePermissions(sidecar,PosixFilePermissions.fromString("r--r-----"));
   if(loads==2&&secondFailure)throw new IOException("second driver load rejected");
   if(loads==2&&secondAdmissionLoss)admitted=false;
   if(loads==1&&staleFirstGeneration)OptimizationConfig.restoreInMemory(OptimizationConfig.snapshot());
   if(loads==1&&failFirst)throw new IOException("first driver load rejected");
   if(!staleFirstReceipt)sourceReceipt=new Object();
  }
 }
 private static PackSelectionTransaction prepare(Backend b)throws Exception{var tx=new PackSelectionTransaction(b);boolean changed=tx.prepare(true,b.preset);check(b.preset==LodPreset.QUALITY?b.selected.equals("Original.zip"):changed,"real public selection/Quality native graph");return tx;}
 private static void run(Backend b,PackSelectionTransaction tx)throws Exception{CompileOptionControl.reloadTwoPhase(tx,b::load,b);}
 public static void main(String[] args)throws Exception{
  if(args.length<1||args.length>2||args.length==2&&!args[1].equals("--platform-required-four-family"))throw new IllegalArgumentException("fresh-receiver [--platform-required-four-family]; extra family parsing requires initialized NeoForge/Iris platform");
  boolean fourFamily=args.length==2;
  Path base=Path.of(args[0]);Files.createDirectories(base);OptimizationConfig.load(base.resolve("flags.properties"));OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.AO_RENDER_SCALE,true));Path makeup=Path.of("dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip");
  var b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);check(!b.loaded().containsKey(b.key),"authentic original100 has no generated half option");var aaBefore=b.originalValues;b.firstIntent=true;var tx=prepare(b);run(b,tx);check(b.loads==2,"one Apply loads generated then activates changed compile option exactly once");check(b.loaded().get(b.key).value().equals("1"),"actual new typed OptionValues consume canonical queued1 after second load");check(b.queue.equals(Map.of("NATIVE_UNRELATED","future")),"new unrelated intent is deferred and preserved");for(var entry:aaBefore.entrySet())check(entry.getValue().equals(b.effectiveOptions().get(entry.getKey())),"native original choice retained: "+entry.getKey());
  for(boolean quality:List.of(false,true)){b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);if(quality)b.preset=LodPreset.QUALITY;else b.admitted=false;tx=prepare(b);run(b,tx);check(b.loads==1,"unsupported/Quality has no unconditional second reload");check(!b.loaded().containsKey(b.key)||b.loaded().get(b.key).value().equals("0"),"unsupported/Quality remains native graph0");}
  b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);byte[] admissionOriginal=Files.readAllBytes(b.dir.resolve("Original.zip.txt"));b.secondAdmissionLoss=true;b.firstIntent=true;tx=prepare(b);boolean admissionRefused=false;try{run(b,tx);}catch(IOException expected){admissionRefused=expected.getMessage().contains("Compile admission/source/profile changed after activation")&&expected.getMessage().contains("native derived construction refused: regression producer")&&expected.getMessage().contains("admitted=");}check(admissionRefused&&b.loads==2,"post-link producer admission loss remains refused with precise producer diagnostic");tx.restoreForRecovery();var admissionRecovery=b;tx.recoveryReload(admissionRecovery::load);tx.finishRecovery();check(b.selected.equals("Original.zip")&&Arrays.equals(admissionOriginal,Files.readAllBytes(b.dir.resolve("Original.zip.txt"))),"post-link refusal restores exact original selection/sidecar");check(b.queue.equals(Map.of("NATIVE_UNRELATED","future")),"post-link refusal/recovery preserves unrelated queued intent");
  for(boolean ignored:List.of(false,true)){b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);byte[] prior=Files.readAllBytes(b.dir.resolve("Original.zip.txt"));var mode=Files.getPosixFilePermissions(b.dir.resolve("Original.zip.txt"));b.secondFailure=!ignored;b.ignoreCompile=ignored;b.firstIntent=true;tx=prepare(b);boolean failed=false;try{run(b,tx);}catch(IOException expected){failed=true;}check(failed,"second failure or ignored application is detected");tx.restoreForRecovery();var recovery=b;tx.recoveryReload(recovery::load);tx.finishRecovery();check(b.selected.equals("Original.zip"),"failed second load restores original selection");check(Arrays.equals(prior,Files.readAllBytes(b.dir.resolve("Original.zip.txt"))),"both witnessed writes restore exact original preimage");check(mode.equals(Files.getPosixFilePermissions(b.dir.resolve("Original.zip.txt"))),"original file permission preimage restored");check(b.queue.equals(Map.of("NATIVE_UNRELATED","future")),"recovery retains unrelated intent");try(var paths=Files.list(b.dir)){check(paths.filter(p->p.getFileName().toString().startsWith("ShaderLOD-")).count()==0,"failed action removes only its generated archive/sidecar");}}
  b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);b.sourceChange=true;tx=prepare(b);boolean refused=false;try{run(b,tx);}catch(IOException expected){refused=true;}check(refused&&b.loads==1,"independent sidecar mutation refuses second reload");refused=false;try{tx.restoreForRecovery();}catch(IOException expected){refused=true;}check(refused,"rollback refuses to overwrite independent edit");
  for(boolean archiveMutation:List.of(false,true)){b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);b.archiveChange=archiveMutation;b.modeChange=!archiveMutation;tx=prepare(b);boolean failed=false;try{run(b,tx);}catch(IOException expected){failed=true;}check(failed&&b.loads==1,"source archive or independent permission change refuses second load");failed=false;try{tx.restoreForRecovery();}catch(IOException expected){failed=true;}check(failed,"unowned archive/mode changes are preserved by refused recovery");}
  // Real source/OptionValues/transaction inputs; only GL driver execution is a unit backend.
  Path sildur=Path.of("dependencies/upstream/pack-expansion/Sildur's Vibrant Shaders v2.02 Extreme.zip");
  for(boolean ignore:List.of(false,true)) {
   OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.PIXEL_SCALE,true));
   var pixel=new Backend(base,"sildurs-vibrant-shaders",CompileOptionControl.SILDUR_PIXEL_GRAPH,sildur);pixel.pixelQueueAdmission=true;pixel.ignoreCompile=ignore;
   var pixelTx=prepare(pixel);boolean pixelRefused=false;
   try{run(pixel,pixelTx);}catch(IOException expected){pixelRefused=true;}
   check(pixelRefused==ignore,"actual Sildur second-load value checked after reload-head synchronization");
   if(!ignore){check(pixel.loads==2&&pixel.loaded().get(pixel.key).value().equals("1"),"real generated131 loaded1 survives owned reload HEAD");check(effective(pixel.current).size()==131,"authentic selected generated131");pixelTx.finishRecovery();}
   else {pixelTx.restoreForRecovery();pixelTx.recoveryReload(pixel::load);pixelTx.finishRecovery();check(pixel.selected.equals("Original.zip"),"wrong loaded0 restores actual native source");}
   check(pixel.queue.isEmpty(),"owned pixel activation queue cleaned");
  }
  OptimizationConfig.replace(OptimizationSnapshot.noneRequested());
  var failedPixel=new Backend(base,"sildurs-vibrant-shaders",CompileOptionControl.SILDUR_PIXEL_GRAPH,sildur);failedPixel.pixelQueueAdmission=true;failedPixel.ignoreCompile=true;
  var failedPixelTx=new PackSelectionTransaction(failedPixel);
  var failedResult=OptimizationEditTransaction.apply(OptimizationSnapshot.noneRequested().with(OptimizationId.PIXEL_SCALE,true),failedPixel::load,()->false,next->new CompileOptionControl.Session(){
   public boolean prepare()throws Exception{return failedPixelTx.prepare(true,LodPreset.PERFORMANCE);}
   public void reload(OptimizationEditTransaction.Reload unused)throws Exception{run(failedPixel,failedPixelTx);}
   public void restore()throws Exception{failedPixelTx.restoreForRecovery();}
   public void recoveryReload(OptimizationEditTransaction.Reload unused)throws Exception{failedPixelTx.recoveryReload(failedPixel::load);}
   public void finish(){failedPixelTx.finishRecovery();}
  });
  check(!failedResult.success()&&failedResult.recoveryComplete()&&!OptimizationConfig.requested(OptimizationId.PIXEL_SCALE)&&failedPixel.selected.equals("Original.zip"),"actual failed Apply restores request and native source after loaded0 mismatch");
  for(boolean partial:List.of(false,true)) {
   OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.PIXEL_SCALE,true));
   var contested=new Backend(base,"sildurs-vibrant-shaders",CompileOptionControl.SILDUR_PIXEL_GRAPH,sildur);contested.pixelQueueAdmission=true;
   contested.partialStagingFailure=partial;contested.injectMatchingIntent=!partial;
   var contestedTx=prepare(contested);RuntimeException failure=null;
   try{run(contested,contestedTx);}catch(RuntimeException expected){failure=expected;}
   check(failure!=null&&contested.loads==1,"refuse before second loader after foreign insertion/staging failure");
   if(partial){check(failure==contested.queue.stagingFailure,"partial put original exception preserved");check(contested.queue.isEmpty(),"actually staged matching entry cleaned after partial put throw");}
   else check(contested.queue.equals(Map.of(contested.key,"1")),"desired callback inserted foreign matching1 remains after unclaimed activation refusal");
   contestedTx.restoreForRecovery();contestedTx.finishRecovery();
  }
  var pixelQuality=new Backend(base,"sildurs-vibrant-shaders",CompileOptionControl.SILDUR_PIXEL_GRAPH,sildur);pixelQuality.pixelQueueAdmission=true;pixelQuality.preset=LodPreset.QUALITY;
  var pixelQualityTx=prepare(pixelQuality);run(pixelQuality,pixelQualityTx);pixelQualityTx.finishRecovery();check(pixelQuality.loads==1&&!pixelQuality.loaded().containsKey(pixelQuality.key),"Sildur Quality keeps actual original115 without activation scope");
  OptimizationConfig.replace(OptimizationSnapshot.noneRequested().with(OptimizationId.AO_RENDER_SCALE,true));
  for(String family:List.of("bsl-shaders","photon")){
   String key=family.equals("photon")?CompileOptionControl.PHOTON_DEAD_MIP:CompileOptionControl.BSL_AO_HALF;Path input=Path.of(family.equals("photon")?"dependencies/upstream/photon_v1.3b.zip":"dependencies/upstream/other-pack-research/BSL_v10.1.8.zip");b=new Backend(base,family,key,input);check(!b.loaded().containsKey(key),"authentic original has no controlled graph: "+family);tx=prepare(b);run(b,tx);check(b.loads==2&&b.loaded().get(key).value().equals("1"),"public family selection + actual typed numeric bootstrap "+family);
  }
  for(String personal:List.of("0","1")){
   b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);Files.writeString(b.dir.resolve("Original.zip.txt"),"# original private preference\n"+b.key+"="+personal+"\nAA_TYPE=2\n");byte[] originalPersonal=Files.readAllBytes(b.dir.resolve("Original.zip.txt"));tx=prepare(b);var sidecar=properties(b.dir.resolve(b.selected+".txt"));check(sidecar.getProperty(b.key).equals("0"),"first generated load clamps original unknown private preference to native0");run(b,tx);check(b.loaded().get(b.key).value().equals("1"),"single Apply activates actual generated1 from original private"+personal);String generated=b.selected;
   var actual=b.effectiveOptions();check("makeup-ultra-fast-shaders".equals(PackSelectionService.verifyGeneratedRendererGraph(b.dir.resolve(generated),actual)),"public source guard allows semantic private base choice variation");var invalid=new TreeMap<>(actual);invalid.put(b.key,"2");check(PackSelectionService.verifyGeneratedRendererGraph(b.dir.resolve(generated),invalid)==null,"wrong compile value refused");invalid=new TreeMap<>(actual);invalid.remove(b.key);check(PackSelectionService.verifyGeneratedRendererGraph(b.dir.resolve(generated),invalid)==null,"missing private base control refused");invalid=new TreeMap<>(actual);invalid.put("SHADERLOD_BSL_DEAD_MIP_CHAIN","1");check(PackSelectionService.verifyGeneratedRendererGraph(b.dir.resolve(generated),invalid)==null,"foreign family compile choice refused");invalid=new TreeMap<>(actual);invalid.put("AA_TYPE","0");check(PackSelectionService.verifyGeneratedRendererGraph(b.dir.resolve(generated),invalid)==null,"native AA variation still refused");
   var off=new PackSelectionService().apply(b.dir,generated,false,LodPreset.PERFORMANCE,actual,true);check(off.accepted()&&off.packFile().equals("Original.zip"),"normal public Off restores original");check(properties(b.dir.resolve("Original.zip.txt")).getProperty(b.key).equals(personal),"original unknown private preference restored unchanged");check(Files.readString(b.dir.resolve("Original.zip.txt")).contains("# original private preference\n"+b.key+"="+personal+"\n"),"original unknown private/comment lines byte-preserved through On/Off");
  }
  b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);var performance=new PackSelectionTransaction(b);performance.prepare(true,LodPreset.PERFORMANCE);var quality=new PackSelectionTransaction(b);quality.prepare(true,LodPreset.QUALITY);Path outputOriginal=b.dir.resolve("Original.zip");try(var zip=FileSystems.newFileSystem(outputOriginal,Map.of())){Files.writeString(zip.getPath("/shaders/lib/config.glsl"),"\n// independent selected output edit\n",StandardOpenOption.APPEND);}byte[] independentOriginal=Files.readAllBytes(outputOriginal);var selectedOutput=b;boolean outputRefused=false;try{quality.reload(selectedOutput::load);}catch(IOException expected){outputRefused=true;}check(outputRefused&&b.loads==0,"Quality existing original output source mutation refuses before forward load");try{quality.restoreForRecovery();}catch(IOException expected){}quality.finishRecovery();check(Arrays.equals(independentOriginal,Files.readAllBytes(outputOriginal)),"selected-original independent source edit never overwritten by recovery");
  // Exercise the new loaded CPU-only route with authentic archive/parser/writer inputs.
  var pairFlags=OptimizationSnapshot.noneRequested().with(OptimizationId.QUAD_COMMAND_COMPACTION,true).with(OptimizationId.SHADOW_ADMISSION_CACHE,true);
  OptimizationConfig.replace(pairFlags);b=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_AO_HALF,makeup);
  var nativePair=new PackSelectionTransaction(b);check(!nativePair.prepare(true,LodPreset.PERFORMANCE)&&b.selected.equals("Original.zip"),"loaded CPU pair retains exact original graph");
  var nativePairBackend=b;nativePair.reload(nativePairBackend::load);nativePair.finishRecovery();
  OptimizationConfig.replace(pairFlags.with(OptimizationId.AO_RENDER_SCALE,true));tx=prepare(b);run(b,tx);tx.finishRecovery();
  String shaderGraph=b.selected;check(!shaderGraph.equals("Original.zip")&&b.loaded().get(b.key).value().equals("1"),"CPU pair plus GLSL request consumes actual generated compile1");
  OptimizationConfig.replace(pairFlags);var returnPair=new PackSelectionTransaction(b);check(returnPair.prepare(true,LodPreset.PERFORMANCE)&&b.selected.equals("Original.zip"),"removing last GLSL request restores original CPU graph");
  var returnPairBackend=b;returnPair.reload(returnPairBackend::load);returnPair.finishRecovery();
  check(!b.loaded().containsKey(b.key),"returned actual native parser has no adapter compile declaration");
  for(var entry:b.originalValues.entrySet())check(entry.getValue().equals(b.effectiveOptions().get(entry.getKey())),"CPU/generated/CPU preserves native option "+entry.getKey());
  var nativeOff=new PackSelectionTransaction(b);nativeOff.prepare(false,LodPreset.PERFORMANCE);check(b.selected.equals("Original.zip"),"CPU pair Off retains original source");nativeOff.finishRecovery();
  // Core settings Apply must re-activate unchanged requests after a native Quality graph.
  for(boolean activationFailure:List.of(false,true)){
   var retained=OptimizationSnapshot.noneRequested().with(OptimizationId.DEPTH_PRIMING,true).with(OptimizationId.BINDING_CERTIFICATE_CACHE,true);
   OptimizationConfig.replace(retained);var presetBackend=new Backend(base,"makeup-ultra-fast-shaders",CompileOptionControl.MAKEUP_DEPTH,makeup);
   presetBackend.preset=LodPreset.QUALITY;var nativeQuality=new PackSelectionTransaction(presetBackend);nativeQuality.prepare(true,LodPreset.QUALITY);
   CompileOptionControl.reloadSettings(nativeQuality,presetBackend::load,presetBackend);nativeQuality.finishRecovery();
   check(presetBackend.selected.equals("Original.zip")&&!presetBackend.loaded().containsKey(presetBackend.key),"actual Quality graph has no private depth declaration");
   Path nativeSidecar=presetBackend.dir.resolve("Original.zip.txt");byte[] nativeBefore=Files.exists(nativeSidecar)?Files.readAllBytes(nativeSidecar):null;
   presetBackend.loads=0;presetBackend.preset=LodPreset.PERFORMANCE;presetBackend.secondFailure=activationFailure;
   var settingsSelection=new PackSelectionTransaction(presetBackend);check(settingsSelection.prepare(true,LodPreset.PERFORMANCE),"same settings transaction selects generated after Quality");
   boolean rejected=false;try{CompileOptionControl.reloadSettings(settingsSelection,presetBackend::load,presetBackend);}catch(IOException expected){rejected=true;}
   if(activationFailure){
    check(rejected&&presetBackend.loads==2,"actual second activation load failure detected");presetBackend.preset=LodPreset.QUALITY;
    settingsSelection.restoreForRecovery();settingsSelection.recoveryReload(presetBackend::load);settingsSelection.finishRecovery();
    check(presetBackend.loads==3&&presetBackend.selected.equals("Original.zip"),"settings activation failure restores native Quality in one recovery load");
    check(Arrays.equals(nativeBefore,Files.exists(nativeSidecar)?Files.readAllBytes(nativeSidecar):null),"both witnessed writes restore exact native sidecar preimage");
   }else{
    check(!rejected&&presetBackend.loads==2&&presetBackend.loaded().get(presetBackend.key).value().equals("1"),"Quality-to-Performance settings entry consumes actual generated depth1 in two loads");settingsSelection.finishRecovery();
   }
   check(OptimizationConfig.snapshot().equals(retained),"preset transition/recovery retains complete independent request vector");
   check(presetBackend.queue.isEmpty(),"settings bootstrap/recovery leaves no private compile intent");
  }
  int standaloneChecks=checks;
  System.out.println("PASS "+standaloneChecks+" standalone public Original/generated/actual Iris OptionValues/native AA/recovery checks; no driver or extra-four-family claim");
  // Opt-in requires the real initialized platform; standalone expansion failed twice.
  if(fourFamily){
  // Four additional pinned families reuse the identical certified CPU route and real typed bootstrap.
  for(var input:List.of(
    new String[]{"complementary-reimagined",CompileOptionControl.REIMAGINED_DEPTH,"dependencies/upstream/other-pack-research/ComplementaryReimagined_r5.9.3.zip"},
    new String[]{"complementary-unbound",CompileOptionControl.UNBOUND_DEPTH,"dependencies/upstream/other-pack-research/ComplementaryUnbound_r5.9.3.zip"},
    new String[]{"bliss-shader",CompileOptionControl.BLISS_DEPTH,"dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip"},
    new String[]{"photon",CompileOptionControl.PHOTON_DEPTH,"dependencies/upstream/photon_v1.3b.zip"})){
   OptimizationConfig.replace(pairFlags);var port=new Backend(base,input[0],input[1],Path.of(input[2]));
   byte[] stock=Files.readAllBytes(port.dir.resolve("Original.zip"));var identity=PackSelectionService.planSelection(port.dir,port.selected,false,LodPreset.QUALITY);
   check(identity!=null&&!port.loaded().containsKey(port.key),"pinned original/parser has no private depth option: "+input[0]);
   var cpu=new PackSelectionTransaction(port);check(!cpu.prepare(true,LodPreset.PERFORMANCE)&&port.selected.equals("Original.zip"),"CPU-only original retained: "+input[0]);cpu.reload(port::load);cpu.finishRecovery();
   OptimizationConfig.replace(pairFlags.with(OptimizationId.DEPTH_PRIMING,true));var shader=prepare(port);run(port,shader);shader.finishRecovery();
   String generatedPort=port.selected;check(!generatedPort.equals("Original.zip")&&port.loaded().get(port.key).value().equals("1"),"real Overworld depth GLSL consumer1: "+input[0]);
   check(identity.equals(PackSelectionService.planSelection(port.dir,port.selected,false,LodPreset.QUALITY)),"generated belongs to exact planned source pair: "+input[0]);
   check(!CompileOptionControl.cpuLiveSource(port.dir,port.selected,input[0],new CoreUniformFrame.Core(new LodSettings(true,32,96,8,false),LodPreset.PERFORMANCE),pairFlags,pairFlags,true),"generated source cannot acquire native-original live permission: "+input[0]);
   OptimizationConfig.replace(pairFlags);var cpuReturn=new PackSelectionTransaction(port);check(cpuReturn.prepare(true,LodPreset.PERFORMANCE)&&port.selected.equals("Original.zip"),"GLSL-to-CPU restores original: "+input[0]);cpuReturn.reload(port::load);cpuReturn.finishRecovery();
   check(!port.loaded().containsKey(port.key),"returned parser has no private depth graph: "+input[0]);
   for(var entry:port.originalValues.entrySet())check(entry.getValue().equals(port.effectiveOptions().get(entry.getKey())),"native option retained across CPU/GLSL/CPU: "+input[0]+"/"+entry.getKey());
   var offPort=new PackSelectionTransaction(port);offPort.prepare(false,LodPreset.PERFORMANCE);check(port.selected.equals("Original.zip"),"Off retains stock: "+input[0]);offPort.finishRecovery();
   check(Arrays.equals(stock,Files.readAllBytes(port.dir.resolve("Original.zip"))),"stock archive bytes unchanged: "+input[0]);
  }
  System.out.println("PASS "+(checks-standaloneChecks)+" optional platform-required four-family typed/source transition checks; no driver rendering claim");
  } else System.out.println("NOT RUN: extra four-family typed bootstrap (Reimagined/Unbound/Bliss/Photon); requires --platform-required-four-family and initialized NeoForge/Iris platform. Actual normal-client controls own acceptance.");
 }
}
