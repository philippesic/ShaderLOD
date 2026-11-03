package com.pp.shaderlod.integration.bindingcompat;
import com.google.gson.*;
import com.pp.shaderlod.integration.astraquad.AstraQuadEligibility;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
/** Pinned AstraLex NF26.2 source compatibility admission: exact original or actual maintained generated graph, no batching eligibility. */
public final class AstraSourceCertificate {
 public static final String RECIPE_SHA="7965fc2930f80491528a91aca3307b342dc333064ee47c382cf796e7f69a2be6";
 private static final String ID="astralex",RECIPE="assets/shaderlod/adapters/patches/adapters/astralex.json";
 private AstraSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static final String GENERATED_SOURCE_SHA="9c29b0a9e3b5d69d9abb38702f9c1fa92472ffb5a7d8ed9e6add65f72b476f88";
 public static Map<String,String> generatedOptions(){if(AstraQuadEligibility.nativeOptions().size()!=1015)return Map.of();var m=new TreeMap<>(AstraQuadEligibility.nativeOptions());m.put("SHADERLOD_ASTRALEX_WORLD_GRAPH","0");m.put("SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","0");m.put("SHADERLOD_ASTRALEX_AO_HALF_RES","0");m.put("SHADERLOD_RECEIVER_FOOTPRINT_BUDGET_R1_INCLUDED","true");m.put("SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN","0");m.put("SHADERLOD_ASTRALEX_PROJECTED_POM_INCLUDED","true");m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");m.put("SHADERLOD_ASTRALEX_DEPTH_PRIMING","0");return Map.copyOf(m);}
 public static boolean generatedOptionsMatch(Map<String,String> actual){if(actual==null||actual.size()!=1027||!Set.of("0","1").contains(actual.getOrDefault("SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN","invalid")))return false;var normalized=new TreeMap<>(actual);for(String key:List.of("SHADERLOD_ASTRALEX_WORLD_GRAPH","SHADERLOD_ASTRALEX_DYNAMIC_GAUSSIAN","SHADERLOD_ASTRALEX_DEAD_MIP_CHAIN")){if(!Set.of("0","1").contains(normalized.getOrDefault(key,"invalid")))return false;normalized.put(key,"0");}return generatedOptions().equals(normalized)&&com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("astralex",normalized);}
 private static Object inputResources()throws Exception{var pins=Map.of("source-proof.json","9f9383722ca89408a4f7bdb62817973fdcce9a7b7a046e7fdbf14c50d3ccd8de","input-api.json","59f6baaf62c8989385c16abaa52c1e5bc023659ee8b3a2317b863703397bc15b","native1015.json","298347d107f05379e2ab255059f768cc5542ab215ccfaad5a9c121c8778fba71");var hashes=new TreeMap<String,String>();for(var e:pins.entrySet()){String actual=sha(resource("assets/shaderlod/astraquad/"+e.getKey()));if(!actual.equals(e.getValue()))throw new IllegalStateException("Changed Astra native/input/API proof resource");hashes.put(e.getKey(),actual);}return Map.copyOf(hashes);}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}
 private static Path child(Path dir,String name)throws Exception{Path p=dir.resolve(name).normalize();if(!Path.of(name).getFileName().toString().equals(name)||!p.startsWith(dir)||p.equals(dir)||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Unsafe/missing source/state child");return p;}
 private static byte[] resource(String path)throws Exception{try(var in=PackSelectionService.class.getClassLoader().getResourceAsStream(path)){if(in==null)throw new IllegalStateException("Missing packaged recipe/helper: "+path);return in.readAllBytes();}}
 private static Map<String,byte[]> archive(Path path)throws Exception{var m=new TreeMap<String,byte[]>();try(var z=new ZipFile(path.toFile())){var entries=z.entries();while(entries.hasMoreElements()){var e=entries.nextElement();if(e.isDirectory())continue;String n=e.getName();if(n.startsWith("/")||n.contains("\\")||Arrays.asList(n.split("/")).contains("..")||m.containsKey(n))throw new IllegalStateException("Unsafe/duplicate archive member");try(var in=z.getInputStream(e)){m.put(n,in.readAllBytes());}}}return m;}
 private static JsonObject json(byte[] b){return JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}
 private static void equalsField(JsonObject o,String key,String expected){if(!expected.equals(o.get(key).getAsString()))throw new IllegalStateException("Source/state field mismatch: "+key);}
 /** Full archive/state and exact input/recipe/helper stamps on the existing50ms consumer cadence. */
 public static Object stamp(Path dir,String selected,String original)throws Exception{
  Path a=child(dir,selected),b=child(dir,original);String as=com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(a),bs=a.equals(b)?as:com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(b);
  Object proofs=inputResources();if(a.equals(b))return List.of(as,bs,proofs);
  var marker=marker(a);Path state=child(dir,marker.get("stateFile").getAsString());byte[] recipe=resource(RECIPE);if(!sha(recipe).equals(RECIPE_SHA))throw new IllegalStateException("Changed recipe resource");var helpers=new TreeMap<String,String>();for(var e:json(recipe).getAsJsonObject("additions").entrySet()){var pin=e.getValue().getAsJsonObject();String actual=sha(resource("assets/shaderlod/adapters/"+pin.get("path").getAsString()));if(!actual.equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Changed helper resource");helpers.put(e.getKey(),actual);}return List.of(as,bs,com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(state),proofs,sha(recipe),Map.copyOf(helpers));
 }
 public static String originalName(Path directory,String selected,Map<String,String> options)throws Exception{
  Path dir=directory.toAbsolutePath().normalize(),path=child(dir,selected);
  if(AstraQuadEligibility.validate(path,options).eligible())return selected;
  if(!generatedOptionsMatch(options))throw new IllegalStateException("No certified Astra source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);inputResources();
   if(!AstraQuadEligibility.validate(original,AstraQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Original native1015/source/input/API resources refused");
   if(selected.equals(requested))return new Result(AstraQuadEligibility.nativeOptions().equals(options),"Original native1015/exact source/input profile",false);
   if(!generatedOptionsMatch(options))throw new IllegalStateException("Generated exact1027 options refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));if(!GENERATED_SOURCE_SHA.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(child(dir,selected)).memberSha256())))throw new IllegalStateException("Current generated source identity mismatch");var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",AstraQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   for(var e:recipe.getAsJsonObject("source_members").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Pinned source member mismatch");
   for(var item:recipe.getAsJsonArray("patches")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!recipe.getAsJsonObject("source_members").has(name))throw new IllegalStateException("Unpinned patch");String before=p.get("before").getAsString(),after=p.get("after").getAsString(),text=new String(expected.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");expected.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){if(expected.containsKey(e.getKey()))throw new IllegalStateException("Helper collision");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(e.getKey(),bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",AstraQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(child(dir,selected)));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"1".equals(owned.get("SHADERLOD_REFINEMENT_ADAPTER").getAsString()))throw new IllegalStateException("State owned overrides changed");
   var base=new TreeMap<String,String>();for(var e:state.getAsJsonObject("baseOptions").entrySet())base.put(e.getKey(),e.getValue().getAsString());if(!AstraQuadEligibility.nativeOptions().equals(base))throw new IllegalStateException("State original complete1015 option choices changed");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated1027 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
