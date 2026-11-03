package com.pp.shaderlod.integration.bindingcompat;
import com.google.gson.*;
import com.pp.shaderlod.integration.solasquad.SolasQuadEligibility;
import com.pp.shaderlod.packs.PackSelectionService;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.charset.StandardCharsets;
/** Pinned Solas NF26.2 source compatibility admission: exact original or actual maintained generated graph, no batching eligibility. */
public final class SolasSourceCertificate {
 public static final String RECIPE_SHA="11ab545e3c55cb59b683ce35600460b7fb1a72671a69a508841d47cabcd3726a";
 private static final String ID="solas-shader",RECIPE="assets/shaderlod/adapters/patches/adapters/solas-shader.json";
 private SolasSourceCertificate(){}
 public record Result(boolean eligible,String reason,boolean generated){}
 public static final String GENERATED_SOURCE_SHA="0ddecbc96a5521f1f519e44fe7aec5814bf03447fc07928ed49ce4c2691b4039";
 public static Map<String,String> generatedOptions(){if(SolasQuadEligibility.nativeOptions().size()!=428)return Map.of();var m=new TreeMap<>(SolasQuadEligibility.nativeOptions());m.put("SHADERLOD_PROJECTED_MATERIAL_BUDGET_INCLUDED","true");m.put("SHADERLOD_OPTIMIZATION_FLAGS_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_ADAPTER","1");m.put("SHADERLOD_REFINEMENT_ADAPTER_INCLUDED","true");m.put("SHADERLOD_REFINEMENT_BUDGET_INCLUDED","true");m.put("SHADERLOD_UNIFORM_SAMPLE_BUDGET_INCLUDED","true");m.put("SHADERLOD_SOLAS_DEPTH_PRIMING","0");return Map.copyOf(m);}
 public static boolean generatedOptionsMatch(Map<String,String> actual){return actual!=null&&actual.size()==435&&(generatedOptions().equals(actual)&&com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("solas-shader",actual)||com.pp.shaderlod.optimization.solasprefix.SolasPrefixProfile.accepts(actual));}
 public static boolean nativeOptionsMatch(Map<String,String> actual){if(actual==null||SolasQuadEligibility.nativeOptions().size()!=428)return false;var nativePom=new TreeMap<>(SolasQuadEligibility.nativeOptions());nativePom.put("ADVANCED_MATERIALS","true");nativePom.put("PARALLAX","true");return SolasQuadEligibility.nativeOptions().equals(actual)||nativePom.equals(actual);}
 private static Object inputResources()throws Exception{var pins=Map.of("source-proof.json","ee3b4986998f020b1ef1488bde7e7082ee617e7e1c0bbb1353e1e33bf6b58159","input-api.json","59f6baaf62c8989385c16abaa52c1e5bc023659ee8b3a2317b863703397bc15b","native428.json","261a3fbc6b6b6ff58a270ae4727ddec2c7ddcf7c642738c4454fa1f1b6eb4113");var hashes=new TreeMap<String,String>();for(var e:pins.entrySet()){String actual=sha(resource("assets/shaderlod/solasquad/"+e.getKey()));if(!actual.equals(e.getValue()))throw new IllegalStateException("Changed Solas native/input/API proof resource");hashes.put(e.getKey(),actual);}return Map.copyOf(hashes);}
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
  if(nativeOptionsMatch(options)&&SolasQuadEligibility.validate(path,SolasQuadEligibility.nativeOptions()).eligible())return selected;
  if(!generatedOptionsMatch(options))throw new IllegalStateException("No certified Solas source/options");
  return marker(path).get("originalFile").getAsString();
 }
 private static JsonObject marker(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var e=z.getEntry("shaderlod-selection.json");if(e==null)throw new IllegalStateException("Generated marker absent");try(var in=z.getInputStream(e)){return json(in.readAllBytes());}}}
 public static Result validate(Path directory,String selected,String requested,Map<String,String> options,boolean nativeReturnTransition){
  try{
   Path dir=directory.toAbsolutePath().normalize();if(requested==null||requested.isBlank())throw new IllegalStateException("Certified original required");Path original=child(dir,requested);inputResources();
   if(!SolasQuadEligibility.validate(original,SolasQuadEligibility.nativeOptions()).eligible())throw new IllegalStateException("Original native428/source/input/API resources refused");
   if(selected.equals(requested))return new Result(nativeOptionsMatch(options),"Original native428/exact source/input profile",false);
   if(!generatedOptionsMatch(options))throw new IllegalStateException("Generated exact435 options refused");
   byte[] recipeBytes=resource(RECIPE);if(!sha(recipeBytes).equals(RECIPE_SHA))throw new IllegalStateException("Stale/tampered packaged recipe");var recipe=json(recipeBytes);
   var source=archive(original);var expected=new TreeMap<String,byte[]>(source);var actual=archive(child(dir,selected));if(!GENERATED_SOURCE_SHA.equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(child(dir,selected)).memberSha256())))throw new IllegalStateException("Current generated source identity mismatch");var marker=json(actual.get("shaderlod-selection.json"));
   if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");equalsField(marker,"adapterId",ID);equalsField(marker,"originalFile",requested);equalsField(marker,"originalSha256",SolasQuadEligibility.ARCHIVE_SHA);equalsField(marker,"recipeSha256",RECIPE_SHA);
   for(var e:recipe.getAsJsonObject("source_members").entrySet())if(!sha(source.get(e.getKey())).equals(e.getValue().getAsString()))throw new IllegalStateException("Pinned source member mismatch");
   for(var item:recipe.getAsJsonArray("patches")){var p=item.getAsJsonObject();String name=p.get("path").getAsString();if(!recipe.getAsJsonObject("source_members").has(name))throw new IllegalStateException("Unpinned patch");String before=p.get("before").getAsString(),after=p.get("after").getAsString(),text=new String(expected.get(name),StandardCharsets.UTF_8);int at=text.indexOf(before);if(before.isEmpty()||at<0||text.indexOf(before,at+before.length())>=0)throw new IllegalStateException("Source anchor mismatch");expected.put(name,(text.substring(0,at)+after+text.substring(at+before.length())).getBytes(StandardCharsets.UTF_8));}
   for(var e:recipe.getAsJsonObject("additions").entrySet()){if(expected.containsKey(e.getKey()))throw new IllegalStateException("Helper collision");var pin=e.getValue().getAsJsonObject();byte[] bytes=resource("assets/shaderlod/adapters/"+pin.get("path").getAsString());if(!sha(bytes).equals(pin.get("sha256").getAsString()))throw new IllegalStateException("Helper pin mismatch");expected.put(e.getKey(),bytes);}
   var allowed=new HashSet<>(expected.keySet());allowed.add("shaderlod-selection.json");allowed.add("SHADERLOD_PRIVATE_SELECTION.md");if(!actual.keySet().equals(allowed))throw new IllegalStateException("Unexpected/missing generated members");
   for(var e:expected.entrySet())if(!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Generated member differs from maintained recipe: "+e.getKey());
   // All vertex/geometry stages remain original, including include bytes except the exact maintained fragment-only patches.
   for(var e:source.entrySet())if(e.getKey().endsWith(".vsh")&&!Arrays.equals(e.getValue(),actual.get(e.getKey())))throw new IllegalStateException("Changed vertex entry");
   var state=json(Files.readAllBytes(child(dir,marker.get("stateFile").getAsString())));if(state.get("schema").getAsInt()!=1)throw new IllegalStateException("State schema");equalsField(state,"stateFile",marker.get("stateFile").getAsString());equalsField(state,"adapterId",ID);equalsField(state,"originalFile",requested);equalsField(state,"originalSha256",SolasQuadEligibility.ARCHIVE_SHA);equalsField(state,"generatedFile",selected);equalsField(state,"generatedSha256",com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(child(dir,selected)));equalsField(state,"recipeSha256",RECIPE_SHA);
   var owned=state.getAsJsonObject("appliedOverrides");if(owned.size()!=1||!"1".equals(owned.get("SHADERLOD_REFINEMENT_ADAPTER").getAsString()))throw new IllegalStateException("State owned overrides changed");
   var base=new TreeMap<String,String>();for(var e:state.getAsJsonObject("baseOptions").entrySet())base.put(e.getKey(),e.getValue().getAsString());if(!com.pp.shaderlod.optimization.solasprefix.SolasPrefixProfile.storedBaseMatches(base,options))throw new IllegalStateException("State exact native428 choices differ from loaded generated435 profile");
   String note="Private local ShaderLOD adapter. Original: "+ID+". Upstream files and license retained. Do not redistribute this copy.\nRendering, quality, DH and PBR validation remain separate checks.\n";
   if(!Arrays.equals(note.getBytes(StandardCharsets.UTF_8),actual.get("SHADERLOD_PRIVATE_SELECTION.md")))throw new IllegalStateException("Generated provenance member changed");
   if(!state.get("active").getAsBoolean()&&!nativeReturnTransition)throw new IllegalStateException("Inactive generated state outside genuine native-return selection");
   return new Result(true,"Exact maintained generated435 source/state/unchanged vertex entries; compatibility only",true);
  }catch(Exception|LinkageError failure){return new Result(false,failure.toString(),false);}
 }
}
