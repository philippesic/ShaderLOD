package com.pp.shaderlod.experiment.makeupmerged;
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
/** Admission only for one original MakeUp/native100 dev runtime. No frame-cached settings. */
public final class MergedLinkageGate {
    public static final String ARCHIVE_SHA="0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
    public static final String OPTIONS_SHA="0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb";
    private static Boolean apiPinned;
    private static final Set<String> diagnostics=new HashSet<>();
    public static synchronized void noteOnce(String reason) {
        if(diagnostics.add(reason))System.out.println("[ShaderLOD merged] gate="+reason);
    }
    private static Object certifiedPack;
    private static String certifiedName;
    private static Map<String,String> expected;
    private MergedLinkageGate() {}
    public static String sha(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
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
    public static boolean packPinned() {
        if(!Boolean.getBoolean("shaderlod.dev.makeupMergedSubmissionR1")){noteOnce("pack flag=false");return false;}
        if(!exactApi()){noteOnce("pack api=false");return false;}
        if(Iris.isFallback()){noteOnce("pack irisFallback=true");return false;}
        try {
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1") || !ModList.get().isLoaded("shaderlod") ||
                !ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253")){noteOnce("pack runtime-or-main=false");return false;}
            for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(ModList.get().isLoaded(id)){noteOnce("pack excluded-mod="+id);return false;}
            Object pack=Iris.getCurrentPack().orElseThrow();String name=Iris.getCurrentPackName();
            if(pack!=certifiedPack || !name.equals(certifiedName)) {
                certifiedPack=null;certifiedName=null;
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
                if(!path.startsWith(dir) || path.equals(dir) || !ARCHIVE_SHA.equals(sha(Files.readAllBytes(path)))){noteOnce("pack archive=false");return false;}
                byte[] bytes=Files.readAllBytes(Path.of(System.getProperty("shaderlod.dev.makeupMergedOptionsPin")));
                if(!OPTIONS_SHA.equals(sha(bytes))){noteOnce("pack options-file-hash=false");return false;}
                expected=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),new TypeToken<Map<String,String>>(){}.getType());
                if(expected==null || expected.size()!=100){noteOnce("pack expected-map-size=false");return false;}
                certifiedPack=pack;certifiedName=name;
            }
            var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();
            var actual=new TreeMap<String,String>();
            for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
            for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            boolean matched=optionsMatch(actual,expected);noteOnce("pack actual-map="+matched+" count="+actual.size());return matched;
        }catch(Exception|LinkageError unavailable){noteOnce("pack exception="+unavailable.getClass().getSimpleName()+" message="+unavailable.getMessage());return false;}
    }
    public static boolean optionsMatch(Map<String,String> actual,Map<String,String> expected) {return actual!=null && expected!=null && expected.size()==100 && actual.equals(expected);}
    public static boolean enabled() {
        var client=Minecraft.getInstance();
        return ShaderLodConfig.SPEC.isLoaded() && ShaderLodConfig.snapshot().enabled() && ShaderLodConfig.PRESET.get()==LodPreset.PERFORMANCE &&
            client.level!=null && client.level.dimension()==Level.OVERWORLD && packPinned() &&
            com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime.enabled();
    }
    // Only pending derivative owners; no native geometry or cross-frame certification is retained.
    private static final ThreadLocal<Construction> constructing=new ThreadLocal<>();
    public static Construction beginConstruction() {
        if(constructing.get()!=null)throw new IllegalStateException("nested merged pipeline construction");
        var scope=new Construction();constructing.set(scope);noteOnce("construction entered");return scope;
    }
    public static boolean trackConstruction(MergedProgramAccess owner) {
        var scope=constructing.get();if(scope==null){noteOnce("clone construction-scope=false");return false;}
        if(!scope.owners.contains(owner))scope.owners.add(owner);return true;
    }
    public static final class Construction implements AutoCloseable {
        private final List<MergedProgramAccess> owners=new ArrayList<>();
        private boolean committed,closed;
        private Construction() {}
        public void commit(){committed=true;}
        public void close() {
            if(closed)return;closed=true;constructing.remove();
            if(!committed)for(var owner:owners)try{owner.deleteMergedPrograms();}
                catch(RuntimeException|LinkageError cleanup){System.out.println("[ShaderLOD merged] derivative cleanup failed: "+cleanup.getClass().getSimpleName());}
            owners.clear();
        }
    }
    public static String helper() {
        try(var in=MergedLinkageGate.class.getResourceAsStream("/shaderlod-makeup-merged-region-offset.glsl")) {
            return in==null?null:new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }catch(Exception unavailable){return null;}
    }
    /** Gate every linkage seam before Mixin application, including private Builder accessor. */
    public static final class Plugin implements org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin {
        public boolean shouldApplyMixin(String target,String mixin){boolean flag=Boolean.getBoolean("shaderlod.dev.makeupMergedSubmissionR1");boolean apply=flag && exactApi();noteOnce("linkage-plugin mixin="+mixin+" flag="+flag+" apply="+apply);return apply;}
        public void onLoad(String p){noteOnce("linkage-plugin loaded package="+p); } public String getRefMapperConfig(){return null;}
        public void acceptTargets(Set<String> a,Set<String> b){} public List<String> getMixins(){return null;}
        public void preApply(String t,org.objectweb.asm.tree.ClassNode n,String m,org.spongepowered.asm.mixin.extensibility.IMixinInfo info){}
        public void postApply(String t,org.objectweb.asm.tree.ClassNode n,String m,org.spongepowered.asm.mixin.extensibility.IMixinInfo info){}
    }

}
