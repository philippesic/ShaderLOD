package com.pp.shaderlod.experiment.makeupquad;
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
public final class QuadCoalescerGate {
    public static final String ARCHIVE_SHA="0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
    public static final String OPTIONS_SHA="0c11f31b3d5e51d9d1bff56a84ac13a3f1265472f7313997a493c74435a418bb";
    private static Boolean apiPinned;
    private static Object certifiedPack;
    private static String certifiedName;
    private static Map<String,String> expected;
    private static Object checkedPipeline, checkedPack;
    private static boolean checkedResult;
    public static boolean modeSupported() { String m=System.getProperty("shaderlod.dev.makeupQuadCoalescerR1", "off"); return m.equals("control") || m.equals("candidate"); }
    public static boolean candidate() { return "candidate".equals(System.getProperty("shaderlod.dev.makeupQuadCoalescerR1")); }
    private QuadCoalescerGate() {}
    public static String sha(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    public static boolean exactApi() {
        if(apiPinned!=null)return apiPinned;
        String[][] pins={{"net/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch.class","8b355f8255d0c6d1fd5e9bf1a89d9dacb2b494cabacdfbb1059201cb0dde3d8c"},{"net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.class","e57ee69430ac4a79ab4608e2a3341b5af2ce08c5ecc84406502a9d0379a6333f"},{"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.class","e567edab9e84facef6da76fe71ad1d4de3d765a9f57e12b624c935b49964a3a2"},{"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType.class","d1448c4dfa83c03d9a291f375fb5893478efe1ceb7fc07b1b11321e1236cf1b2"},{"net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer$IndexType$2.class","846f1fa106f3e35c0790922cc624c5e990ed49aa05f0b9c939e50530bc347f09"},{"net/irisshaders/iris/pipeline/programs/SodiumPrograms.class","9fe2bf60a9c6140c90bcf09aeae23e38365f029660385d4dae919fbe6e7481cf"},{"net/irisshaders/iris/pipeline/IrisRenderingPipeline.class","95b3eb4425306964328cf771c7cc79b8bfffdf90e15bce813ad1a7bd38f34579"},{"net/irisshaders/iris/pipeline/transform/transformer/SodiumTransformer.class","17bbfb7cc7bd5da043f508924c6485c2bc550a123dad94165cc033610803f8ae"},{"net/irisshaders/iris/compat/sodium/mixin/MixinDefaultChunkRenderer.class","dc0564352c37ba3a01919bbe185fb3e894998837950d32fe8f8e68837370c51f"}};
        try {for(var pin:pins)try(var in=QuadCoalescerGate.class.getClassLoader().getResourceAsStream(pin[0])) {
            if(in==null || !pin[1].equals(sha(in.readAllBytes())))return apiPinned=false;
        }return apiPinned=true;}catch(Exception|LinkageError unavailable){return apiPinned=false;}
    }
    public static boolean packPinned() {
        if(!modeSupported() || !exactApi() || Iris.isFallback())return false;
        try {
            if(!SharedConstants.getCurrentVersion().getName().equals("1.21.1") || !ModList.get().isLoaded("shaderlod") ||
                !ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253"))return false;
            var stack=new TreeMap<String,String>();
            for(var mod:ModList.get().getMods()) stack.put(mod.getModId(),mod.getVersion().toString());
            if(!stackMatches(stack)) return false;
            Object pack=Iris.getCurrentPack().orElseThrow();String name=Iris.getCurrentPackName();
            if(pack!=certifiedPack || !name.equals(certifiedName)) {
                certifiedPack=null;certifiedName=null;
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var path=dir.resolve(name).normalize();
                if(!path.startsWith(dir) || path.equals(dir) || !ARCHIVE_SHA.equals(sha(Files.readAllBytes(path))))return false;
                byte[] bytes=Files.readAllBytes(Path.of(System.getProperty("shaderlod.dev.makeupQuadCoalescerOptionsPin")));
                if(!OPTIONS_SHA.equals(sha(bytes)))return false;
                expected=new Gson().fromJson(new String(bytes,StandardCharsets.UTF_8),new TypeToken<Map<String,String>>(){}.getType());
                if(expected==null || expected.size()!=100)return false;
                certifiedPack=pack;certifiedName=name;
            }
            var options=Iris.getCurrentPack().orElseThrow().getShaderPackOptions();var declared=options.getOptionSet();var values=options.getOptionValues();
            var actual=new TreeMap<String,String>();
            for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
            for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            return optionsMatch(actual,expected);
        }catch(Exception|LinkageError unavailable){return false;}
    }
    public static final Map<String,String> NATIVE_STACK=Map.ofEntries(
        Map.entry("fabric_api_base","0.4.42+d1308ded19"),
        Map.entry("fabric_block_view_api_v2","1.0.10+9afaaf8c19"),
        Map.entry("fabric_renderer_api_v1","3.4.0+acb05a3919"),
        Map.entry("fabric_rendering_data_attachment_v1","0.3.48+73761d2e19"),
        Map.entry("iris","1.8.12-snapshot+mc1.21.1-local"), Map.entry("minecraft","1.21.1"),
        Map.entry("neoforge","21.1.253"), Map.entry("shaderlod","0.1.0-dev"),
        Map.entry("shaderlod_dev","0.1.0"), Map.entry("sodium","0.6.13+mc1.21.1"));
    public static boolean stackMatches(Map<String,String> stack) { return NATIVE_STACK.equals(stack); }
    public static boolean optionsMatch(Map<String,String> actual,Map<String,String> expected) {return actual!=null && expected!=null && expected.size()==100 && actual.equals(expected);}
    public static boolean identityPinned() {
        try {
            Object pipeline=Iris.getPipelineManager().getPipelineNullable();
            Object pack=Iris.getCurrentPack().orElse(null);
            if(pipeline==null || pack==null || pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class) return false;
            var caps=org.lwjgl.opengl.GL.getCapabilities();
            if(!capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex)) return false;
            if(pipeline!=checkedPipeline || pack!=checkedPack) {
                checkedPipeline=pipeline; checkedPack=pack; checkedResult=false;
                checkedResult=packPinned();
                if(checkedResult && ((net.irisshaders.iris.pipeline.IrisRenderingPipeline)pipeline).getSodiumPrograms().getClass()!=net.irisshaders.iris.pipeline.programs.SodiumPrograms.class) checkedResult=false;
            }
            return checkedResult && !Iris.isFallback();
        } catch(Exception|LinkageError unavailable) { return false; }
    }
    public static boolean configAllows(boolean loaded,boolean enabled,LodPreset preset) {
        return loaded && enabled && preset==LodPreset.PERFORMANCE;
    }
    public static boolean capabilitiesAllow(int pointerSize,boolean gl41,long multiDrawFunction) {
        return pointerSize==8 && gl41 && multiDrawFunction!=0;
    }
    public static boolean enabled() {
        if (!modeSupported() || !ModList.get().isLoaded("shaderlod")) return false;
        var client=Minecraft.getInstance();
        return ShaderLodConfig.SPEC.isLoaded() && configAllows(true, ShaderLodConfig.ENABLED.get(), ShaderLodConfig.PRESET.get()) &&
            !Boolean.getBoolean("shaderlod.dev.makeupEarlyAlphaR1") && !Boolean.getBoolean("shaderlod.dev.makeupMergedSubmissionR1") && !Boolean.getBoolean("shaderlod.dev.unboundDepthPrimingR2") &&
            client.level!=null && client.level.dimension()==Level.OVERWORLD && modeSupported() && identityPinned();
    }
}
