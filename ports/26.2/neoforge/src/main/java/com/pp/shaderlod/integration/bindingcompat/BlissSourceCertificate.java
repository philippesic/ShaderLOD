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
 public static final String RECIPE_SHA="b5280c3a5a19b7e6ab10349b884e6afb0fcb774fddc2c5874eced99098b2a3b3";
 private static final String ID="bliss-shader",RECIPE="assets/shaderlod/adapters/patches/adapters/bliss-shader.json";
 private BlissSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static Map<String,String> generatedOptions(){var m=new TreeMap<>(BlissQuadEligibility.nativeOptions());m.put("SHADERLOD_EFFECT_ADAPTER","1");m.put("SHADERLOD_EFFECT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");return Map.copyOf(m);}
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
  if(BlissQuadEligibility.validate(path,options).eligible())return selected;
  if(!generatedOptions().equals(options))throw new IllegalStateException("No certified Bliss source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);
   if(!BlissQuadEligibility.validate(original,BlissQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Original native463/source/API resources refused");
   if(selected.equals(requested))return new Result(BlissQuadEligibility.optionsMatch(options),"Original native463/TAA/HQ_CLOUDS/native density",false);
   if(!generatedOptions().equals(options))throw new IllegalStateException("Generated exact466 options refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",BlissQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   for(var e:recipe.getAsJsonObject("source_members").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Pinned source member mismatch");
   for(var item:recipe.getAsJsonArray("patches")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!recipe.getAsJsonObject("source_members").has(name))throw new IllegalStateException("Unpinned patch");String before=p.get("before").getAsString(),after=p.get("after").getAsString(),text=new String(expected.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");expected.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){if(expected.containsKey(e.getKey()))throw new IllegalStateException("Helper collision");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(e.getKey(),bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",BlissQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",sha(Files.readAllBytes(child(dir,selected))));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"1".equals(owned.get("SHADERLOD_EFFECT_ADAPTER").getAsString()))throw new IllegalStateException("State owned overrides changed");
   for(var e:state.getAsJsonObject("baseOptions").entrySet())if(!e.getValue().getAsString().equals(BlissQuadEligibility.nativeOptions().get(e.getKey())))throw new IllegalStateException("State original option choice changed");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated466 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
