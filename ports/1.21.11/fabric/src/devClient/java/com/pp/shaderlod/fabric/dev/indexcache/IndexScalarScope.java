package com.pp.shaderlod.fabric.dev.indexcache;

import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadApiPins;
import com.pp.shaderlod.integration.makeupquad.MakeUpQuadRuntime;
import com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility;
import java.util.*;
import java.nio.file.*;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.minecraft.client.Minecraft;
/** Parent registers only for a frozen native Sildur tuple; reset at every render entry/exit. */
public final class IndexScalarScope {
    private static boolean active,admitted,phaseEnabled;
    private static final boolean WRITER_CLASSES_PINNED=writerClassesPinned();
    private static Object checkedPack,checkedPipeline,checkedValues;
    private static Map<String,Boolean> booleans=Map.of();
    private static Map<String,String> strings=Map.of();
    private static boolean certified;
    public static long hits,avoidedCountReads,nativeScans,nativeCountReads;
    private IndexScalarScope() {}
    private static boolean writerClassesPinned() {
        var pins=Map.of(
            "/net/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegion.class","991f1a8a122015aea6bfde193d3276b20a4fe93cb4197b7b3589060d677fd6ff",
            "/net/irisshaders/iris/compat/sodium/mixin/MixinRenderRegion.class","a446ce6516614f942c5da8869a3d23ab34a99f4b059295658f96aee12d81b41d",
            "/com/pp/shaderlod/integration/makeupquad/mixin/MakeUpQuadMixin.class","533fdee10d8e5636792c85a68ff1bf18ed9207e9d69716435352c867cb872ff0",
            "/com/pp/shaderlod/integration/makeupquad/AdjacentQuadCoalescer.class","22a646d0138e24dc0109a2509a8b9226bcc74421d5aea25a9f55d456e3a69572");
        try {
            for(var pin:pins.entrySet())try(var in=IndexScalarScope.class.getResourceAsStream(pin.getKey())) {
                if(in==null || !pin.getValue().equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))))return false;
            }
            return true;
        }catch(Exception|LinkageError failure){return false;}
    }
    public static boolean active() { return active; }
    public static boolean admitted() { return admitted; }
    public static void setPhase(boolean enabled) { phaseEnabled=enabled;active=false; }
    public static void end() { active=false;admitted=false; }
    public static void begin(boolean opaque) {
        active=false;admitted=false;
        try {
            if(!WRITER_CLASSES_PINNED || !opaque || !Boolean.getBoolean("shaderlod.dev.portIndexScalarCache") ||
                Boolean.getBoolean("shaderlod.rendererSpeedPortR1") || Boolean.getBoolean("shaderlod.experimentalRendererResearch") ||
                !com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() ||
                net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return;
            var client=Minecraft.getInstance();var config=FabricLodConfig.snapshot();
            if(!FabricLodConfig.loaded() || config.enabled() || config.debug() || config.preset()!=LodPreset.PERFORMANCE ||
                client.level==null || client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD ||
                Iris.isFallback() || !IrisApi.getInstance().isShaderPackInUse() || !Iris.getShaderPackOptionQueue().isEmpty() ||
                !"OpenGL".equals(com.mojang.blaze3d.systems.RenderSystem.getDevice().getBackendName()) ||
                !MakeUpQuadApiPins.exactApi() ||
                !MakeUpQuadRuntime.resourcesMatch(client.getResourceManager().listPacks().map(net.minecraft.server.packs.PackResources::packId).toList())) return;
            var mods=new TreeMap<String,String>();for(var mod:FabricLoader.getInstance().getAllMods()) {
                var data=mod.getMetadata();if(!data.getId().equals("java"))mods.put(data.getId(),data.getVersion().getFriendlyString());
            }
            if(!MakeUpQuadRuntime.stackMatches(mods))return;
            var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();
            if(pipeline==null || pipeline.getClass()!=net.irisshaders.iris.pipeline.IrisRenderingPipeline.class)return;
            var options=pack.getShaderPackOptions();var values=options.getOptionValues();
            if(values.getClass()!=MutableOptionValues.class)return;var mutable=(MutableOptionValues)values;
            if(pack!=checkedPack || pipeline!=checkedPipeline || values!=checkedValues ||
                !booleans.equals(mutable.getBooleanValues()) || !strings.equals(mutable.getStringValues())) {
                certified=false;checkedPack=pack;checkedPipeline=pipeline;checkedValues=values;
                booleans=Map.copyOf(mutable.getBooleanValues());strings=Map.copyOf(mutable.getStringValues());
                var actual=new TreeMap<String,String>();var declared=options.getOptionSet();
                for(String key:declared.getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
                for(String key:declared.getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
                var dir=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=dir.resolve(Iris.getCurrentPackName()).normalize();
                certified=archive.startsWith(dir) && !archive.equals(dir) && !Files.isSymbolicLink(archive) &&
                    SildurQuadEligibility.validate(archive,actual).eligible();
            }
            admitted=certified;active=certified && phaseEnabled;
        } catch(Exception|LinkageError unavailable) { active=false; }
    }
}
