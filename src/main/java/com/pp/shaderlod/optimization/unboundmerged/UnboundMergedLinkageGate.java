package com.pp.shaderlod.optimization.unboundmerged;
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
/** Normal NF1.21.1: exact original native398 or current generated406 source and pinned API. */
public final class UnboundMergedLinkageGate {
    private static Boolean apiPinned;
    private static final Set<String> diagnostics=new HashSet<>();
    public static synchronized void noteOnce(String reason) {
        if(diagnostics.add(reason))System.out.println("[ShaderLOD merged] gate="+reason);
    }
    private static final com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache MERGED_SOURCE_CACHE=new com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache();
    public static void invalidateSource(){MERGED_SOURCE_CACHE.invalidate();}
    private UnboundMergedLinkageGate() {}
    public static String sha(byte[] value) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    public static String status(){return "Unbound NF1211 merged SOLID/CUTOUT normal consumer; per-pass regional copy/TBO; actual GL pending";}
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
        try {for(var pin:pins)try(var in=UnboundMergedLinkageGate.class.getClassLoader().getResourceAsStream(pin[0])) {
            if(in==null){noteOnce("linkage-api missing="+pin[0]+" loader="+UnboundMergedLinkageGate.class.getClassLoader().getClass().getName());return apiPinned=false;}
            String actual=sha(in.readAllBytes());if(!pin[1].equals(actual)){noteOnce("linkage-api mismatch="+pin[0]+" actual="+actual+" expected="+pin[1]);return apiPinned=false;}
        }noteOnce("linkage-api admitted");return apiPinned=true;}catch(Exception|LinkageError unavailable){noteOnce("linkage-api exception="+unavailable.getClass().getSimpleName());return apiPinned=false;}
    }
    public static boolean source(Path selected,Map<String,String> actual) {
      if(actual==null)return false;
      if(actual.size()==398)return com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.optionsMatch(actual)&&com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(selected,actual).eligible();
      if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-unbound",actual))return false;
      try {
       if(!"14ee368b6f2890b07cc8b31ebab56da0dfd5e2bc237784768ae745b9f2bec0d7".equals(com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(selected).memberSha256())))return false;
       if(!"complementary-unbound".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(selected,actual)))return false;
       try(var in=UnboundMergedLinkageGate.class.getResourceAsStream("/assets/shaderlod/adapters/patches/adapters/complementary-unbound.json")){if(in==null||!sha(in.readAllBytes()).equals("d19db58ed1901563a7ad064d3a49c175afb29fd1e46062c08db08929bf0a51fa"))return false;}
       var generated=archive(selected);var marker=com.google.gson.JsonParser.parseString(new String(generated.get("shaderlod-selection.json"),StandardCharsets.UTF_8)).getAsJsonObject();String name=marker.get("originalFile").getAsString();if(!Path.of(name).getFileName().toString().equals(name))return false;
       Path original=selected.toAbsolutePath().normalize().getParent().resolve(name);
       return com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.validate(original,com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions()).eligible()&&com.pp.shaderlod.integration.makeupquad.NamedVertexContract.accepts("complementary-unbound",archive(original),generated);
      }catch(Exception|LinkageError failure){return false;}
    }
    private static Map<String,byte[]> archive(Path p)throws Exception{
        if(!Files.isRegularFile(p,java.nio.file.LinkOption.NOFOLLOW_LINKS)||Files.size(p)>128L*1024*1024)throw new IllegalArgumentException("archive");
        var files=new TreeMap<String,byte[]>();long total=0;
        try(var zip=new java.util.zip.ZipInputStream(Files.newInputStream(p))){java.util.zip.ZipEntry e;while((e=zip.getNextEntry())!=null){byte[] b=zip.readNBytes(128*1024*1024+1);total+=b.length;if(total>128L*1024*1024)throw new IllegalArgumentException("extent");if(!e.isDirectory()&&files.put(e.getName(),b)!=null)throw new IllegalArgumentException("duplicate");}}return files;
    }
    /** Distinct merged certificate; full exact validator is the cache miss supplier. */
    static boolean sourceCached(Path dir,String name,Map<String,String> actual,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object coreQueue,Collection<String> resources)throws Exception {
      if(actual!=null&&actual.size()==398){invalidateSource();return source(dir.resolve(name),actual);}
      if(!com.pp.shaderlod.integration.makeupquad.GeneratedRendererOptionContract.accepts("complementary-unbound",actual)){invalidateSource();return false;}
      String helper=helper();if(helper==null||!sha(helper.getBytes(StandardCharsets.UTF_8)).equals(com.pp.shaderlod.optimization.makeupmerged.MakeupMergedVertexInterface.HELPER_SHA256)){invalidateSource();return false;}
      return MERGED_SOURCE_CACHE.check(UnboundMergedLinkageGate.class,dir,name,"complementary-unbound",actual,pack,values,declared,pipeline,world,epoch,java.util.Arrays.asList(coreQueue,helper),resources,()->source(dir.resolve(name),actual));
    }
    public static boolean packPinned(net.irisshaders.iris.shaderpack.ShaderPack pack){
  return com.pp.shaderlod.optimization.sourcecache.IrisSourceLifecycle.proof(java.util.Arrays.asList(UnboundMergedLinkageGate.class,"packPinned",pack),()->{
        if(pack==null||!exactApi()||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()){invalidateSource();return false;}
        try{
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1")){invalidateSource();return false;}
            var stack=new TreeMap<String,String>();for(var mod:ModList.get().getMods())stack.put(mod.getModId(),mod.getVersion().toString());
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

        }catch(Exception|LinkageError e){{invalidateSource();return false;}}
    
  });
 }
    public static boolean controls(boolean loaded,boolean enabled,LodPreset preset,boolean flag){return loaded&&enabled&&preset==LodPreset.PERFORMANCE&&flag;}
    public static boolean enabled(){
        try{var client=Minecraft.getInstance();boolean admitted=controls(ShaderLodConfig.SPEC.isLoaded(),ShaderLodConfig.snapshot().enabled(),ShaderLodConfig.PRESET.get(),com.pp.shaderlod.optimization.OptimizationConfig.requested(com.pp.shaderlod.optimization.OptimizationId.MERGED_TERRAIN)) &&
            !com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()&&client.level!=null&&client.level.dimension()==Level.OVERWORLD&&
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.resourcesMatch(client.getResourcePackRepository().getSelectedIds())&&
            packPinned(Iris.getCurrentPack().orElse(null));if(!admitted)invalidateSource();return admitted;
        }catch(Exception|LinkageError e){invalidateSource();return false;}
    }
    public static boolean trackConstruction(UnboundMergedProgramAccess owner){return com.pp.shaderlod.optimization.makeupmerged.MergedLinkageGate.trackConstructionCleanup(owner,owner::unboundDeleteMergedPrograms);}
    /** Optional bounded construction receipt, never an admission bypass or readiness source. */
    public static String captureIdentity(net.irisshaders.iris.shaderpack.ShaderPack pack) {
      try{var o=pack.getShaderPackOptions();var set=o.getOptionSet();var v=o.getOptionValues();var m=new TreeMap<String,String>();set.getBooleanOptions().keySet().forEach(k->m.put(k,Boolean.toString(v.getBooleanValueOrDefault(k))));set.getStringOptions().keySet().forEach(k->m.put(k,v.getStringValueOrDefault(k)));String n=Iris.getIrisConfig().getShaderPackName().orElseThrow();var path=Iris.getShaderpacksDirectory().resolve(n);return "packIdentity="+System.identityHashCode(pack)+" source="+com.pp.shaderlod.packs.SupportedPackCatalog.shaderSourceDigest(com.pp.shaderlod.integration.PackSourceReader.read(path).memberSha256())+" options="+m.size()+" optionsSha="+sha(m.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception|LinkageError e){return "identityRefused="+e.getClass().getSimpleName();}
    }
    public static String helper() {
        try(var in=UnboundMergedLinkageGate.class.getResourceAsStream("/shaderlod-makeup-merged-normal-region-offset.glsl")) {
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
