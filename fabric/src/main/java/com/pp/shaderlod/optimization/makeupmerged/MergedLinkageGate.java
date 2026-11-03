package com.pp.shaderlod.optimization.makeupmerged;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.fabricmc.loader.api.FabricLoader;
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
    public static String status(){return "MakeUp Fabric1211 merged SOLID/CUTOUT normal consumer; per-pass regional copy/TBO; actual GL pending";}
    public static boolean exactApi(){return MakeUpMergedApiPins.exactApi();}
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
        if(pack==null||!exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())return false;
        try{
            var caps=org.lwjgl.opengl.GL.getCapabilities();
            if(!capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glTexBuffer,caps.glMultiDrawElementsBaseVertex))return false;
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1"))return false;
            var stack=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());
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
    }
    public static boolean capabilitiesAllow(int pointerSize,boolean gl41,long texBuffer,long multiDraw){return pointerSize==8&&gl41&&texBuffer!=0&&multiDraw!=0;}
    public static boolean controls(boolean loaded,boolean enabled,LodPreset preset,boolean flag){return loaded&&enabled&&preset==LodPreset.PERFORMANCE&&flag;}
    public static boolean enabled(){
        try{var client=Minecraft.getInstance();return controls(FabricLodConfig.loaded(),FabricLodConfig.snapshot().enabled(),FabricLodConfig.snapshot().preset(),com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN)) &&
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

}
