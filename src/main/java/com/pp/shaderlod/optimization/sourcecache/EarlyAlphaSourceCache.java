package com.pp.shaderlod.optimization.sourcecache;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.util.zip.ZipFile;import com.google.gson.*;import com.pp.shaderlod.optimization.*;
/** Exact source verification during a build; steady callers consume the build-owned receipt. */
public final class EarlyAlphaSourceCache {
 @FunctionalInterface public interface Validation {boolean run()throws Exception;}
 private Object[] owners;private Object key;private boolean valid,result;
 public void invalidate(){valid=false;owners=null;key=null;}
 private record HelperPin(String path,String sha256){}
 private record HelperPlan(Class<?> owner,ClassLoader loader,String family,String recipeSha,long generation,List<HelperPin> helpers){
  private HelperPlan {helpers=List.copyOf(helpers);}
 }
 // One bounded parse memo, never a byte/digest memo. The caller reads and hashes the recipe first.
 private HelperPlan helperPlan;
 private synchronized List<HelperPin> helperPins(Class<?> owner,String family,String recipeSha,byte[] recipe){
  boolean enabled=OptimizationConfig.requested(OptimizationId.BINDING_CERTIFICATE_CACHE);
  var previous=helperPlan;
  if(enabled&&previous!=null&&previous.owner()==owner&&previous.loader()==owner.getClassLoader()
      &&previous.family().equals(family)&&previous.recipeSha().equals(recipeSha)&&previous.generation()==OptimizationConfig.generation())return previous.helpers();
  helperPlan=null; // Changed/malformed input or Cache Off cannot retain a reusable old plan.
  var helpers=parseHelperPins(recipe);
  var plan=new HelperPlan(owner,owner.getClassLoader(),family,recipeSha,OptimizationConfig.generation(),helpers);
  if(enabled&&OptimizationConfig.requested(OptimizationId.BINDING_CERTIFICATE_CACHE))helperPlan=plan;
  return plan.helpers();
 }
 private static List<HelperPin> parseHelperPins(byte[] recipe){
  var json=JsonParser.parseString(new String(recipe,StandardCharsets.UTF_8)).getAsJsonObject();
  var helpers=new ArrayList<HelperPin>();
  for(var e:json.getAsJsonObject("additions").entrySet()){
   var pin=e.getValue().getAsJsonObject();String path=pin.get("path").getAsString();
   if(path.startsWith("/")||path.contains("..")||path.contains("\\"))throw new IllegalStateException("Unsafe helper resource");
   helpers.add(new HelperPin(path,pin.get("sha256").getAsString()));
  }
  return List.copyOf(helpers);
 }
 // Explicit current immutable dependency pins, same resource set used by NF26.1.2 source admission.
 private static final Map<String,String> RESOURCES=Map.ofEntries(
  Map.entry("assets/shaderlod/astraquad/input-api.json","59f6baaf62c8989385c16abaa52c1e5bc023659ee8b3a2317b863703397bc15b"),
  Map.entry("assets/shaderlod/astraquad/native1015.json","298347d107f05379e2ab255059f768cc5542ab215ccfaad5a9c121c8778fba71"),
  Map.entry("assets/shaderlod/astraquad/source-proof.json","9f9383722ca89408a4f7bdb62817973fdcce9a7b7a046e7fdbf14c50d3ccd8de"),
  Map.entry("assets/shaderlod/blissquad/api-proof.json","806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07"),
  Map.entry("assets/shaderlod/blissquad/native463.json","c6c53b96a18a0f6d6a8a6a3019f7041315aa5e626052f90201665cea21cd7883"),
  Map.entry("assets/shaderlod/blissquad/source-proof.json","be8d75438ade58edd1518b30c7000edc2c0df80ce2696cc2884819dd9564c73b"),
  Map.entry("assets/shaderlod/bslquad/api-proof.json","806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07"),
  Map.entry("assets/shaderlod/bslquad/native384.json","0aaa1276ea6e24106e6ed062a3bd149d1b53c09693d04a4637dcb19e6c43c394"),
  Map.entry("assets/shaderlod/bslquad/source-proof.json","82d47e661df8ead0923ee1b05380561910c5465a67ca215b94e26fbd1cb86ee2"),
  Map.entry("assets/shaderlod/makeupquad/api-proof.json","806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07"),
  Map.entry("assets/shaderlod/makeupquad/native100.json","0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb"),
  Map.entry("assets/shaderlod/makeupquad/source-proof.json","f7d016d9ea720256ce3e7b5fb86055c657cc77c5c65fdc755f0ecfa9ffca0dc0"),
  Map.entry("assets/shaderlod/photonquad/api-proof.json","80768f1ca89a62e3a8746f0df5e49cdce8b45b91810f671dfd03b5f586544bb7"),
  Map.entry("assets/shaderlod/photonquad/native366.json","9b1d4db811f54cc986c97e8c8ce917f6731fbaf1f006927ccdef351bb3c5797f"),
  Map.entry("assets/shaderlod/photonquad/source-proof.json","24dce64cea2682c5d2b4d8f513531c25eadeb961bd3dc0293bd061f04c5ec5ea"),
  Map.entry("assets/shaderlod/reimaginedquad/api-proof.json","806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07"),
  Map.entry("assets/shaderlod/reimaginedquad/native398.json","ccfca334aeaa074a74d1f8e9071b707d32161a6b771366fefcc0f87f3b1175f4"),
  Map.entry("assets/shaderlod/reimaginedquad/source-proof.json","25e3408224e16748c86ae505b20355c50be514de2601658a74598cef1df84baf"),
  Map.entry("assets/shaderlod/rendererquad/generated-main-options.json","b40de1d2f388ac3db4d0cea9d3bd1d4a7bfdb61f94ebe33d34ecc16508bf5546"),
  Map.entry("assets/shaderlod/rendererquad/legacy-scalar-api.json","579617c660fce1eb6ceeeaea4665cd0eaa36ef56ad4274289d8308e39dc389ad"),
  Map.entry("assets/shaderlod/rendererquad/named-vertex-contracts.json","a7e0323f6526057fbe6510d94be1576a1ca2a869ba3ddc24aab36e267e1d3dfe"),
  Map.entry("assets/shaderlod/rendererquad/photon-normal-main-options.json","b0fa3507253a383429a66cd792aab587afe1f8597a7c978974e1175646cd4b8d"),
  Map.entry("assets/shaderlod/sildurquad/api-proof.json","806daab5da2ec861b16ff3271c481dbb579bf789743c78afee6103fe0ab2ea07"),
  Map.entry("assets/shaderlod/sildurquad/native115.json","795a8b210019380e8d120924aa4ef24555afc8bf9ea5ff9bffe8dafb9d631bd3"),
  Map.entry("assets/shaderlod/sildurquad/source-proof.json","896abface1ed04cf72f79f8460e55fa5821702a69382d9a5691ef47583cf6d51"),
  Map.entry("assets/shaderlod/solasquad/input-api.json","59f6baaf62c8989385c16abaa52c1e5bc023659ee8b3a2317b863703397bc15b"),
  Map.entry("assets/shaderlod/solasquad/native428.json","261a3fbc6b6b6ff58a270ae4727ddec2c7ddcf7c642738c4454fa1f1b6eb4113"),
  Map.entry("assets/shaderlod/solasquad/source-proof.json","f11b624f965b6822b431fe2635c3751e9f8f8a36815765c8d8813bc02bcf6900"),
  Map.entry("assets/shaderlod/unboundquad/api-proof.json","80768f1ca89a62e3a8746f0df5e49cdce8b45b91810f671dfd03b5f586544bb7"),
  Map.entry("assets/shaderlod/unboundquad/native398.json","b64d94998c99bf867e80d350a4a0a8108c58507e0401eb053a70e3aca81a5036"),
  Map.entry("assets/shaderlod/unboundquad/source-proof.json","4da7e9f155925752ff043576856825898d70c211bd9b3b9792f4816f788dac2d"));
 private static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));}
 private static byte[] resource(Class<?> owner,String path)throws Exception{try(var in=owner.getClassLoader().getResourceAsStream(path)){if(in==null)throw new IllegalStateException("Missing source dependency: "+path);return in.readAllBytes();}}
 private static Path child(Path dir,String name)throws Exception{if(name==null||name.isBlank()||!Path.of(name).getFileName().toString().equals(name))throw new IllegalStateException("Unsafe source child");Path p=dir.resolve(name).normalize();if(!dir.equals(p.getParent())||Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Missing/unsafe source child");return p;}
 private static String optional(Path p)throws Exception{if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return "absent";if(Files.isSymbolicLink(p)||!Files.isRegularFile(p))throw new IllegalStateException("Unsafe sidecar");return SourceArchiveDigest.sha256(p);}
 // Archive and classloader names use '/', independent of the host filesystem.
 private static String safe(String name){if(name==null||name.isBlank()||name.startsWith("/")||name.contains("..")||name.contains("\\")||name.indexOf('\0')>=0||name.matches("^[A-Za-z]:.*")||Arrays.stream(name.split("/",-1)).anyMatch(part->part.isEmpty()||part.equals(".")))throw new IllegalStateException("Unsafe Photon member/resource");return name;}
 private static String hashPin(JsonObject object,String name){String value=object.get(name).getAsString();if(!value.matches("[a-f0-9]{64}"))throw new IllegalStateException("Invalid Photon digest");return value;}
 private static void photonHelper(Class<?> owner,String target,JsonObject pin,String sourceKey,Set<String> targets,List<Object> stamps)throws Exception {
  if(!safe(target).startsWith("shaders/")||!targets.add(target))throw new IllegalStateException("Photon helper target collision");
  String source=safe(pin.get(sourceKey).getAsString()),expected=hashPin(pin,"sha256");String actual=sha(resource(owner,"assets/shaderlod/adapters/"+source));
  if(!expected.equals(actual))throw new IllegalStateException("Photon helper content changed: "+source);stamps.add(List.of(target,source,actual));
 }
 private static void nativePin(Map<String,String> pins,String member,String expected){safe(member);String before=pins.putIfAbsent(member,expected);if(before!=null&&!before.equals(expected))throw new IllegalStateException("Conflicting Photon native source pin");}
 /** Photon has an explicit different maintained manifest, never adapters/photon.json. */
 private static void photon(Class<?> owner,Path original,List<Object> stamps)throws Exception {
  byte[] recipe=resource(owner,"assets/shaderlod/adapters/patches/photon-experiments/live-pixel-budget.json");stamps.add(sha(recipe));var json=JsonParser.parseString(new String(recipe,StandardCharsets.UTF_8)).getAsJsonObject();
  if(json.get("schema").getAsInt()!=1||!"v1.3b".equals(json.get("base_version").getAsString())||!"SHADERLOD_LIVE_PIXEL_BUDGET".equals(json.get("switch").getAsString()))throw new IllegalStateException("Unsupported Photon manifest");hashPin(json,"base_sha256");
  var targets=new HashSet<String>();for(String key:List.of("addition","common_addition")){var pin=json.getAsJsonObject(key);photonHelper(owner,pin.get("path").getAsString(),pin,"source_path",targets,stamps);}
  var additions=json.getAsJsonObject("additions");if(additions==null||additions.size()==0)throw new IllegalStateException("Missing Photon additions");for(var e:new TreeMap<>(additions.asMap()).entrySet())photonHelper(owner,e.getKey(),e.getValue().getAsJsonObject(),"path",targets,stamps);
  var required=new TreeMap<String,String>();var dependencies=json.getAsJsonObject("source_dependencies");if(dependencies==null||dependencies.size()==0)throw new IllegalStateException("Missing Photon source dependencies");for(var e:dependencies.entrySet()){String hash=e.getValue().getAsString();if(!hash.matches("[a-f0-9]{64}"))throw new IllegalStateException("Invalid Photon dependency digest");nativePin(required,e.getKey(),hash);}
  var members=json.getAsJsonArray("members");if(members==null||members.size()==0)throw new IllegalStateException("Missing Photon source members");for(var member:members){var pin=member.getAsJsonObject();hashPin(pin,"patched_sha256");if(pin.getAsJsonArray("replacements")==null||pin.getAsJsonArray("replacements").size()==0)throw new IllegalStateException("Missing Photon source replacement");nativePin(required,pin.get("path").getAsString(),hashPin(pin,"source_sha256"));}
  nativePin(required,"LICENSE",hashPin(json,"license_sha256"));
  try(var zip=new ZipFile(original.toFile())){for(var pin:required.entrySet()){var entry=zip.getEntry(pin.getKey());if(entry==null||entry.isDirectory())throw new IllegalStateException("Missing Photon original member");var digest=java.security.MessageDigest.getInstance("SHA-256");try(var in=zip.getInputStream(entry)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1)digest.update(buffer,0,count);}String actual=HexFormat.of().formatHex(digest.digest());if(!actual.equals(pin.getValue()))throw new IllegalStateException("Photon original dependency changed: "+pin.getKey());stamps.add(List.of(pin.getKey(),actual));}}
  String profile=sha(resource(owner,"assets/shaderlod/photonearlyalpha/optional394.json"));if(!profile.equals("0ba649f93425bd223b46ee0de06dbc138bc074b30d901e58e679051509dc554b"))throw new IllegalStateException("Photon optional native profile changed");stamps.add(List.of("photon-optional394",profile));
 }
 public static Object fingerprint(Class<?> owner,Path directory,String name,String family)throws Exception {
  return fingerprint(owner,directory,name,family,null);
 }
 private static Object fingerprint(Class<?> owner,Path directory,String name,String family,EarlyAlphaSourceCache parseOwner)throws Exception {
  Path dir=directory.toAbsolutePath().normalize(),selected=child(dir,name);var stamps=new ArrayList<Object>();
  stamps.add(selected.toString());stamps.add(SourceArchiveDigest.sha256(selected)); // ALWAYS complete raw bytes, never stat/TTL/digest memo.
  Path original;
  try(var zip=new ZipFile(selected.toFile())){var entry=zip.getEntry("shaderlod-selection.json");if(entry==null||entry.getSize()<0||entry.getSize()>65536)throw new IllegalStateException("Generated source marker absent/oversize");JsonObject marker;try(var in=zip.getInputStream(entry)){byte[] b=in.readNBytes(65537);if(b.length>65536)throw new IllegalStateException("Marker overflow");marker=JsonParser.parseString(new String(b,StandardCharsets.UTF_8)).getAsJsonObject();}
   if(marker.get("schema").getAsInt()!=1||!family.equals(marker.get("adapterId").getAsString()))throw new IllegalStateException("Foreign source marker");
   original=child(dir,marker.get("originalFile").getAsString());Path state=child(dir,marker.get("stateFile").getAsString());stamps.add(SourceArchiveDigest.sha256(original));stamps.add(SourceArchiveDigest.sha256(state));stamps.add(optional(dir.resolve(name+".txt")));stamps.add(optional(dir.resolve(original.getFileName()+".txt")));
  }
  if("photon".equals(family))photon(owner,original,stamps);
  else {
  byte[] recipe=resource(owner,"assets/shaderlod/adapters/patches/adapters/"+family+".json");String recipeSha=sha(recipe);stamps.add(recipeSha);
  for(var pin:parseOwner==null?parseHelperPins(recipe):parseOwner.helperPins(owner,family,recipeSha,recipe)){String path=pin.path();byte[] bytes=resource(owner,"assets/shaderlod/adapters/"+path);String actual=sha(bytes);if(!actual.equals(pin.sha256()))throw new IllegalStateException("Helper content changed");stamps.add(List.of(path,actual));}
  }
  for(var e:new TreeMap<>(RESOURCES).entrySet()){String actual=sha(resource(owner,e.getKey()));if(!actual.equals(e.getValue()))throw new IllegalStateException("Source dependency changed: "+e.getKey());stamps.add(List.of(e.getKey(),actual));}
  return List.copyOf(stamps);
 }
 public boolean check(Class<?> owner,Path dir,String name,String family,Map<String,String> options,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object core,Collection<String> resources,Validation validation)throws Exception {
  if(!IrisSourceLifecycle.matchesInputs(dir,name,pack,values,declared,options))return false;
  return IrisSourceLifecycle.proof(java.util.List.of(this,owner,family,options,epoch),()->validateAtLoad(owner,dir,name,family,options,pack,values,declared,pipeline,world,epoch,core,resources,validation));
 }
 private boolean validateAtLoad(Class<?> owner,Path dir,String name,String family,Map<String,String> options,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object core,Collection<String> resources,Validation validation)throws Exception {

  try {
  Object[] actualOwners={owner,pack,values,declared,pipeline,world};long flags=OptimizationConfig.generation();
  Object stamp=fingerprint(owner,dir,name,family,this);Object next=List.of(Map.copyOf(options),epoch,com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation(),flags,core==null?"unavailable":core,List.copyOf(resources),stamp);
  boolean same=valid&&owners!=null&&owners.length==actualOwners.length;for(int i=0;same&&i<owners.length;i++)same=owners[i]==actualOwners[i];
  if(OptimizationConfig.requested(OptimizationId.BINDING_CERTIFICATE_CACHE)&&same&&Objects.equals(key,next)&&flags==OptimizationConfig.generation())return result;
  invalidate();boolean accepted=validation.run();
  if(flags!=OptimizationConfig.generation()||!stamp.equals(fingerprint(owner,dir,name,family,this)))return false;
  owners=actualOwners;key=next;result=accepted;valid=true;return accepted;
  }catch(Exception|Error failure){invalidate();throw failure;}
 }
}
