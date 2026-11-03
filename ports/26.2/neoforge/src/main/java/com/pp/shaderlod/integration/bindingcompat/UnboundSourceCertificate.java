package com.pp.shaderlod.integration.bindingcompat;
import com.google.gson.*;
import com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
/** Pinned Unbound NF26.2 source compatibility admission: exact original or actual maintained generated graph, no batching eligibility. */
public final class UnboundSourceCertificate {
 public static final String RECIPE_SHA="d19db58ed1901563a7ad064d3a49c175afb29fd1e46062c08db08929bf0a51fa";
 private static final String ID="complementary-unbound",RECIPE="assets/shaderlod/adapters/patches/adapters/complementary-unbound.json";
 private UnboundSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static Map<String,String> generatedOptions(){var m=new TreeMap<>(UnboundQuadEligibility.nativeOptions());m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_UNBOUND_DEPTH_PRIMING","0");m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED","true");m.put("SHADERLOD_PROJECTED_POM_ADAPTER_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");return Map.copyOf(m);}
 public static boolean generatedOptionsMatch(Map<String,String> options){if(options==null)return false;String v=options.get("SHADERLOD_UNBOUND_DEPTH_PRIMING");if(v==null||!Set.of("0","1").contains(v))return false;var m=new TreeMap<>(options);m.put("SHADERLOD_UNBOUND_DEPTH_PRIMING","0");return generatedOptions().equals(m)||com.pp.shaderlod.optimization.unboundprefix.UnboundPrefixProfile.accepts(options);}
 public static boolean nativeOptionsMatch(Map<String,String>a){return UnboundQuadEligibility.optionsMatch(a)||com.pp.shaderlod.optimization.unboundprefix.UnboundPrefixProfile.nativeAccepts(a);}
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
  if(nativeOptionsMatch(options)&&UnboundQuadEligibility.validate(path,UnboundQuadEligibility.nativeOptions()).eligible())return selected;
  if(!generatedOptionsMatch(options))throw new IllegalStateException("No certified Unbound source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);
   if(!UnboundQuadEligibility.validate(original,UnboundQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Original native398/source/API resources refused");
   if(selected.equals(requested))return new Result(nativeOptionsMatch(options),"Original native398/TAA/style4/RP1",false);
   if(!generatedOptionsMatch(options))throw new IllegalStateException("Generated exact406 options refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",UnboundQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   for(var e:recipe.getAsJsonObject("source_members").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Pinned source member mismatch");
   for(var item:recipe.getAsJsonArray("patches")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!recipe.getAsJsonObject("source_members").has(name))throw new IllegalStateException("Unpinned patch");String before=p.get("before").getAsString(),after=p.get("after").getAsString(),text=new String(expected.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");expected.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){if(expected.containsKey(e.getKey()))throw new IllegalStateException("Helper collision");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(e.getKey(),bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",UnboundQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(child(dir,selected)));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"1".equals(owned.get("SHADERLOD_REFINEMENT_ADAPTER").getAsString()))throw new IllegalStateException("State owned overrides changed");
   var stored=new TreeMap<String,String>();for(var e:state.getAsJsonObject("baseOptions").entrySet())stored.put(e.getKey(),e.getValue().getAsString());if(!com.pp.shaderlod.optimization.unboundprefix.UnboundPrefixProfile.storedBaseMatches(stored,options))throw new IllegalStateException("State exactnative398 choices differ from loaded406 profile");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated406 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
