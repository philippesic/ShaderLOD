package com.pp.shaderlod.fabric.dev.zeroemission;
import com.google.gson.*;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.bslquad.BslQuadEligibility;
import com.pp.shaderlod.integration.makeupquad.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
/** Same observer workload for stock/control/candidate. Raw intrinsic and wall sanity, no performance acceptance. */
public final class BslZeroEmissionObserver {
 public static final boolean REQUESTED=Boolean.getBoolean("shaderlod.dev.portBslZeroEmissionObserver");
 private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
 private static final JsonObject REPORT=new JsonObject();
 private static final String EPOCH=UUID.randomUUID().toString();
 private static final long START=System.nanoTime();
 private static final String ARM=System.getProperty("shaderlod.dev.portBslZeroEmissionArm","");
 private static final String CORE=System.getProperty("shaderlod.dev.portBslZeroEmissionCore","");
 private static Object world,pack,pipeline;
 private static FabricLodConfig.Values config;
 private static String archiveHash,stamp;
 private static Path archive;
 private static long stable,windowStart,frames,ordinal,lastObservationAt;
 private static int minEffective=Integer.MAX_VALUE,maxEffective;
 private static boolean done,armed,firstWorld,capturePending,captureAfterFrame,frameReady;
 private BslZeroEmissionObserver(){}
 public static String epoch(){return EPOCH;}
 public static long renderOrdinal(){return ordinal;}
 public static boolean armed(){return REQUESTED&&armed&&!done;}
 public static void draw(TerrainRenderPass pass){if(armed())BslZeroEmissionDrawCapture.draw(pass);}
 public static void before(boolean renderLevel){
  if(!REQUESTED||done)return;frameReady=false;ordinal++;var c=Minecraft.getInstance();
  try {
   long now=System.nanoTime(),deadline=Long.parseLong(System.getProperty("shaderlod.dev.startupDeadlineEpochMillis","0"));
   if(deadline<=System.currentTimeMillis()||now-START>180_000_000_000L)throw new IllegalStateException("finite wall/internal deadline");
   if(!renderLevel||c.level==null||c.player==null||!c.isGameLoadFinished()){stable=0;return;}
   if(!firstWorld){firstWorld=true;REPORT.add("firstWorldObservation",observation(c));phase(c,"waiting-for-native-readiness");}
   if(Iris.getCurrentPack().isEmpty()||Iris.getPipelineManager().getPipelineNullable()==null||!IrisApi.getInstance().isShaderPackInUse()) {if(armed||windowStart!=0)throw new IllegalStateException("native shader/pipeline disappeared after warmup");stable=0;return;}
   guard(c);
   if(now-lastObservationAt>1_000_000_000L&&windowStart==0){lastObservationAt=now;REPORT.add("currentObservation",observation(c));write(c);}
   if(!ready(c)){if(armed||windowStart!=0)throw new IllegalStateException("readiness changed after warmup");stable=0;return;}
   if(stable==0){stable=now;phase(c,"warming-native-private-scene");return;}
   if(now-stable<30_000_000_000L)return;
   armed=true;frameReady=true;
   if(BslZeroEmissionDrawCapture.complete()&&windowStart==0){validateDraws();REPORT.add("drawObservations",BslZeroEmissionDrawCapture.observations());windowStart=now;phase(c,"completed-render-wall-sanity");}
  }catch(Exception|LinkageError e){failure(e);}
 }
 public static void after(boolean completed){
  if(!REQUESTED)return;var c=Minecraft.getInstance();
  if(!completed){if(!done)failure(new IllegalStateException("native original render failed"));return;}
  try {
   if(frameReady&&windowStart!=0&&!done){
    var limiter=c.getFramerateLimitTracker();int configured=c.options.framerateLimit().get(),effective=limiter.getFramerateLimit();
    if(!com.pp.shaderlod.port.dev.PortRendererFrames.limiterAllows(configured,effective,limiter.getThrottleReason().name(),c.options.inactivityFpsLimit().get()==net.minecraft.client.InactivityFpsLimit.MINIMIZED,c.isWindowActive(),c.getWindow().isIconified())||!ready(c))throw new IllegalStateException("completed-frame limiter/nativeUI violation");
    int frameError=GL11.glGetError();if(frameError!=GL11.GL_NO_ERROR)throw new IllegalStateException("completed-render GLerror "+frameError);
    frames++;minEffective=Math.min(minEffective,effective);maxEffective=Math.max(maxEffective,effective);
    long elapsed=System.nanoTime()-windowStart;
    if(elapsed>=20_000_000_000L){guard(c);if(!archiveHash.equals(hash(archive)))throw new IllegalStateException("terminal archive changed");done=true;armed=false;
     REPORT.addProperty("status","complete-intrinsics-wall-sanity-pending-still");REPORT.addProperty("completedRenderFrames",frames);REPORT.addProperty("wallSeconds",elapsed/1e9);REPORT.addProperty("wallMillisecondsPerCompletedRender",elapsed/1e6/frames);REPORT.addProperty("minEffectiveLimit",minEffective);REPORT.addProperty("maxEffectiveLimit",maxEffective);
     REPORT.addProperty("avoidedShaderWorkMeasured",false);REPORT.addProperty("qualityAccepted",false);REPORT.addProperty("performanceAccepted",false);REPORT.addProperty("gpuQueriesPerformed",false);captureAfterFrame=true;capturePending=true;
    }
   }
   if(captureAfterFrame){captureAfterFrame=false;capture(c);}
  }catch(Exception|LinkageError e){failure(e);}
 }
 public static void failure(Throwable e){done=true;armed=false;captureAfterFrame=false;REPORT.addProperty("status","failed-zero-emission-observer");REPORT.addProperty("failure",e.toString());REPORT.add("drawObservations",BslZeroEmissionDrawCapture.observations());write(Minecraft.getInstance());}
 private static Map<String,String> options(){var o=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var v=o.getOptionValues();var map=new TreeMap<String,String>();o.getOptionSet().getBooleanOptions().keySet().forEach(k->map.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));o.getOptionSet().getStringOptions().keySet().forEach(k->map.put(k,v.getStringValueOrDefault(k)));return map;}
 private static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 private static String stamp(Path p)throws Exception{var a=Files.readAttributes(p,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);return a.size()+":"+a.lastModifiedTime()+":"+a.fileKey();}
 private static void guard(Minecraft c)throws Exception{
  if(!Set.of("stock","control","candidate").contains(ARM)||!Set.of("off","on","quality").contains(CORE))throw new IllegalStateException("explicit arm/core mode required");
  for(String flag:List.of("portBslEarlyAlpha","portBslEarlyAlphaObserver","portBslGpuBudget","portBslAlphaAdmission","portBslOriginalSource","portSildurOriginalSource","portRoundtrip","portQualityRoundtrip","portRoundtripUi","portRendererSpeedProbe","gpuTiming","compare","benchmark","measureOnly"))if(!System.getProperty("shaderlod.dev."+flag,"false").equals("false"))throw new IllegalStateException("conflicting property "+flag);
  var cfg=FabricLodConfig.snapshot();var opts=options();
  if(!FabricLodConfig.loaded()||cfg.debug()||cfg.enabled()!=!CORE.equals("off")||cfg.preset()!=(CORE.equals("quality")?LodPreset.QUALITY:LodPreset.PERFORMANCE)||!BslQuadEligibility.optionsMatch(opts)||Iris.isFallback()||Iris.getIrisConfig().areDebugOptionsEnabled()||!Iris.getShaderPackOptionQueue().isEmpty()||Boolean.getBoolean("shaderlod.rendererSpeedPortR1")||MakeUpQuadRuntime.enabled()||Boolean.getBoolean(RendererRolloutPolicy.RESEARCH_ROUTES))throw new IllegalStateException("native384/core/debug/export/renderer guard");
  if(BslZeroEmission.REQUESTED==ARM.equals("stock"))throw new IllegalStateException("stock bridgeOFF/private bridgeON required");
  if(!Iris.getCurrentPackName().equals(Iris.getIrisConfig().getShaderPackName().orElse("")))throw new IllegalStateException("selected/loaded mismatch");
  if(!MakeUpQuadRuntime.resourcesMatch(c.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList()))throw new IllegalStateException("resources mismatch");
  if(world==null){
   if(!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()||!"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName())||!org.lwjgl.opengl.GL.getCapabilities().OpenGL41)throw new IllegalStateException("actual OpenGL41/render-thread backend");
   var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();archive=directory.resolve(Iris.getCurrentPackName()).normalize();if(!archive.startsWith(directory)||archive.equals(directory))throw new IllegalStateException("archive path outside shaderpack directory");archiveHash=hash(archive);String wanted=BslZeroEmission.expectedArchive(ARM);
   if(Files.isSymbolicLink(archive)||!archiveHash.equals(wanted)||!MakeUpQuadApiPins.exactApi())throw new IllegalStateException("exact named original/private archive/API");
   var mods=new TreeMap<String,String>();net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods().forEach(m->{if(!m.getMetadata().getId().equals("java"))mods.put(m.getMetadata().getId(),m.getMetadata().getVersion().getFriendlyString());});if(!MakeUpQuadRuntime.stackMatches(mods))throw new IllegalStateException("tuple stack");
   pipeline=Iris.getPipelineManager().getPipelineNullable();if(pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class||((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass()!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class||Iris.getCurrentPack().orElseThrow().getShaderPackOptions().getOptionValues().getClass()!=net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class)throw new IllegalStateException("pipeline class");pack=Iris.getCurrentPack().orElseThrow();world=c.level;config=cfg;stamp=stamp(archive);
   REPORT.addProperty("arm",ARM);REPORT.addProperty("expectedCore",CORE);REPORT.addProperty("archiveSha256",archiveHash);REPORT.add("native384",JSON.toJsonTree(opts));REPORT.add("core",JSON.toJsonTree(cfg));REPORT.add("mods",JSON.toJsonTree(mods));REPORT.addProperty("glRenderer",GL11.glGetString(GL11.GL_RENDERER));REPORT.addProperty("glVersion",GL11.glGetString(GL11.GL_VERSION));
  }else if(world!=c.level||pack!=Iris.getCurrentPack().orElseThrow()||pipeline!=Iris.getPipelineManager().getPipelineNullable()||!config.equals(cfg)||!stamp.equals(stamp(archive)))throw new IllegalStateException("world/pack/pipeline/config/source changed");
 }
 private static boolean ready(Minecraft c){var p=c.player;var w=c.getWindow();var t=c.getMainRenderTarget();return c.screen==null&&!c.isPaused()&&!c.noRender&&c.isWindowActive()&&w.isFullscreen()&&GLFW.glfwGetWindowMonitor(w.handle())!=0&&w.getWidth()==1920&&w.getHeight()==1200&&t.width==1920&&t.height==1200&&c.options.renderDistance().get()==24&&!c.options.enableVsync().get()&&c.options.framerateLimit().get()==260&&c.level.dimension()==net.minecraft.world.level.Level.OVERWORLD&&c.level.getDayTime()==7000&&c.level.getRainLevel(1)==0&&c.level.getThunderLevel(1)==0&&p.isSpectator()&&Math.abs(p.getX()-256.5)<.0001&&Math.abs(p.getY()-95)<.0001&&Math.abs(p.getZ()-768.5)<.0001&&Math.abs(p.getYRot()-135)<.0001&&Math.abs(p.getXRot()-9)<.0001;}
 private static void validateDraws(){
  var draws=BslZeroEmissionDrawCapture.observations();
  if(draws.size()!=2||!draws.has("SOLID")||!draws.has("CUTOUT"))throw new IllegalStateException("exact native terrain roles required");
  String[] names={"shaderlodBslZeroEmissionEnabled","shaderlodBslZeroEmissionVersion","shaderlodBslZeroEmissionPreset"};
  for(String role:List.of("SOLID","CUTOUT")){
   var d=draws.getAsJsonObject(role);
   if(d.has("failure")||d.get("glErrorBefore").getAsInt()!=0||d.get("glErrorAfter").getAsInt()!=0||!d.get("programIdentityMatches").getAsBoolean()||d.get("attachedFragmentCount").getAsInt()!=1||!d.get("activeUnitRestored").getAsBoolean()||!d.get("programUnchanged").getAsBoolean()||!d.get("framebufferUnchanged").getAsBoolean())throw new IllegalStateException("native draw/source/GL/restoration failure: "+role);
   boolean allAbsent=true;for(String name:names)allAbsent&=d.get(name+"Location").getAsInt()==-1;
   if(ARM.equals("stock")){if(!allAbsent)throw new IllegalStateException("stock contains zero-emission protocol");}
   else if(ARM.equals("control")&&allAbsent){d.addProperty("unusedControlProtocolPruned",true);}
   else {
    int[] expected={CORE.equals("on")?1:0,1,CORE.equals("quality")?0:1};
    for(int i=0;i<names.length;i++)if(d.get(names[i]+"Location").getAsInt()<0||!d.has(names[i]+"Readback")||d.get(names[i]+"Readback").getAsInt()!=expected[i])throw new IllegalStateException("actual zero-emission protocol mismatch: "+role+" "+names[i]);
   }
   for(var entry:d.getAsJsonArray("attachedShaders")){
    var sh=entry.getAsJsonObject();if(!sh.has("source"))continue;String source=sh.get("source").getAsString();String compact=source.replaceAll("\\s+","")
     .replace("vec3(0.0f)","vec3(0.0)").replace("vec3(2.0f)","vec3(2.0)"); // Iris emits explicit float suffixes; preserve the exact domain/constants.
    boolean skip=java.util.regex.Pattern.compile("shaderlodBslZeroEmissionSkip\\s*\\(\\s*emission\\s*,\\s*albedo\\.rgb\\s*\\)").matcher(source).find();
    if(skip!=ARM.equals("candidate"))throw new IllegalStateException("zero-emission helper call differs from arm: "+role);
    if(!compact.contains("RGB2HSV(albedo.rgb)")||!compact.contains("GetHardcodedEmission(albedo.rgb,hsv)"))throw new IllegalStateException("native emission arithmetic absent: "+role);
    if(ARM.equals("candidate")&&(!compact.contains("if(!shaderlodBslZeroEmissionSkip(emission,albedo.rgb))")||!compact.contains("shaderlodBslZeroEmissionEnabled")||!compact.contains("shaderlodBslZeroEmissionVersion==1")||!compact.contains("shaderlodBslZeroEmissionPreset==1")||!compact.contains("nativeEmission==0.0")||!compact.contains("greaterThanEqual(nativeRgb,vec3(0.0))")||!compact.contains("lessThanEqual(nativeRgb,vec3(2.0))")))throw new IllegalStateException("candidate exact guarded emission safety domain absent: "+role);
    if(source.contains("shaderlodBslNativeAlphaReject")||source.contains("shaderlodBslShadowColor")||source.contains("shaderlodBslAlphaReference"))throw new IllegalStateException("unrelated alpha/owned LOD prototype source: "+role);
    if(role.equals("CUTOUT")&&!compact.contains("!(iris_FragData0.a>iris_currentAlphaTest)"))throw new IllegalStateException("native late alpha predicate absent");
   }
  }
  if(!draws.getAsJsonObject("SOLID").get("nativeEpoch").getAsString().equals(draws.getAsJsonObject("CUTOUT").get("nativeEpoch").getAsString()))throw new IllegalStateException("native terrain epoch differs");
 }
 private static JsonObject observation(Minecraft c){var o=new JsonObject();o.addProperty("capturedAtUtc",java.time.Instant.now().toString());o.addProperty("worldPresent",c.level!=null);o.addProperty("playerPresent",c.player!=null);o.addProperty("screenClass",c.screen==null?"none":c.screen.getClass().getName());o.addProperty("screenAbsent",c.screen==null);o.addProperty("focused",c.isWindowActive());o.addProperty("paused",c.isPaused());o.addProperty("fullscreen",c.getWindow().isFullscreen());o.addProperty("framebufferWidth",c.getMainRenderTarget().width);o.addProperty("framebufferHeight",c.getMainRenderTarget().height);o.addProperty("windowFramebufferWidth",c.getWindow().getWidth());o.addProperty("windowFramebufferHeight",c.getWindow().getHeight());o.addProperty("nativeMonitor",GLFW.glfwGetWindowMonitor(c.getWindow().handle()));o.addProperty("irisSelectedPack",Iris.getIrisConfig().getShaderPackName().orElse(""));o.addProperty("irisLoadedPack",Iris.getCurrentPackName());o.addProperty("shaderPackInUse",IrisApi.getInstance().isShaderPackInUse());o.addProperty("irisFallback",Iris.isFallback());if(c.player!=null){o.addProperty("cameraX",c.player.getX());o.addProperty("cameraY",c.player.getY());o.addProperty("cameraZ",c.player.getZ());o.addProperty("yaw",c.player.getYRot());o.addProperty("pitch",c.player.getXRot());}if(c.level!=null)o.addProperty("dayTime",c.level.getDayTime());return o;}
 private static void phase(Minecraft c,String phase){REPORT.addProperty("status",phase);REPORT.add("currentObservation",observation(c));write(c);}
 private static void capture(Minecraft c){String name="shaderlod-bsl-zero-emission-"+ARM+"-"+System.currentTimeMillis()+".png";Path image=c.gameDirectory.toPath().resolve("screenshots").resolve(name);REPORT.addProperty("still",image.toString());Screenshot.grab(c.gameDirectory,name,c.getMainRenderTarget(),1,m->c.execute(()->{capturePending=false;boolean nonblack=false;try{var img=javax.imageio.ImageIO.read(image.toFile());if(img!=null){outer:for(int y=0;y<img.getHeight();y+=8)for(int x=0;x<img.getWidth();x+=8)if((img.getRGB(x,y)&0xffffff)!=0){nonblack=true;break outer;}}}catch(Exception ignored){}REPORT.addProperty("stillSaved",Files.isRegularFile(image));REPORT.addProperty("stillNonblack",nonblack);REPORT.addProperty("status",nonblack?"complete-zero-emission-observations":"failed-zero-emission-still");write(c);}));write(c);}
 private static void write(Minecraft c){try{REPORT.addProperty("capturePending",capturePending);var p=c.gameDirectory.toPath().resolve("shaderlod-dev/bsl-zero-emission-observer.json");Files.createDirectories(p.getParent());Files.writeString(p,JSON.toJson(REPORT)+"\n");}catch(Exception e){org.slf4j.LoggerFactory.getLogger("shaderlod-dev").error("zero-emission observer receipt failed",e);}}
}
