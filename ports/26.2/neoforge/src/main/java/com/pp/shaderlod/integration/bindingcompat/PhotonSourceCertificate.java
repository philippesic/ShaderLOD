package com.pp.shaderlod.integration.bindingcompat;
import com.google.gson.*;
import com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
/** Pinned Photon NF26.2 source compatibility admission: exact original or actual maintained generated graph, no batching eligibility. */
public final class PhotonSourceCertificate {
 public static final String RECIPE_SHA="1d6bd267a12adf7727a8941731a653c23483f4fdd40dec63a648aa85618d15d6";
 private static final String ID="photon",RECIPE="assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json";
 private PhotonSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static final String GENERATED_OPTIONS_SHA="ab0aa5f8281702b2a1ae3750d193821f70c17e39de3d47e55bee26b67e307e1c";
 private static final Map<String,String> GENERATED_OPTIONS=loadGeneratedOptions();
 private static Map<String,String> loadGeneratedOptions(){try{byte[] bytes=resource("assets/shaderlod/photonquad/generated394.json");if(!sha(bytes).equals(GENERATED_OPTIONS_SHA))return Map.of();var m=new TreeMap<String,String>();for(var e:json(bytes).entrySet()){if(!e.getValue().isJsonPrimitive()||!e.getValue().getAsJsonPrimitive().isString())return Map.of();m.put(e.getKey(),e.getValue().getAsString());}if(m.size()!=395)return Map.of();for(var e:configuredOptions().entrySet())if(!e.getValue().equals(m.get(e.getKey())))return Map.of();return Map.copyOf(m);}catch(Exception|LinkageError unavailable){return Map.of();}}
 public static Map<String,String> generatedOptions(){return GENERATED_OPTIONS;}
 /** Only declared source-owned compile selectors may vary; all native choices remain exact. */
 public static boolean generatedOptionsMatch(Map<String,String> options){return profileGeneratedMatch(GENERATED_OPTIONS,options)||profileGeneratedMatch(LIGHTING_GENERATED,options);}
 private static boolean profileGeneratedMatch(Map<String,String> expected,Map<String,String> options){if(expected==null||expected.size()!=395||!options.keySet().equals(expected.keySet()))return false;for(var e:expected.entrySet()){String v=options.get(e.getKey());boolean allowed=switch(e.getKey()){case "SHADERLOD_PHOTON_DEPTH_PRIMING","SHADERLOD_PHOTON_DEAD_MIP_CHAIN"->Set.of("0","1").contains(v);case "SHADERLOD_SHARED_SPATIAL","SHADERLOD_LIGHTING_RATE_PROBE"->Set.of("false","true").contains(v);default->e.getValue().equals(v);};if(!allowed)return false;}return true;}

 public static Map<String,String> configuredOptions(){var m=new TreeMap<>(PhotonQuadEligibility.nativeOptions());if(m.size()!=366)throw new IllegalStateException("Pinned Photon native map unavailable");m.put("TAAU_RENDER_SCALE","1.00");return Map.copyOf(m);}
 private static final Map<String,String> LIGHTING_NATIVE=loadProfile("eligible-native366.json","55aca351f8f634b2dd777d5763c2837921285d1841a4121090c6decbc0b12457",366);
 private static final Map<String,String> LIGHTING_GENERATED=loadProfile("eligible-generated394.json","2777acc1c8b1865fd4e765ca2858d71e8fe3ab1754d3c1469072e97e0c4fbb25",395);
 private static Map<String,String> loadProfile(String name,String pin,int count){try{byte[] bytes=resource("assets/shaderlod/photonspatial/"+name);if(!sha(bytes).equals(pin))return Map.of();var m=new TreeMap<String,String>();for(var e:json(bytes).entrySet())m.put(e.getKey(),e.getValue().getAsString());return m.size()==count?Map.copyOf(m):Map.of();}catch(Exception|LinkageError missing){return Map.of();}}
 public static Map<String,String> lightingOptions(){return LIGHTING_NATIVE;}
 public static Map<String,String> lightingGeneratedOptions(){return LIGHTING_GENERATED;}
 public static boolean optionsMatch(Map<String,String> options){return configuredOptions().equals(options)||(LIGHTING_NATIVE.size()==366&&LIGHTING_NATIVE.equals(options));}
 public static boolean originalSource(Path path){return PhotonQuadEligibility.validate(path,PhotonQuadEligibility.nativeOptions()).eligible();}
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
  if(optionsMatch(options)&&originalSource(path))return selected;
  if(!generatedOptionsMatch(options))throw new IllegalStateException("No certified Photon source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static void replace(Map<String,byte[]> files,String name,String before,String after){String text=new String(files.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");files.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);
   if(!originalSource(original))throw new IllegalStateException("Original configured366/source/API resources refused");
   if(selected.equals(requested))return new Result(optionsMatch(options),"Original configured366/TAA/native TAAU off/latent1.00/SH_SKYLIGHT off",false);
   if(!generatedOptionsMatch(options))throw new IllegalStateException("Generated declared394/native366 options refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",PhotonQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   if(!sha(source.get("LICENSE")).equals(recipe.get("license_sha256").getAsString()))throw new IllegalStateException("License ancestry mismatch");
   for(var e:recipe.getAsJsonObject("source_dependencies").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Source dependency mismatch: "+e.getKey());
   for(var item:recipe.getAsJsonArray("members")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!sha(source.get(name)).equals(p.get("source_sha256").getAsString()))throw new IllegalStateException("Pinned member mismatch");
    for(var replacement:p.getAsJsonArray("replacements")){var r=replacement.getAsJsonObject();replace(expected,name,r.get("before").getAsString(),r.get("after").getAsString());}
    if(!sha(expected.get(name)).equals(p.get("patched_sha256").getAsString()))throw new IllegalStateException("Patched member mismatch");
    if(name.equals("shaders/settings.glsl")){replace(expected,name,"//#define SHADERLOD_LIVE_PIXEL_BUDGET\n","#define SHADERLOD_LIVE_PIXEL_BUDGET\n");if(!sha(expected.get(name)).equals(p.get("enabled_sha256").getAsString()))throw new IllegalStateException("Enabled settings mismatch");}
   }
   for(String key:List.of("addition","common_addition")){var pin=recipe.getAsJsonObject(key);String name=pin.get("path").getAsString();if(expected.containsKey(name))throw new IllegalStateException("Helper collision");byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("source_path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(name,bytes);}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){String name=e.getKey();if(!name.startsWith("shaders/")||name.contains("\\")||Arrays.asList(name.split("/")).contains("..")||expected.containsKey(name))throw new IllegalStateException("Unsafe/colliding additional member");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Additional helper/program pin mismatch: "+name);expected.put(name,bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!e.getKey().equals("shaders/program/gbuffers_all_solid.vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   if(!com.pp.shaderlod.integration.makeupquad.NamedVertexContract.accepts(ID,source,actual))throw new IllegalStateException("Exact Photon invariant vertex closure rejected");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",PhotonQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(child(dir,selected)));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"true".equals(owned.get("SHADERLOD_LIVE_PIXEL_BUDGET").getAsString()))throw new IllegalStateException("State owned overrides changed");
   var baseOptions=new TreeMap<String,String>();for(var e:state.getAsJsonObject("baseOptions").entrySet())baseOptions.put(e.getKey(),e.getValue().getAsString());if(!optionsMatch(baseOptions))throw new IllegalStateException("State original complete366 choices changed");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated394 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
