package com.pp.shaderlod.fabric.dev;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.pp.shaderlod.fabric.FabricLodConfig;
import com.pp.shaderlod.integration.makeupquad.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;
/** Untimed dev observations only. No private writes, guard replacement, new profile mixing or admission override. */
final class RendererAdmissionDiagnostics {
    private static final Gson GSON=new Gson();
    private RendererAdmissionDiagnostics() {}
    private static Object read(Class<?> type,String field) throws Exception { var member=type.getDeclaredField(field);member.setAccessible(true);return member.get(null); }
    private static String classSource(Object value) {
        if(value==null)return "null";
        try{return value.getClass().getName()+" @ "+value.getClass().getProtectionDomain().getCodeSource().getLocation();}
        catch(Exception unavailable){return value.getClass().getName();}
    }
    static JsonObject observe(Minecraft client) {
        var data=new JsonObject();data.addProperty("observedUtc",java.time.Instant.now().toString());data.addProperty("timingObservation",false);
        try {
            data.add("apiCachedExactResult",GSON.toJsonTree(read(MakeUpQuadApiPins.class,"pinned")));data.addProperty("apiFreshResourceMatch",MakeUpQuadApiPins.resourcesMatch(MakeUpQuadApiPins.class.getClassLoader()));
            var profiles=new JsonObject();data.add("wholeApiProfiles",profiles);
            for(var name:new String[]{"PINS","LOOM_DEVELOPMENT_PINS"}) {
                var profile=new JsonObject();var rows=new JsonArray();profile.add("classHashes",rows);boolean matches=true;
                for(var pin:(String[][])read(MakeUpQuadApiPins.class,name)) {
                    var row=new JsonObject();row.addProperty("resource",pin[0]);row.addProperty("expectedSha256",pin[1]);
                    var loader=MakeUpQuadApiPins.class.getClassLoader();var url=loader.getResource(pin[0]);row.addProperty("resourceUrl",url==null?"null":url.toString());
                    try(var input=loader.getResourceAsStream(pin[0])) {
                        var actual=input==null?"missing":HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
                        row.addProperty("actualSha256",actual);boolean ok=actual.equals(pin[1]);row.addProperty("matches",ok);matches &=ok;
                    }
                    rows.add(row);
                }
                profile.addProperty("all16Match",matches);profiles.add(name,profile);
            }
            var cache=new JsonObject();data.add("readOnlyRuntimeCache",cache);
            for(var name:new String[]{"checkedName","checkedGeneration","checkedResult","checkedStack","checkedBooleans","checkedStrings"})cache.add(name,GSON.toJsonTree(read(MakeUpQuadRuntime.class,name)));
            for(var name:new String[]{"checkedPack","checkedPipeline","checkedValues","checkedWorld"}) {
                Object value=read(MakeUpQuadRuntime.class,name);cache.addProperty(name+"Class",value==null?"null":value.getClass().getName());cache.addProperty(name+"Identity",value==null?0:System.identityHashCode(value));
                if(name.equals("checkedWorld"))cache.addProperty(name+"SameAsCurrent",value==client.level);
                if(name.equals("checkedPipeline"))cache.addProperty(name+"SameAsCurrent",value==Iris.getPipelineManager().getPipelineNullable());
                if(name.equals("checkedPack"))cache.addProperty(name+"SameAsCurrent",value==Iris.getCurrentPack().orElse(null));
            }
            cache.add("apiCachedPinned",GSON.toJsonTree(read(MakeUpQuadApiPins.class,"pinned")));
        } catch(Exception | LinkageError failure){data.addProperty("apiOrCacheDiagnosticUnavailable",failure.toString());}
        try {
            var config=FabricLodConfig.snapshot();data.add("config",GSON.toJsonTree(config));data.addProperty("configLoaded",FabricLodConfig.loaded());
            data.addProperty("configAllows",MakeUpQuadRuntime.configAllows(FabricLodConfig.loaded(),config.enabled(),config.preset()));
            data.addProperty("optIn",Boolean.getBoolean(MakeUpQuadRuntime.RESEARCH_OPT_IN));data.addProperty("lifecycleSuspended",MakeUpQuadLifecycle.suspended());data.addProperty("lifecycleGeneration",MakeUpQuadLifecycle.generation());
            data.addProperty("worldPresent",client.level!=null);data.addProperty("overworldIdentity",client.level!=null && client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD);
            if(client.level!=null)data.addProperty("dimension",client.level.dimension().identifier().toString());
            var resources=client.getResourcePackRepository().getSelectedIds();data.add("selectedRepositoryResourceIds",GSON.toJsonTree(resources));
            data.addProperty("resourcesMatch",MakeUpQuadRuntime.resourcesMatch(resources));
            var pinnedResources=(java.util.Set<?>)read(MakeUpQuadRuntime.class,"NATIVE_RESOURCE_IDS");
            var excluded=new JsonArray();for(var id:resources)if(!pinnedResources.contains(id))excluded.add(id);
            data.add("resourceIdsExcludedByActualPredicate",excluded);
            var stack=new TreeMap<String,String>();FabricLoader.getInstance().getAllMods().forEach(mod->stack.put(mod.getMetadata().getId(),mod.getMetadata().getVersion().getFriendlyString()));
            data.add("actualFullStack",GSON.toJsonTree(stack));data.addProperty("stackMatches",MakeUpQuadRuntime.stackMatches(stack));
            data.addProperty("irisFallback",Iris.isFallback());data.addProperty("shaderPackInUse",net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse());
            try {
                com.pp.shaderlod.integration.RendererBackend.requireOpenGl();data.addProperty("backendGuardAllows",true);
                var backend=((net.irisshaders.iris.mixin.GpuDeviceAccessor)com.mojang.blaze3d.systems.RenderSystem.getDevice()).getBackend();
                data.addProperty("backendName",backend.getDeviceInfo().backendName());data.addProperty("backendClass",backend.getClass().getName());
                var caps=org.lwjgl.opengl.GL.getCapabilities();data.addProperty("pointerSize",org.lwjgl.system.Pointer.POINTER_SIZE);data.addProperty("openGL41",caps.OpenGL41);data.addProperty("multiDrawBaseVertexAvailable",caps.glMultiDrawElementsBaseVertex!=0);
                data.addProperty("capabilitiesAllow",MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex));
            } catch(Exception | LinkageError failure){data.addProperty("backendOrCapabilitiesFailure",failure.toString());}
            var pipeline=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);
            data.addProperty("pipelineClassSource",classSource(pipeline));data.addProperty("exactPipelineClass",pipeline!=null && pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class);
            data.addProperty("packClassSource",classSource(pack));data.addProperty("loadedPackName",Iris.getCurrentPackName());data.addProperty("selectedPackName",Iris.getIrisConfig().getShaderPackName().orElse(""));
            if(pack!=null) {
                var options=pack.getShaderPackOptions();var values=options.getOptionValues();var effective=new TreeMap<String,String>();
                data.addProperty("optionValuesClassSource",classSource(values));data.addProperty("exactMutableValues",values.getClass()==net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class);
                for(var key:options.getOptionSet().getBooleanOptions().keySet())effective.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
                for(var key:options.getOptionSet().getStringOptions().keySet())effective.put(key,values.getStringValueOrDefault(key));
                data.add("effectiveOptions",GSON.toJsonTree(effective));data.addProperty("effectiveOptionCount",effective.size());
                if(values instanceof net.irisshaders.iris.shaderpack.option.values.MutableOptionValues mutable) {
                    data.add("rawBooleanOverrides",GSON.toJsonTree(mutable.getBooleanValues()));data.add("rawStringOverrides",GSON.toJsonTree(mutable.getStringValues()));
                }
                var directory=Iris.getShaderpacksDirectory().toAbsolutePath().normalize();var archive=directory.resolve(Iris.getCurrentPackName()).normalize();
                boolean safe=archive.startsWith(directory) && !archive.equals(directory);
                data.addProperty("archivePath",archive.toString());data.addProperty("archivePathSafe",safe);
                if(safe) {
                    if(Files.isRegularFile(archive))data.addProperty("archiveSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
                    data.add("makeUpCertificate",GSON.toJsonTree(MakeUpQuadEligibility.validate(archive,effective)));
                    data.add("sildurCertificate",GSON.toJsonTree(com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.validate(archive,effective)));
                }
            }
        } catch(Exception | LinkageError failure){data.addProperty("diagnosticUnavailable",failure.toString());}
        return data;
    }
}
