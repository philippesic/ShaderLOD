package com.pp.shaderlod.integration.bindingcompat;
import com.google.gson.*;
import com.pp.shaderlod.integration.blissquad.BlissQuadEligibility;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
/** Pinned Bliss NF26.2 source compatibility admission: exact original or actual maintained generated graph, no batching eligibility. */
public final class BlissSourceCertificate {
 public static final String RECIPE_SHA="fc63141d1952018fdab645074d728cff98108da7a247730c21372b5517e023dd";
 private static final String ID="bliss-shader",RECIPE="assets/shaderlod/adapters/patches/adapters/bliss-shader.json";
 private BlissSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static Map<String,String> generatedOptions(){var m=new TreeMap<>(BlissQuadEligibility.nativeOptions());m.put("SHADERLOD_EFFECT_ADAPTER","1");m.put("SHADERLOD_BLISS_PIXEL_GRAPH","0");m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED","true");m.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");m.put("SHADERLOD_BLISS_DEPTH_PRIMING","0");return Map.copyOf(m);}

 /** Named exact SSR profile; no arbitrary native option normalization or batching admission. */
 public static Map<String,String> nativeSsrOptions(){var m=new TreeMap<>(BlissQuadEligibility.nativeOptions());m.put("Screen_Space_Reflections","true");m.put("Specular_Reflections","true");return Map.copyOf(m);}
 public static Map<String,String> generatedSsrOptions(){var m=new TreeMap<>(generatedOptions());m.put("Screen_Space_Reflections","true");m.put("Specular_Reflections","true");return Map.copyOf(m);}
 /** Binding source/input compatibility only. Original renderer/batching pins stay unchanged. */
 public static Map<String,String> nativeCloudFogOptions(){var m=new TreeMap<>(BlissQuadEligibility.nativeOptions());m.put("RAYMARCH_CLOUDS_WITH_FOG","true");return Map.copyOf(m);}
 public static boolean optionsMatchKnownNative(Map<String,String> options){
  if(options==null||options.size()!=463)return false;
  if(BlissQuadEligibility.nativeOptions().size()==463&&nativeCloudFogOptions().equals(options))return true;
  if(BlissQuadEligibility.nativeOptions().size()==463&&nativeSsrOptions().equals(options))return true;
  if(!options.containsKey("POM")||!options.containsKey("indirect_effect"))return false;
  if(!Set.of("false","true").contains(options.get("POM"))||
     !Set.of("0","1","2").contains(options.get("indirect_effect")))return false;
  try{
   int lo=Integer.parseInt(options.get("minRayMarchSteps")),hi=Integer.parseInt(options.get("maxRayMarchSteps"));
   if(!Integer.toString(lo).equals(options.get("minRayMarchSteps"))||!Integer.toString(hi).equals(options.get("maxRayMarchSteps"))||
      !(lo==15||(lo>=20&&lo<=200&&lo%5==0))||!(hi>=5&&hi<=200&&hi%5==0))return false;
  }catch(Exception invalid){return false;}
  var base=new TreeMap<>(options);
  for(String key:List.of("POM","indirect_effect","minRayMarchSteps","maxRayMarchSteps"))base.put(key,BlissQuadEligibility.nativeOptions().get(key));
  return BlissQuadEligibility.nativeOptions().size()==463&&base.equals(BlissQuadEligibility.nativeOptions());
 }
 public static Map<String,String> generatedOptions(Map<String,String> nativeChoices){
  if(!optionsMatchKnownNative(nativeChoices))throw new IllegalArgumentException("Unpinned Bliss native choices");
  var result=new TreeMap<>(generatedOptions());
  if(nativeCloudFogOptions().equals(nativeChoices))result.put("RAYMARCH_CLOUDS_WITH_FOG","true");
  if(nativeSsrOptions().equals(nativeChoices)){result.put("Screen_Space_Reflections","true");result.put("Specular_Reflections","true");}
  for(String key:List.of("POM","indirect_effect","minRayMarchSteps","maxRayMarchSteps"))result.put(key,nativeChoices.get(key));
  return Map.copyOf(result);
 }
 public static boolean optionsMatchGenerated(Map<String,String> options){
  if(options==null||options.size()!=generatedOptions().size())return false;
  if("true".equals(options.get("Screen_Space_Reflections"))&&!"0".equals(options.get("SHADERLOD_BLISS_PIXEL_GRAPH")))return false;
  var nativeChoices=new TreeMap<>(options);
  for(String key:generatedOptions().keySet())if(!BlissQuadEligibility.nativeOptions().containsKey(key)){
   if(key.equals("SHADERLOD_BLISS_PIXEL_GRAPH")){if(!Set.of("0","1").contains(options.get(key)))return false;}else if(!Objects.equals(options.get(key),generatedOptions().get(key)))return false;nativeChoices.remove(key);
  }
  return optionsMatchKnownNative(nativeChoices);
 }
 private static boolean knownBaseChoice(String key,String value){
  var choices=new TreeMap<>(BlissQuadEligibility.nativeOptions());
  if(!choices.containsKey(key))return false;choices.put(key,value);return optionsMatchKnownNative(choices);
 }
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Path child(Path dir,String name)throws Exception{Path p=dir.resolve(name).normalize();if(!Path.of(name).getFileName().toString().equals(name)||!p.startsWith(dir)||p.equals(dir)||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Unsafe/missing source/state child");return p;}
 private static byte[] resource(String path)throws Exception{try(var in=PackSelectionService.class.getClassLoader().getResourceAsStream(path)){if(in==null)throw new IllegalStateException("Missing packaged recipe/helper: "+path);return in.readAllBytes();}}
 private static Map<String,byte[]> archive(Path path)throws Exception{var m=new TreeMap<String,byte[]>();try(var z=new ZipFile(path.toFile())){var entries=z.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(e.isDirectory())continue;String n=e.getName();if(n.startsWith("/")||n.contains("\\")||Arrays.asList(n.split("/")).contains("..")||m.containsKey(n))throw new IllegalStateException("Unsafe/duplicate archive member");try(var in=z.getInputStream(e)){m.put(n,in.readAllBytes());}}}return m;}
 private static JsonObject json(byte[] b){return JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}
 private static void equalsField(JsonObject o,String key,String expected){if(!expected.equals(o.get(key).getAsString()))throw new IllegalStateException("Source/state field mismatch: "+key);}
 /** Actual byte stamps, used once per world tick in explicit untimed mode; metadata replay cannot hide tampering. */
 public static Object stamp(Path dir,String selected,String original)throws Exception{
  Path a=child(dir,selected),b=child(dir,original);String as=com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(a),bs=a.equals(b)?as:com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(b);
  if(a.equals(b))return List.of(as,bs);
  var marker=marker(a);Path state=child(dir,marker.get("stateFile").getAsString());return List.of(as,bs,com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(state));
 }
 public static String originalName(Path directory,String selected,Map<String,String> options)throws Exception{
  Path dir=directory.toAbsolutePath().normalize(),path=child(dir,selected);
  if(optionsMatchKnownNative(options)&&BlissQuadEligibility.validate(path,BlissQuadEligibility.nativeOptions()).eligible())return selected;
  if(!optionsMatchGenerated(options))throw new IllegalStateException("No certified Bliss source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);
   if(!BlissQuadEligibility.validate(original,BlissQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Original native463/source/API resources refused");
   if(selected.equals(requested))return new Result(optionsMatchKnownNative(options),"Original native463/TAA/HQ_CLOUDS/native density",false);
   if(!optionsMatchGenerated(options))throw new IllegalStateException("Generated exact469/explicit native choices refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",BlissQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   for(var e:recipe.getAsJsonObject("source_members").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Pinned source member mismatch");
   for(var item:recipe.getAsJsonArray("patches")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!recipe.getAsJsonObject("source_members").has(name))throw new IllegalStateException("Unpinned patch");String before=p.get("before").getAsString(),after=p.get("after").getAsString(),text=new String(expected.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");expected.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){if(expected.containsKey(e.getKey()))throw new IllegalStateException("Helper collision");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(e.getKey(),bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // Exact recipe postimages above cover pinned projection-only vertex changes; unpinned vertex changes always refuse. Native attribute/layout proof is delivered with this recipe.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey()))&&!recipe.getAsJsonObject("source_members").has(e.getKey()))throw new IllegalStateException("Unpinned changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",BlissQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(child(dir,selected)));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"1".equals(owned.get("SHADERLOD_EFFECT_ADAPTER").getAsString()))throw new IllegalStateException("State owned overrides changed");
   var storedBase=new TreeMap<String,String>();state.getAsJsonObject("baseOptions").entrySet().forEach(e->storedBase.put(e.getKey(),e.getValue().getAsString()));
   if(!nativeSsrOptions().equals(storedBase)){for(var e:state.getAsJsonObject("baseOptions").entrySet())if(!knownBaseChoice(e.getKey(),e.getValue().getAsString()))throw new IllegalStateException("State original option choice changed");}
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated469 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
