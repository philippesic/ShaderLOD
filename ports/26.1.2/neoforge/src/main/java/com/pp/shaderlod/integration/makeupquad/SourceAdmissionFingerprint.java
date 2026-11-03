package com.pp.shaderlod.integration.makeupquad;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.util.zip.ZipFile;import com.google.gson.*;
/** Actual complete-byte renderer source admission stamps, no timestamp/size or cached digest. */
public final class SourceAdmissionFingerprint {
 private SourceAdmissionFingerprint(){}
 private static final List<String> RESOURCES=List.of(
"assets/shaderlod/astraquad/input-api.json",
"assets/shaderlod/astraquad/native1015.json",
"assets/shaderlod/astraquad/source-proof.json",
"assets/shaderlod/blissquad/api-proof.json",
"assets/shaderlod/blissquad/native463.json",
"assets/shaderlod/blissquad/source-proof.json",
"assets/shaderlod/bslquad/api-proof.json",
"assets/shaderlod/bslquad/native384.json",
"assets/shaderlod/bslquad/source-proof.json",
"assets/shaderlod/makeupquad/api-proof.json",
"assets/shaderlod/makeupquad/native100.json",
"assets/shaderlod/makeupquad/source-proof.json",
"assets/shaderlod/photonquad/api-proof.json",
"assets/shaderlod/photonquad/native366.json",
"assets/shaderlod/photonquad/source-proof.json",
"assets/shaderlod/reimaginedquad/api-proof.json",
"assets/shaderlod/reimaginedquad/native398.json",
"assets/shaderlod/reimaginedquad/source-proof.json",
"assets/shaderlod/rendererquad/generated-main-options.json",
"assets/shaderlod/rendererquad/legacy-scalar-api.json",
"assets/shaderlod/rendererquad/named-vertex-contracts.json",
"assets/shaderlod/rendererquad/photon-normal-main-options.json",
"assets/shaderlod/sildurquad/api-proof.json",
"assets/shaderlod/sildurquad/native115.json",
"assets/shaderlod/sildurquad/source-proof.json",
"assets/shaderlod/solasquad/input-api.json",
"assets/shaderlod/solasquad/native428.json",
"assets/shaderlod/solasquad/source-proof.json",
"assets/shaderlod/unboundquad/api-proof.json",
"assets/shaderlod/unboundquad/native398.json",
"assets/shaderlod/unboundquad/source-proof.json");
 private static Path child(Path dir,String name)throws Exception{if(name==null||name.isBlank()||!Path.of(name).getFileName().toString().equals(name))throw new IllegalStateException("Unsafe source/state name");var p=dir.resolve(name).normalize();if(!p.getParent().equals(dir)||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Missing/unsafe source/state");return p;}
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));}
 private static byte[] resource(String name)throws Exception{try(var in=SourceAdmissionFingerprint.class.getClassLoader().getResourceAsStream(name)){if(in==null)throw new IllegalStateException("Missing renderer source resource "+name);return in.readAllBytes();}}
 public static Object read(Path directory,String name)throws Exception{Path dir=directory.toAbsolutePath().normalize(),selected=child(dir,name);String selectedSha=com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(selected);var stamps=new ArrayList<Object>();stamps.add(selectedSha);
  try(var zip=new ZipFile(selected.toFile())){var e=zip.getEntry("shaderlod-selection.json");if(e!=null){if(e.getSize()<0||e.getSize()>65536)throw new IllegalStateException("Marker size");JsonObject marker;try(var input=zip.getInputStream(e)){marker=JsonParser.parseString(new String(input.readNBytes(65537),StandardCharsets.UTF_8)).getAsJsonObject();}if(marker.get("schema").getAsInt()!=1)throw new IllegalStateException("Marker schema");var original=child(dir,marker.get("originalFile").getAsString());var state=child(dir,marker.get("stateFile").getAsString());stamps.add(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(original));stamps.add(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(state));var family=marker.get("adapterId").getAsString();var catalog=com.pp.shaderlod.packs.SupportedPackCatalog.inventory().stream().filter(p->p.id().equals(family)).findFirst().orElseThrow();String recipePath=family.equals("photon")?"patches/photon-experiments/live-pixel-budget.json":"patches/adapters/"+family+".json";byte[] recipe=resource("assets/shaderlod/adapters/"+recipePath);stamps.add(sha(recipe));var json=JsonParser.parseString(new String(recipe,StandardCharsets.UTF_8)).getAsJsonObject();for(var addition:json.getAsJsonObject("additions").entrySet()){var pin=addition.getValue().getAsJsonObject();stamps.add(sha(resource("assets/shaderlod/adapters/"+pin.get("path").getAsString())));}}
  }
  for(String path:RESOURCES)stamps.add(sha(resource(path)));return List.copyOf(stamps);
 }
}
