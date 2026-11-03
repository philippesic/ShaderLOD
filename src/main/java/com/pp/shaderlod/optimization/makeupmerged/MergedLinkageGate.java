package com.pp.shaderlod.optimization.makeupmerged;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.ShaderLodConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
/** Normal NF1.21.1: exact original native100 or current generated106 source and pinned API. */
public final class MergedLinkageGate {
    private static Boolean apiPinned;
    private static final Set<String> diagnostics=new HashSet<>();
    public static synchronized void noteOnce(String reason) {
        if(diagnostics.add(reason))System.out.println("[ShaderLOD merged] gate="+reason);
    }
    private static Object certifiedPack;private static String certifiedName;private static long certifiedGeneration=-1;
    private static Map<String,String> certifiedOptions;private static boolean certified;
    private MergedLinkageGate() {}
    public static String sha(byte[] value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    public static String status(){return "MakeUp NF1211 merged SOLID/CUTOUT normal consumer; per-pass regional copy/TBO; actual GL pending";}
    public static boolean exactApi() {
        if(apiPinned!=null)return apiPinned;
        String[][] pins={
        {"net/irisshaders/iris/Iris.class","4cb88bd1c15635db3845cb4b3db757c611707e1fc2a00af879298c09bb45d3d1"},        {"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},
        {"net/irisshaders/iris/pipeline/programs/SodiumShader.class","33e49ddf69f01a86a64ad2e2d33595b81a22a859ba1feda0dda65813e663f356"},
        {"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},
        {"net/irisshaders/iris/gl/program/ProgramSamplers.class","431566fc2b9233c62c4413dbe1231600275bd1a31562e62355f0e3d75ce387f7"},
        {"net/irisshaders/iris/gl/texture/TextureType.class","93e55d5ac5a3902f68a95a04273bb0095215da86f7a92aa6d43ca7edf81c31db"},
        {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram.class","f4439d38f4733c65c08f5b575a387835725605c43d8860f3e652cb4fd92c7ec0"},
        {"net/caffeinemc/mods/sodium/client/gl/shader/GlProgram$Builder.class","80e9edbf32bf34171bdafcfb94e79a3c764a11b81d0a1183788220c64b8b7758"}};
        try {for(var pin:pins)try(var in=MergedLinkageGate.class.getClassLoader().getResourceAsStream(pin[0])) {
            if(in==null){noteOnce("linkage-api missing="+pin[0]+" loader="+MergedLinkageGate.class.getClassLoader().getClass().getName());return apiPinned=false;}
            String actual=sha(in.readAllBytes());if(!pin[1].equals(actual)){noteOnce("linkage-api mismatch="+pin[0]+" actual="+actual+" expected="+pin[1]);return apiPinned=false;}
        }noteOnce("linkage-api admitted");return apiPinned=true;}catch(Exception|LinkageError unavailable){noteOnce("linkage-api exception="+unavailable.getClass().getSimpleName());return apiPinned=false;}
    }
    /** Same exact native profile; colored shadow is fragment-only and both legal values have active VSH proof. */
    public static boolean source(java.nio.file.Path archive,Map<String,String> actual){
        if(actual==null||!Set.of("true","false").contains(actual.getOrDefault("COLORED_SHADOW","invalid")))return false;
        var normalized=new TreeMap<>(actual);normalized.put("COLORED_SHADOW","true");
        if(normalized.size()==100)return com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.optionsMatch(normalized)&&
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(archive,normalized).eligible();
        if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("makeup-ultra-fast-shaders",normalized))return false;
        try{
            if(!"makeup-ultra-fast-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(archive,actual)))return false;
            try(var in=MergedLinkageGate.class.getClassLoader().getResourceAsStream("assets/shaderlod/adapters/patches/adapters/makeup-ultra-fast-shaders.json")){
                if(in==null || !com.pp.shaderlod.optimization.makeupao.MakeUpAoSourceProof.RECIPE_SHA.equals(sha(in.readAllBytes())))return false;
            }
            var generated=archive(archive);var marker=com.google.gson.JsonParser.parseString(new String(generated.get("shaderlod-selection.json"),StandardCharsets.UTF_8)).getAsJsonObject();
            String name=marker.get("originalFile").getAsString();if(name.isBlank()||!Path.of(name).getFileName().toString().equals(name))return false;
            Path original=archive.toAbsolutePath().normalize().getParent().resolve(name);
            return com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.validate(original,com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions()).eligible()&&
                com.pp.shaderlod.integration.makeupquad.NamedVertexContract.accepts("makeup-ultra-fast-shaders",archive(original),generated);
        }catch(Exception|LinkageError e){return false;}
    }
    private static Map<String,byte[]> archive(Path p)throws Exception{
        if(!Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(p)>128L*1024*1024)throw new IllegalArgumentException("archive");
        var files=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(p))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){byte[] b=zip.readNBytes(128*1024*1024+1);total+=b.length;if(total>128L*1024*1024)throw new IllegalArgumentException("extent");if(!e.isDirectory()&&files.put(e.getName(),b)!=null)throw new IllegalArgumentException("duplicate");}}return files;
    }
    public static boolean packPinned(net.irisshaders.iris.shaderpack.ShaderPack pack){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(MergedLinkageGate.class,"packPinned",pack),()->{
        if(pack==null||!exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())return false;
        try{
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1"))return false;
            var stack=new TreeMap<String,String>();for(var mod:ModList.get().getMods())stack.put(mod.getModId(),mod.getVersion().toString());
            if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(stack))return false;
            var options=pack.getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();var actual=new TreeMap<String,String>();
            for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
            for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null||!Path.of(name).getFileName().toString().equals(name))return false;
            long generation=com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation();
            if(pack!=certifiedPack||!name.equals(certifiedName)||generation!=certifiedGeneration||!actual.equals(certifiedOptions)){
                certified=false;certifiedPack=pack;certifiedName=name;certifiedOptions=Map.copyOf(actual);certifiedGeneration=generation;
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
                certified=path.startsWith(dir)&&!path.equals(dir)&&source(path,actual);
            }return certified;
        }catch(Exception|LinkageError e){return false;}
    
  });
 }
    public static boolean controls(boolean loaded,boolean enabled,LodPreset preset,boolean flag){return loaded&&enabled&&preset==LodPreset.PERFORMANCE&&flag;}
    public static boolean enabled(){
        try{var client=Minecraft.getInstance();return controls(ShaderLodConfig.SPEC.isLoaded(),ShaderLodConfig.snapshot().enabled(),ShaderLodConfig.PRESET.get(),com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN)) &&
            !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()&&client.level!=null&&client.level.dimension()==Level.OVERWORLD&&
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds())&&
            packPinned(Iris.getCurrentPack().orElse(null));
        }catch(Exception|LinkageError e){return false;}
    }
    // Only pending derivative owners; no native geometry or cross-frame certification is retained.
    private static final ThreadLocal<Construction> constructing=new ThreadLocal<>();
    public static Construction beginConstruction() {
        if(constructing.get()!=null)throw new IllegalStateException("nested merged pipeline construction");
        var scope=new Construction();constructing.set(scope);noteOnce("construction entered");return scope;
    }
    public static boolean trackConstruction(MergedProgramAccess owner) {
        return trackConstructionCleanup(owner,owner::deleteMergedPrograms);
    }
    public static boolean trackConstructionCleanup(Object owner,Runnable cleanup) {
        var scope=constructing.get();if(scope==null){noteOnce("clone construction-scope=false");return false;}
        if(scope.seen.add(java.util.Objects.requireNonNull(owner)))scope.owners.add(java.util.Objects.requireNonNull(cleanup));return true;
    }
    public static final class Construction implements AutoCloseable {
        private final List<Runnable> owners=new ArrayList<>();
        private final Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean committed,closed;
        private Construction() {}
        public void commit(){committed=true;}
        /** Constructor failures must supply the primary Throwable before the final close. */
        public void closeAfterFailure(Throwable primary) {
            java.util.Objects.requireNonNull(primary);
            var failures=releaseOwners();
            Set<Throwable> attached=Collections.newSetFromMap(new IdentityHashMap<>());
            attached.add(primary);Collections.addAll(attached,primary.getSuppressed());
            for(var failure:failures)if(attached.add(failure))primary.addSuppressed(failure);
        }
        private List<Throwable> releaseOwners() {
            if(closed)return List.of();closed=true;constructing.remove();
            var failures=new ArrayList<Throwable>();
            try {
                if(!committed)for(var owner:owners)try{owner.run();}
                    catch(RuntimeException|LinkageError cleanup){
                        failures.add(cleanup);
                        System.out.println("[ShaderLOD merged] derivative cleanup failed: "+cleanup.getClass().getSimpleName());
                    }catch(Error cleanup){failures.add(cleanup);}
            }finally{owners.clear();seen.clear();}
            return failures;
        }
        /** Close without an active constructor failure; committed ownership stays native. */
        public void close() {
            var failures=releaseOwners();
            Error unexpected=null;
            for(var failure:failures)if(failure instanceof Error error && !(error instanceof LinkageError)){
                unexpected=error;break;
            }
            if(unexpected!=null){
                Set<Throwable> attached=Collections.newSetFromMap(new IdentityHashMap<>());
                attached.add(unexpected);
                for(var failure:failures)if(attached.add(failure))unexpected.addSuppressed(failure);
                throw unexpected;
            }
        }
    }
    public static String helper() {
        try(var in=MergedLinkageGate.class.getResourceAsStream("/shaderlod-makeup-merged-normal-region-offset.glsl")) {
            return in==null?null:new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception unavailable){return null;}
    }
    /** Gate every linkage seam before Mixin application, including private Builder accessor. */
    public static final class Plugin implements org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin {
        public boolean shouldApplyMixin(String target,String mixin){boolean flag=true;boolean apply=exactApi();noteOnce("linkage-plugin mixin="+mixin+" flag="+flag+" apply="+apply);return apply;}
        public void onLoad(String p){noteOnce("linkage-plugin loaded package="+p); } public String getRefMapperConfig(){return null;}
        public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
        public void preApply(String t,org.objectweb.asm.tree.ClassNode n,String m,org.spongepowered.asm.mixin.extensibility.IMixinInfo info){}
        public void postApply(String t,org.objectweb.asm.tree.ClassNode n,String m,org.spongepowered.asm.mixin.extensibility.IMixinInfo info){}
    }


 /** Invoked synchronously by the authentic pipeline build; never by a rendering callback. */
 public static void qualifyLoadedSource(net.irisshaders.iris.shaderpack.ShaderPack pack){try{packPinned(pack);}catch(Exception|LinkageError refused){}}
}
