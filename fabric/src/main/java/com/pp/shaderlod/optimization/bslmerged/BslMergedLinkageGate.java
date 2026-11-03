package com.pp.shaderlod.optimization.bslmerged;
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
/** Normal NF1.21.1: exact original native384 or current generated397 source and pinned API. */
public final class BslMergedLinkageGate {
    private static Boolean apiPinned;
    private static final Set<String> diagnostics=new HashSet<>();
    public static synchronized void noteOnce(String reason) {
        if(diagnostics.add(reason))System.out.println("[ShaderLOD merged] gate="+reason);
    }
    private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache MERGED_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
    public static void invalidateSource(){MERGED_SOURCE_CACHE.invalidate();}
    private BslMergedLinkageGate() {}
    public static String sha(byte[] value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    public static String status(){return "BSL Fabric1211 merged SOLID/CUTOUT normal consumer; per-pass regional copy/TBO; actual GL pending";}
    public static boolean exactApi(){return com.pp.shaderlod.optimization.makeupmerged.MakeUpMergedApiPins.exactApi();}
    public static boolean source(Path selected,Map<String,String> actual) {
      if(actual==null)return false;
      if(actual.size()==384)return com.pp.shaderlod.integration.bslquad.BslQuadEligibility.optionsMatch(actual)&&com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(selected,actual).eligible();
      if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("bsl-shaders",actual))return false;
      try {
       if(!"c994e5977daaf74f7d03cdfb71871e55b078a5b66adaba158efcef7e2ba81010".equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(selected).memberSha256())))return false;
       if(!"bsl-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(selected,actual)))return false;
       try(var in=BslMergedLinkageGate.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/bsl-shaders.json")){if(in==null||!sha(in.readAllBytes()).equals("040a27f13bd94f204b814cb28604e10fdbe5f0fa9aefd226aaa3eda83930da0e"))return false;}
       var generated=archive(selected);var marker=com.google.gson.JsonParser.parseString(new String(generated.get("shaderlod-selection.json"),StandardCharsets.UTF_8)).getAsJsonObject();String name=marker.get("originalFile").getAsString();if(!Path.of(name).getFileName().toString().equals(name))return false;
       Path original=selected.toAbsolutePath().normalize().getParent().resolve(name);
       return com.pp.shaderlod.integration.bslquad.BslQuadEligibility.validate(original,com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions()).eligible()&&com.pp.shaderlod.integration.makeupquad.NamedVertexContract.accepts("bsl-shaders",archive(original),generated);
      }catch(Exception|LinkageError failure){return false;}
    }
    private static Map<String,byte[]> archive(Path p)throws Exception{
        if(!Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(p)>128L*1024*1024)throw new IllegalArgumentException("archive");
        var files=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(p))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){byte[] b=zip.readNBytes(128*1024*1024+1);total+=b.length;if(total>128L*1024*1024)throw new IllegalArgumentException("extent");if(!e.isDirectory()&&files.put(e.getName(),b)!=null)throw new IllegalArgumentException("duplicate");}}return files;
    }
    /** Separate merged certificate; the exact full source validator remains the miss supplier. */
    static boolean sourceCached(Path dir,String name,Map<String,String> actual,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object coreQueue,Collection<String> resources)throws Exception {
      if(actual!=null&&actual.size()==384){invalidateSource();return source(dir.resolve(name),actual);}
      if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("bsl-shaders",actual)){invalidateSource();return false;}
      String helper=helper();if(helper==null||!sha(helper.getBytes(StandardCharsets.UTF_8)).equals(com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface.HELPER_SHA256)){invalidateSource();return false;}
      return MERGED_SOURCE_CACHE.check(BslMergedLinkageGate.class,dir,name,"bsl-shaders",actual,pack,values,declared,pipeline,world,epoch,java.util.Arrays.asList(coreQueue,helper),resources,()->source(dir.resolve(name),actual));
    }
    public static boolean packPinned(net.irisshaders.iris.shaderpack.ShaderPack pack){
        if(pack==null||!exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()){invalidateSource();return false;}
        try{
            var caps=org.lwjgl.opengl.GL.getCapabilities();
            if(!capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glTexBuffer,caps.glMultiDrawElementsBaseVertex)){invalidateSource();return false;}
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1")){invalidateSource();return false;}
            var stack=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods())stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString());
            if(!com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.stackMatches(stack)){invalidateSource();return false;}
            var options=pack.getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();var actual=new TreeMap<String,String>();
            for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
            for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            String name=Iris.getIrisConfig().getShaderPackName().orElse(null);if(name==null||!Path.of(name).getFileName().toString().equals(name)){invalidateSource();return false;}
            var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
            if(!path.startsWith(dir)||path.equals(dir)){invalidateSource();return false;}
            var client=Minecraft.getInstance();
            return sourceCached(dir,name,actual,pack,values,declared,Iris.getPipelineManager().getPipelineNullable(),client.level,
                com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.generation(),
                java.util.Arrays.asList(com.pp.shaderlod.optimization.OptimizationRuntimeContext.core(),Map.copyOf(Iris.getShaderPackOptionQueue())),
                client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList());

        }catch(Exception|LinkageError e){invalidateSource();return false;}
    }
    public static boolean capabilitiesAllow(int pointerSize,boolean gl41,long texBuffer,long multiDraw){return pointerSize==8&&gl41&&texBuffer!=0&&multiDraw!=0;}
    public static boolean controls(boolean loaded,boolean enabled,LodPreset preset,boolean flag){return loaded&&enabled&&preset==LodPreset.PERFORMANCE&&flag;}
    public static boolean enabled(){
        try{var client=Minecraft.getInstance();boolean admitted=controls(FabricLodConfig.loaded(),FabricLodConfig.snapshot().enabled(),FabricLodConfig.snapshot().preset(),com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN)) &&
            !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()&&client.level!=null&&client.level.dimension()==Level.OVERWORLD&&
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds())&&
            packPinned(Iris.getCurrentPack().orElse(null));if(!admitted)invalidateSource();return admitted;
        }catch(Exception|LinkageError e){invalidateSource();return false;}
    }
    public static boolean trackConstruction(BslMergedProgramAccess owner){return com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(owner,owner::bslDeleteMergedPrograms);}
    /** Optional bounded construction receipt, never an admission bypass or readiness source. */
    public static String captureIdentity(net.irisshaders.iris.shaderpack.ShaderPack pack) {
      try{var o=pack.getShaderPackOptions();var set=o.getOptionSet();var v=o.getOptionValues();var m=new TreeMap<String,String>();set.getBooleanOptions().keySet().forEach(k->m.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));set.getStringOptions().keySet().forEach(k->m.put(k,v.getStringValueOrDefault(k)));String n=Iris.getIrisConfig().getShaderPackName().orElseThrow();var path=Iris.getShaderpacksDirectory().resolve(n);return "packIdentity="+System.identityHashCode(pack)+" source="+com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(path).memberSha256())+" options="+m.size()+" optionsSha="+sha(m.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception|LinkageError e){return "identityRefused="+e.getClass().getSimpleName();}
    }
    public static String helper() {
        try(var in=BslMergedLinkageGate.class.getResourceAsStream("/shaderlod-makeup-merged-normal-region-offset.glsl")) {
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
