package com.pp.shaderlod.dev;
import com.google.gson.*;import java.nio.file.*;import net.irisshaders.iris.Iris;import org.lwjgl.opengl.*;
/** One explicitly armed actual native terrain submission; numeric program reuse is irrelevant. */
public final class BslMaterialProbe {
 private BslMaterialProbe(){}
 private static Object pack,pipeline,world;private static Path output;private static JsonObject result;private static boolean g03,bslPrivate;
 static void arm(Path path){
  if(!Boolean.getBoolean("shaderlod.dev.fragmentWorkProbe")||!Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe")||output!=null||Iris.isFallback())throw new IllegalStateException("Exclusive untimed material witness required");
  pack=Iris.getCurrentPack().orElseThrow();pipeline=Iris.getPipelineManager().getPipelineNullable();world=net.minecraft.client.Minecraft.getInstance().level;if(world==null)throw new IllegalStateException("World required");output=path;result=null;g03=false;bslPrivate=false;
 }
 static void armBslOptionMismatch(Path path)throws Exception {
  if(!com.pp.shaderlod.optimization.bslearlyalpha.mixin.BslEarlyAlphaPlugin.exactApi())throw new IllegalStateException("Pinned BSL API required");
  var pack=Iris.getCurrentPack().orElseThrow();var actual=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());
  var expected=new java.util.TreeMap<>(com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions());expected.put("TAA","false");
  String name=Iris.getCurrentPackName();
  if(!actual.equals(expected)||!name.equals(java.nio.file.Path.of(name).getFileName().toString())
      ||!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.ARCHIVE_SHA.equals(LoadedArtifactEvidence.fileSha256(Iris.getShaderpacksDirectory().resolve(name))))throw new IllegalStateException("Exact original BSL single TAAfalse mismatch required");
  arm(path);g03=true;
 }
 static void armBslPublic(Path path)throws Exception {
  if(!com.pp.shaderlod.optimization.bslearlyalpha.mixin.BslEarlyAlphaPlugin.exactApi())throw new IllegalStateException("Pinned public BSL API required");
  var pack=Iris.getCurrentPack().orElseThrow();var options=com.pp.shaderlod.optimization.sourcecache.EffectiveOptions.current(pack.getShaderPackOptions());
  if(!com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(Iris.getShaderpacksDirectory().resolve(Iris.getCurrentPackName()),options).eligible())throw new IllegalStateException("Original BSL/native384 public witness required");
  arm(path);g03=true;
 }
 static void armBslPrivate(Path path)throws Exception {
  if(!com.pp.shaderlod.experiment.sourceonlyalpha.BslPostSampleAlphaR1.nativeInputsCurrent())throw new IllegalStateException("Exact original BSL/native384 private witness required");
  arm(path);g03=true;bslPrivate=true;
 }
 static void armG03(Path path,String digest)throws Exception {
  if(!Boolean.getBoolean("shaderlod.dev.sourceOnlyAlphaAsahiR1")||!com.pp.shaderlod.experiment.sourceonlyalpha.SourceOnlyAlphaR23.exactApi()
   ||!java.util.Set.of("0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8","e86463d58cb2b58911a7f20052ac8a3f6ca2fcc81773fab5e34dd5451bcc3567","05d8b5669946000355df8abe95e3028e893059864c752a3042ce176c14e43dbb").contains(digest))throw new IllegalStateException("Pinned G03 witness required");
  String name=Iris.getCurrentPackName();if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!LoadedArtifactEvidence.fileSha256(Iris.getShaderpacksDirectory().resolve(name)).equals(digest))throw new IllegalStateException("G03 archive differs");
  arm(path);g03=true;
 }
 static void armG03Public(Path path,String digest)throws Exception {
  if(!com.pp.shaderlod.optimization.makeupearlyalpha.mixin.MakeUpEarlyAlphaPlugin.exactApi())throw new IllegalStateException("Pinned public G03 API required");
  String name=Iris.getCurrentPackName();
  if(!name.matches("[A-Za-z0-9_.-]+\\.zip")||!LoadedArtifactEvidence.fileSha256(Iris.getShaderpacksDirectory().resolve(name)).equals(digest))throw new IllegalStateException("Public G03 archive differs");
  boolean original="0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8".equals(digest);
  var current=Iris.getCurrentPack().orElseThrow();
  if(!original||!"MakeUpNativeOriginal.zip".equals(name)) {
   if(!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaCompilation.enabled(current)
    ||!com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpEarlyAlphaRuntime.source(current))throw new IllegalStateException("Owned public G03 graph required");
  }
  arm(path);g03=true;
 }
 public static void beforeDraw(){
  if(output==null)return;
  try{
   if(Iris.getCurrentPack().orElse(null)!=pack||Iris.getPipelineManager().getPipelineNullable()!=pipeline||net.minecraft.client.Minecraft.getInstance().level!=world)throw new IllegalStateException("Material witness identity changed");
   int program=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
   if(g03) {
    if(!(pipeline instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline iris)||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())return;
    var cutout=iris.getSodiumPrograms().getProgram(net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses.CUTOUT);
    if(cutout==null||program!=cutout.handle())return;
    int location=GL20.glGetUniformLocation(program,"iris_currentAlphaTest");if(location<0)throw new IllegalStateException("Native CUTOUT alpha uniform absent");
    var x=com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.bslMaterialState();
    if(!"complete_attached_fragment_bytes".equals(x.getAsJsonObject("attached_fragment_source").get("status").getAsString()))throw new IllegalStateException("Complete actual CUTOUT fragment required");
    x.addProperty("draw_origin",bslPrivate?"actual-bsl-private-native-cutout-pre-submission":"actual-g03-native-cutout-pre-submission");x.addProperty("matches_named_cutout_program",true);x.addProperty("native_alpha_reference",GL20.glGetUniformf(program,location));x.addProperty("status","complete");x.addProperty("pack",Iris.getCurrentPackName());x.addProperty("scope","Actual current named CUTOUT immediately before native GL multi-draw. Attached source/samplers/uniform threshold observed; not a GPU completion, rejected-fragment count, timing or equivalence proof.");
    Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(x),StandardOpenOption.CREATE_NEW);result=x;output=null;return;
   }
   if(GL20.glGetUniformLocation(program,"normals")<0||GL20.glGetUniformLocation(program,"specular")<0)return;
   var x=com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.bslMaterialState();x.addProperty("status","complete");x.addProperty("pack",Iris.getCurrentPackName());x.addProperty("scope","Actual bound native terrain submission with active normals/specular. Source establishes height-alpha consumer; actual POMwork coverage is joined separately. No speed or everymaterial claim.");Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(x),StandardOpenOption.CREATE_NEW);result=x;output=null;
  }catch(Exception e){result=new JsonObject();result.addProperty("status","failed");result.addProperty("error",e.toString());output=null;}
 }
 static JsonObject status(){var x=result==null?new JsonObject():result.deepCopy();if(!x.has("status"))x.addProperty("status",output==null?"idle":"pending");return x;}
}
