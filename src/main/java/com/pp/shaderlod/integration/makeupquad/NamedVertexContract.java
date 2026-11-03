package com.pp.shaderlod.integration.makeupquad;
import com.google.gson.*;import java.util.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.regex.*;
/** Named exact source closures whose actual native/generated active vertex code was checked with Iris.
 * No GLSL rewriting or runtime token suppression. Runtime API/stack/resource/preset/world guards remain required. */
public final class NamedVertexContract {
 private static final String SHA="a7e0323f6526057fbe6510d94be1576a1ca2a869ba3ddc24aab36e267e1d3dfe";
 private static final String PHOTON_UNUSED_BLOCK="387927d5265142bd6205834d6ba24136871fcb64e1bd28fea9226b2d4f9b4905";
 private static final JsonObject PROFILES=load();
 private NamedVertexContract() {}
 private static JsonObject load() {
  try(var in=NamedVertexContract.class.getResourceAsStream("/assets/shaderlod/rendererquad/named-vertex-contracts.json")) {
   if(in==null)return new JsonObject();byte[] b=in.readAllBytes();if(!SHA.equals(MakeUpQuadEligibility.sha(b)))return new JsonObject();return JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();
  }catch(Exception|LinkageError unavailable){return new JsonObject();}
 }
 private static final Pattern INCLUDE=Pattern.compile("(?m)^\\s*#\\s*include\\s+[\"<]([^\">]+)[\">]\\s*(?://[^\\r\\n]*)?$");
 private static boolean stage(String p) {return p.startsWith("shaders/")&&Path.of(p).getFileName().toString().startsWith("gbuffers_")&&p.matches(".*\\.(vsh|gsh|tcs|tes)$");}
 private static Set<String> roots(Map<String,byte[]> files) {var roots=new TreeSet<String>();files.keySet().stream().filter(NamedVertexContract::stage).forEach(roots::add);return roots;}
 private static void visit(String path,Map<String,byte[]> files,Map<String,String> closure)throws Exception {
  if(closure.containsKey(path))return;byte[] b=files.get(path);if(b==null)throw new IllegalArgumentException("Missing include");closure.put(path,MakeUpQuadEligibility.sha(b));String text=new String(b,StandardCharsets.UTF_8);var m=INCLUDE.matcher(text);int count=0;
  while(m.find()){count++;String p=m.group(1);if(p.indexOf('\\')>=0)throw new IllegalArgumentException("Non-archive include separator");Path resolved=p.startsWith("/")?Path.of("shaders"+p):Path.of(path).getParent().resolve(p);String next=resolved.normalize().toString().replace(java.io.File.separatorChar,'/');if(!next.startsWith("shaders/"))throw new IllegalArgumentException("Escaped include");visit(next,files,closure);}
  if(count!=Pattern.compile("(?m)^\\s*#\\s*include\\b").matcher(text).results().count())throw new IllegalArgumentException("Unproved include");
 }
 private static Map<String,String> closure(Map<String,byte[]> files)throws Exception {var r=new TreeMap<String,String>();for(var p:roots(files))visit(p,files,r);return r;}
 private static Map<String,String> map(JsonObject obj){var r=new TreeMap<String,String>();obj.entrySet().forEach(e->r.put(e.getKey(),e.getValue().getAsString()));return r;}
 public static boolean accepts(String family,Map<String,byte[]> original,Map<String,byte[]> generated) {
  try {var profile=PROFILES.getAsJsonObject(family);if(profile==null)return false;
   boolean exact=profile.get("exact_active_code").getAsBoolean();
   boolean unusedPhoton="photon".equals(family)&&profile.has("proof_kind")&&("photon-invariant-depth-v1".equals(profile.get("proof_kind").getAsString())&&profile.has("depth_invariant_semantics_sha256")&&"08d97155fa32520ed677ffd64dc7629af2ddf08b14e53318c00a0d69246961e6".equals(profile.get("depth_invariant_semantics_sha256").getAsString()))&&
     profile.has("native_entry_behavior_exact")&&profile.get("native_entry_behavior_exact").getAsBoolean()&&
     profile.has("unused_addition_sha256")&&PHOTON_UNUSED_BLOCK.equals(profile.get("unused_addition_sha256").getAsString());
   boolean indexedBliss="bliss-shader".equals(family)&&profile.has("proof_kind")&&"bliss-indexed-uniform-postscale-v1".equals(profile.get("proof_kind").getAsString())&&
     profile.has("indexed_vertex_behavior_preserved")&&profile.get("indexed_vertex_behavior_preserved").getAsBoolean()&&
     profile.has("clip_postscale_sha256")&&"f3d2e1aff5972303ddbb343ad43442fea005969edf7e4aee451203e598c46f69".equals(profile.get("clip_postscale_sha256").getAsString());
   boolean unusedAstra="astralex".equals(family)&&profile.has("proof_kind")&&"astra-unused-bridge-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("native_entry_behavior_exact").getAsBoolean()&&"2ee948fa2b7e4f8bd777e7dd6c77dc031ea6d9b129ff2735b0942bf59020ac75".equals(profile.get("unused_addition_sha256").getAsString());
   boolean astraDepth="astralex".equals(family)&&profile.has("proof_kind")&&"astra-depth-subset-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("native_vertex_math_preserved").getAsBoolean()&&profile.get("private0_native_vertex_exact").getAsBoolean()&&profile.get("private1_adds_only_material_output_and_invariants").getAsBoolean()&&"c0c54dff186381cf787f568eec3e660df2a860d9ea38dbecb3c77c9492e72303".equals(profile.get("private_depth_helper_sha256").getAsString());
   boolean astraSpatial="astralex".equals(family)&&profile.has("proof_kind")&&"astra-depth-spatial-subset-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("native_vertex_math_preserved").getAsBoolean()&&profile.get("adapter0_native_vertex_exact").getAsBoolean()&&profile.get("adapter1_adds_only_raw_material_and_invariants").getAsBoolean()&&profile.get("native_fragment_phase0_preserved").getAsBoolean()&&profile.get("same_linked_native_program_both_roles").getAsBoolean()&&"17dacec42f72f0bfdb5ebc3156871e3ce88f55dba34c20dc6c114acabcd3fb10".equals(profile.get("raw_material_transport_sha256").getAsString())&&"c0c54dff186381cf787f568eec3e660df2a860d9ea38dbecb3c77c9492e72303".equals(profile.get("private_depth_helper_sha256").getAsString());
   boolean bslSpatial="bsl-shaders".equals(family)&&profile.has("proof_kind")&&"bsl-forward-spatial-subset-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("native_vertex_math_preserved").getAsBoolean()&&profile.get("adapter0_native_vertex_exact").getAsBoolean()&&profile.get("adapter1_adds_only_raw_material").getAsBoolean()&&profile.get("native_fragment_phase0_preserved").getAsBoolean()&&profile.get("same_linked_native_program_both_roles").getAsBoolean()&&"10c3f2afee5e662aeaf1057dd0f3f683473add17f4cb3b917652682e4e246703".equals(profile.get("raw_material_transport_sha256").getAsString());
   boolean candleMakeUp="makeup-ultra-fast-shaders".equals(family)&&profile.has("proof_kind")&&"makeup-candle-exact-zero-v1".equals(profile.get("proof_kind").getAsString())&&
     profile.has("indexed_vertex_behavior_preserved")&&profile.get("indexed_vertex_behavior_preserved").getAsBoolean()&&
     profile.has("candle_semantics_sha256")&&"763bfe3abdc2eba342afc8fd22b5f958adbfb95f08ec77ff95bb85335c3373ab".equals(profile.get("candle_semantics_sha256").getAsString())&&
     profile.has("zero_helper_sha256")&&"826ed57183367e10404bacdce7fae0ec518160f0d8bf71b11efa57f407c5912b".equals(profile.get("zero_helper_sha256").getAsString());
   boolean bslRate="bsl-shaders".equals(family)&&profile.has("proof_kind")&&"bsl-forward-spatial-rate-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("adapter0_native_vertex_exact").getAsBoolean()&&profile.get("native_phase012_vertex_math_preserved").getAsBoolean()&&profile.get("owned_phase3_fullscreen_triangle").getAsBoolean()&&profile.get("same_linked_native_program_all_three_roles").getAsBoolean()&&profile.get("native_fragment_phase0_preserved").getAsBoolean()&&"10c3f2afee5e662aeaf1057dd0f3f683473add17f4cb3b917652682e4e246703".equals(profile.get("raw_material_transport_sha256").getAsString())&&"50e4550237bbbd5ec6c59ca925f71d01e7d7c6597d5a6fc0a771f4591379add1".equals(profile.get("triangle_role_semantics_sha256").getAsString());
   boolean solasRate="solas-shader".equals(family)&&profile.has("proof_kind")&&"solas-forward-rate-phase-v1".equals(profile.get("proof_kind").getAsString())&&profile.get("native_phase012_vertex_exact").getAsBoolean()&&profile.get("captured_input_lighting").getAsBoolean()&&profile.get("fragment_phase0_native").getAsBoolean()&&profile.get("actual_normal_selector_GREATER01").getAsBoolean()&&"b492ffac45bb848a0933886bbcedb3beb20beb43ef0a159445d21355c2443295".equals(profile.get("rate_vertex_branch_sha256").getAsString());
   if(!exact&&!unusedPhoton&&!indexedBliss&&!unusedAstra&&!astraDepth&&!astraSpatial&&!bslSpatial&&!candleMakeUp&&!bslRate&&!solasRate)return false;
   var expectedRoots=new TreeSet<String>();profile.getAsJsonArray("roots").forEach(e->expectedRoots.add(e.getAsString()));
   return !expectedRoots.isEmpty()&&expectedRoots.equals(roots(original))&&expectedRoots.equals(roots(generated))&&
     map(profile.getAsJsonObject("original_closure")).equals(closure(original))&&map(profile.getAsJsonObject("generated_closure")).equals(closure(generated));
  }catch(Exception|LinkageError unavailable){return false;}
 }
}
