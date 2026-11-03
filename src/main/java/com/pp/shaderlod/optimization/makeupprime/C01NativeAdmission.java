package com.pp.shaderlod.optimization.makeupprime;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.irisshaders.iris.Iris;

/** Finite private native-pack C01 cost experiment. No public or persisted enable path. */
final class C01NativeAdmission {
    private C01NativeAdmission() {}
    private static final String ARCHIVE="0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
    // Actual linked Sodium SOLID stages, identical in all six r54 graph exports.
    private static final String VERTEX="d9185681f940eb8f1ee4cb9b317cb2467aa9919de94b05b1f457d4f51221daec";
    private static final String FRAGMENT="84c0033ff28de958c6c47ec2e6f170d9eb5693f27b88e2acb5ab1c270d400903";
    private static Object pack,pipeline,values,world;
    private static long epoch=-1;
    private static String selected;
    private static boolean admitted,lastCombined;
    static boolean qualified() {
        if(!Boolean.getBoolean("shaderlod.dev.c01Bounds")||!Boolean.getBoolean("shaderlod.dev.c01NativeR66"))return false;
        if(!C01Platform.allowed(System.getProperty("os.name"),System.getProperty("os.arch"),
            org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_RENDERER),
            org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VERSION)))return false;
        if(!net.minecraft.SharedConstants.getCurrentVersion().getName().equals("1.21.1")
            ||!net.neoforged.fml.ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion().toString().equals("21.1.253"))return false;
        var c=net.minecraft.client.Minecraft.getInstance();
        if(c.level==null||c.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty())return false;
        var requests=com.pp.shaderlod.optimization.OptimizationConfig.snapshot();
        boolean combined=com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.pair(requests);
        for(var id:com.pp.shaderlod.optimization.OptimizationId.values())if(requests.enabled(id)&&!combined)return false;
        if(combined&&(!com.pp.shaderlod.ShaderLodConfig.snapshot().enabled()
            ||com.pp.shaderlod.ShaderLodConfig.PRESET.get()!=com.pp.shaderlod.LodPreset.PERFORMANCE
            ||com.pp.shaderlod.integration.makeupquad.MakeUpQuadLifecycle.suspended()))return false;
        var current=Iris.getCurrentPack().orElse(null);var currentPipeline=Iris.getPipelineManager().getPipelineNullable();
        if(current==null||currentPipeline==null)return false;
        var options=current.getShaderPackOptions();long currentEpoch=MakeUpDepthPriming.sourceEpoch();String name=Iris.getCurrentPackName();
        if(combined==lastCombined&&current==pack&&currentPipeline==pipeline&&options.getOptionValues()==values&&c.level==world&&epoch==currentEpoch&&Objects.equals(selected,name))return admitted;
        admitted=false;pack=current;pipeline=currentPipeline;values=options.getOptionValues();world=c.level;epoch=currentEpoch;selected=name;lastCombined=combined;
        try {
            if(!com.pp.shaderlod.optimization.makeupprime.mixin.DepthMixinPlugin.exactApi())return false;
            for(String id:new String[]{"distanthorizons","voxy","create","flywheel"})if(net.neoforged.fml.ModList.get().isLoaded(id))return false;
            if(name==null||!Path.of(name).getFileName().toString().equals(name))return false;
            Path file=Iris.getShaderpacksDirectory().resolve(name);
            if(Files.isSymbolicLink(file))return false;
            var actual=new TreeMap<String,String>();
            options.getOptionSet().getBooleanOptions().keySet().forEach(k->actual.put(k,Boolean.toString(options.getOptionValues().getBooleanValueOrDefault(k))));
            options.getOptionSet().getStringOptions().keySet().forEach(k->actual.put(k,options.getOptionValues().getStringValueOrDefault(k)));
            if(combined)return admitted=combinationOptions(actual)
                &&"makeup-ultra-fast-shaders".equals(com.pp.shaderlod.packs.PackSelectionService.verifyGeneratedRendererGraph(file,actual));
            if(!ARCHIVE.equals(com.pp.shaderlod.optimization.SourceArchiveDigest.sha256(file)))return false;
            JsonObject expected;
            try(var in=C01NativeAdmission.class.getResourceAsStream("/assets/shaderlod/makeupquad/native100.json")){
                if(in==null)return false;expected=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            }
            return admitted=expected.size()==100&&expected.equals(new Gson().toJsonTree(actual));
        }catch(Exception|LinkageError refused){return false;}
    }
    /** Additional private benchmark tuple only; every native value and unrelated option remains guarded.
     * Linked SOLID stage pins, depth/raster/query checks and original route are unchanged. */
    static boolean combinationOptions(Map<String,String> actual) {
        return com.pp.shaderlod.optimization.makeupexact.MakeUpCombinationGraph.enabled()
            &&com.pp.shaderlod.optimization.framecopy.MakeUpLeanGraph.profile(actual);
    }
    static boolean stageAllowed(String text,boolean vertex) {
        if(text==null)return false;
        try{return (vertex?VERTEX:FRAGMENT).equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
