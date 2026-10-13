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
 public static final String RECIPE_SHA="192a63174c76730d462fc091156eb7cdee80f79cc8a6c6aba4db176c5b47dd17";
 private static final String ID="photon",RECIPE="assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json";
 private PhotonSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static Map<String,String> generatedOptions(){var m=new TreeMap<>(configuredOptions());m.put("SHADERLOD_LIVE_PIXEL_BUDGET","true");m.put("SHADERLOD_LIVE_PIXEL_BUDGET_INCLUDED","true");m.put("SHADERLOD_RENDER_BUDGET_INCLUDED","true");return Map.copyOf(m);}
 public static Map<String,String> configuredOptions(){var m=new TreeMap<>(PhotonQuadEligibility.nativeOptions());if(m.size()!=366)throw new IllegalStateException("Pinned Photon native map unavailable");m.put("TAAU_RENDER_SCALE","1.00");return Map.copyOf(m);}
 public static boolean optionsMatch(Map<String,String> options){return configuredOptions().equals(options);}
 public static boolean originalSource(Path path){return PhotonQuadEligibility.validate(path,PhotonQuadEligibility.nativeOptions()).eligible();}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Path child(Path dir,String name)throws Exception{Path p=dir.resolve(name).normalize();if(!Path.of(name).getFileName().toString().equals(name)||!p.startsWith(dir)||p.equals(dir)||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Unsafe/missing source/state child");return p;}
 private static byte[] resource(String path)throws Exception{try(var in=PackSelectionService.class.getClassLoader().getResourceAsStream(path)){if(in==null)throw new IllegalStateException("Missing packaged recipe/helper: "+path);return in.readAllBytes();}}
 private static Map<String,byte[]> archive(Path path)throws Exception{var m=new TreeMap<String,byte[]>();try(var z=new ZipFile(path.toFile())){var entries=z.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(e.isDirectory())continue;String n=e.getName();if(n.startsWith("/")||n.contains("\\")||Arrays.asList(n.split("/")).contains("..")||m.containsKey(n))throw new IllegalStateException("Unsafe/duplicate archive member");try(var in=z.getInputStream(e)){m.put(n,in.readAllBytes());}}}return m;}
 private static JsonObject json(byte[] b){return JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}
 private static void equalsField(JsonObject o,String key,String expected){if(!expected.equals(o.get(key).getAsString()))throw new IllegalStateException("Source/state field mismatch: "+key);}
 /** Actual byte stamps, used once per world tick in explicit untimed mode; metadata replay cannot hide tampering. */
 public static Object stamp(Path dir,String selected,String original)throws Exception{
  Path a=child(dir,selected),b=child(dir,original);String as=sha(Files.readAllBytes(a)),bs=a.equals(b)?as:sha(Files.readAllBytes(b));
  if(a.equals(b))return List.of(as,bs);
  var marker=marker(a);Path state=child(dir,marker.get("stateFile").getAsString());return List.of(as,bs,sha(Files.readAllBytes(state)));
 }
 public static String originalName(Path directory,String selected,Map<String,String> options)throws Exception{
  Path dir=directory.toAbsolutePath().normalize(),path=child(dir,selected);
  if(optionsMatch(options)&&originalSource(path))return selected;
  if(!generatedOptions().equals(options))throw new IllegalStateException("No certified Photon source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static void replace(Map<String,byte[]> files,String name,String before,String after){String text=new String(files.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");files.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);
   if(!originalSource(original))throw new IllegalStateException("Original configured366/source/API resources refused");
   if(selected.equals(requested))return new Result(optionsMatch(options),"Original configured366/TAA/native TAAU off/latent1.00/SH_SKYLIGHT off",false);
   if(!generatedOptions().equals(options))throw new IllegalStateException("Generated exact369 options refused");
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
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",PhotonQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",sha(Files.readAllBytes(child(dir,selected))));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"true".equals(owned.get("SHADERLOD_LIVE_PIXEL_BUDGET").getAsString()))throw new IllegalStateException("State owned overrides changed");
   for(var e:state.getAsJsonObject("baseOptions").entrySet())if(!e.getValue().getAsString().equals(configuredOptions().get(e.getKey())))throw new IllegalStateException("State original option choice changed");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated369 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
