package com.pp.shaderlod.dev.bindingfix;
import java.util.*;
import com.pp.shaderlod.dev.mixin.PortGlRenderPassAccessor;
import net.caffeinemc.mods.sodium.mixin.core.GlRenderPassAccessor;
import com.pp.shaderlod.integration.makeupquad.*;
import net.irisshaders.iris.Iris;
/** Single render-thread bounded context around original executeDraws; no global descriptor mutation. */
public final class BindingInputContext {
    public record Context(Object pipeline,Object[] boundSlots) {}
    private static Context current;
    private static final BindingInputCertificateCache CERTIFICATE=new BindingInputCertificateCache();
    public static long certificateRefreshes,certificateReuses,sourceStampReads;
    private static Boolean checkedStack;
    private static Map<String,String> actualStack=Map.of();
    private static Object stampWorld;private static long stampTick=Long.MIN_VALUE,stampGeneration=Long.MIN_VALUE;
    private static String stampName;private static Object sourceStamp;
    private static int nativeOptionCount;private static String nativeAA,certificateReason="not-observed";
    private record SourceStamp(long size,java.nio.file.attribute.FileTime modified,Object fileKey) {}
    /** File metadata once per actual world tick/generation/name; archive SHA is recomputed only after changed input. */
    private static Object observedSourceStamp(net.minecraft.client.Minecraft client,String name,long generation) throws Exception {
        long tick=client.level.getGameTime();
        if(stampWorld!=client.level || tick!=stampTick || generation!=stampGeneration || !Objects.equals(stampName,name)) {
            var archive=Iris.getShaderpacksDirectory().resolve(name);
            var attributes=java.nio.file.Files.readAttributes(archive,java.nio.file.attribute.BasicFileAttributes.class,java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if(!attributes.isRegularFile() || attributes.isSymbolicLink())throw new IllegalStateException("Original archive file unavailable/aliased");
            sourceStamp=new SourceStamp(attributes.size(),attributes.lastModifiedTime(),attributes.fileKey());sourceStampReads++;
            stampWorld=client.level;stampTick=tick;stampGeneration=generation;stampName=name;
        }
        return sourceStamp;
    }
    private static final com.google.gson.Gson DIAGNOSTIC_GSON=new com.google.gson.Gson();
    private static boolean shadowGateLogged,ordinaryGateLogged,shadowGetterLogged,ordinaryGetterLogged,attemptOpen;
    private static Object attemptedPipeline;
    private static boolean noBoundPassThroughLogged;
    private static String attemptEntry="unscoped";
    public record Snapshot(Context context,boolean attemptOpen,Object attemptedPipeline,String attemptEntry) {}
    public static Snapshot snapshot() { return new Snapshot(current,attemptOpen,attemptedPipeline,attemptEntry); }
    public static void restore(Snapshot prior) {
        current=prior.context();attemptOpen=prior.attemptOpen();attemptedPipeline=prior.attemptedPipeline();attemptEntry=prior.attemptEntry();
    }
    public static Context enter(Object pass,String entry) throws Exception { attemptEntry=entry;return enter(pass); }

    private static boolean predicate(com.google.gson.JsonObject data,String name,boolean value) {
        if(data!=null) {data.addProperty(name,value);if(!value && !data.has("firstFailedPredicate"))data.addProperty("firstFailedPredicate",name);}
        return value;
    }
    private static boolean shadow() { return net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered(); }

    public static long correctedQueries,shadowCorrectedQueries,ordinaryCorrectedQueries;
    public static long glFunctionEntries,shadowGlFunctionEntries,ordinaryGlFunctionEntries,completedDrawBodies,shadowCompletedDrawBodies,ordinaryCompletedDrawBodies;
    public static int lastIrisDescriptorSlots,lastCorrectedDescriptorSlots,lastIrisSlotZeroIdentity,lastCorrectedSlotZeroIdentity;
    private BindingInputContext() {}
    public static Context current() { return current; }
    public static Context enter(Object pass) throws Exception {
        Context prior=current;current=null;attemptOpen=true;attemptedPipeline=null;
        var client=net.minecraft.client.Minecraft.getInstance();boolean shadow=shadow();
        boolean record=Boolean.getBoolean("shaderlod.dev.portBindingInputFix") && client.level!=null && !(shadow?shadowGateLogged:ordinaryGateLogged);
        var data=record?new com.google.gson.JsonObject():null;
        if(data!=null) {if(shadow)shadowGateLogged=true;else ordinaryGateLogged=true;
            data.addProperty("observedUtc",java.time.Instant.now().toString());data.addProperty("shadowPass",shadow);
            data.addProperty("entry",attemptEntry+" outer wrapper before native body");data.addProperty("validationPolicyMutated",false);
            data.addProperty("passClass",pass.getClass().getName());data.addProperty("helperModule",String.valueOf(BindingInputContext.class.getModule().getName()));}
        try {
            if(data!=null)try {
                if(pass instanceof GlRenderPassAccessor access) {
                    var candidate=access.getPipeline();attemptedPipeline=candidate==null?null:candidate.info();
                    data.addProperty("actualPassPipelineIdentity",System.identityHashCode(attemptedPipeline));
                    data.addProperty("actualPassPipelineInfoClass",attemptedPipeline==null?"absent":attemptedPipeline.getClass().getName());
                }
                if(pass instanceof PortGlRenderPassAccessor access) {
                    var slots=access.shaderlod$vertexBuffers();data.addProperty("actualPassVertexSlotCount",slots==null?-1:slots.length);
                    var bound=new com.google.gson.JsonArray();if(slots!=null)for(int i=0;i<slots.length;i++)if(slots[i]!=null)bound.add(i);
                    data.add("actualPassNonNullVertexSlots",bound);
                }
            }catch(RuntimeException|LinkageError unavailable){data.addProperty("readonlyPassObservationUnavailable",unavailable.toString());}
            boolean admitted=admitted(data);if(data!=null)data.addProperty("admitted",admitted);
            if(!admitted)return prior;
            if(predicate(data,"knownPassClass",BindingInputContract.knownPassClass(pass.getClass().getName()))) {
                var compiled=((GlRenderPassAccessor)pass).getPipeline();
                var buffers=((PortGlRenderPassAccessor)pass).shaderlod$vertexBuffers();
                attemptedPipeline=compiled==null?null:compiled.info();
                if(data!=null) {data.addProperty("pipelineIdentity",System.identityHashCode(attemptedPipeline));
                    data.addProperty("pipelineInfoClass",attemptedPipeline==null?"absent":attemptedPipeline.getClass().getName());
                    data.addProperty("vertexSlotCount",buffers==null?-1:buffers.length);
                    var bound=new com.google.gson.JsonArray();if(buffers!=null)for(int i=0;i<buffers.length;i++)if(buffers[i]!=null)bound.add(i);data.add("nonNullVertexSlots",bound);}
                if(!predicate(data,"exactCompiledPipeline",compiled!=null && compiled.getClass()==com.mojang.blaze3d.opengl.GlRenderPipeline.class) ||
                    !predicate(data,"exactPipelineInfo",compiled.info().getClass()==com.mojang.blaze3d.pipeline.RenderPipeline.class)) {
                    if(data!=null)data.addProperty("adapterScope","unsupported-pipeline-native-pass-through");
                    return prior;
                }
                if(!predicate(data,"nativeVertexSlotArray",buffers!=null && buffers.length==16)) {
                    if(data!=null)data.addProperty("adapterScope","unsupported-slot-array-native-pass-through");
                    return prior;
                }
                // Collection draws set slots after entry. Keep the live array; sole0 proof belongs at getter consumption.
                if(data!=null) {
                    data.addProperty("soleBoundSlotZeroAtEntry",BindingInputContract.soleBoundSlotZero(buffers));
                    data.addProperty("adapterScope","exact-native-slot-array-shape-eligibility-deferred-to-getter");
                }
                if(BindingInputContract.noBoundSlots(buffers) && !noBoundPassThroughLogged) {
                    noBoundPassThroughLogged=true;
                    org.slf4j.LoggerFactory.getLogger("shaderlod-dev").info(
                        "ShaderLOD binding input deferred scope: entry={}, pipeline={}, slots=16, nonNullAtEntry=[], correctionRequiresGetterShape=true, nativeBodyUnchanged=true",
                        attemptEntry,compiled.info().getLocation());
                }
                current=new Context(compiled.info(),buffers);
            }
            return prior;
        } catch(Exception|LinkageError failure) {
            if(data!=null)data.addProperty("unavailable",failure.toString());throw failure;
        } finally {
            if(data!=null) {
                data.addProperty("contextPresent",current!=null);data.addProperty("contextPipelineIdentity",current==null?0:System.identityHashCode(current.pipeline()));
                data.add("classloaderPins",DIAGNOSTIC_GSON.toJsonTree(BindingInputPins.observations()));
                org.slf4j.LoggerFactory.getLogger("shaderlod-dev").info("ShaderLOD binding input first gate: {}",data);
            }
        }
    }
    public static void exit(Context prior) { current=prior;attemptOpen=false;attemptedPipeline=null; }
    public static void observeGetter(Object pipeline,Object[] nativeBindings,Object[] rewritten) {
        if(!attemptOpen || net.minecraft.client.Minecraft.getInstance().level==null)return;
        boolean shadow=shadow();if(shadow?shadowGetterLogged:ordinaryGetterLogged)return;
        if(shadow)shadowGetterLogged=true;else ordinaryGetterLogged=true;
        var data=new com.google.gson.JsonObject();data.addProperty("observedUtc",java.time.Instant.now().toString());data.addProperty("shadowPass",shadow);
        data.addProperty("entry","outer getter after original method and Iris callbacks");data.addProperty("drawEntry",attemptEntry);data.addProperty("pipelineIdentity",System.identityHashCode(pipeline));
        data.addProperty("contextPresent",current!=null);data.addProperty("contextPipelineIdentity",current==null?0:System.identityHashCode(current.pipeline()));
        data.addProperty("contextPipelineMatches",current!=null && current.pipeline()==pipeline);data.addProperty("attemptedPipelineIdentity",System.identityHashCode(attemptedPipeline));
        data.addProperty("nativeBindingLength",nativeBindings==null?-1:nativeBindings.length);data.addProperty("originalReturnedLength",rewritten==null?-1:rewritten.length);
        data.addProperty("nativeSlotZeroIdentity",nativeBindings==null||nativeBindings.length==0?0:System.identityHashCode(nativeBindings[0]));
        data.addProperty("returnedSlotZeroIdentity",rewritten==null||rewritten.length==0?0:System.identityHashCode(rewritten[0]));
        org.slf4j.LoggerFactory.getLogger("shaderlod-dev").info("ShaderLOD binding input first getter: {}",data);
    }
    public static void glFunctionEntry() {
        if(current==null)return;
        glFunctionEntries++;
        if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())shadowGlFunctionEntries++;
        else ordinaryGlFunctionEntries++;
    }
    public static void completedDrawBody() {
        if(current==null)return;
        completedDrawBodies++;
        if(net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered())shadowCompletedDrawBodies++;
        else ordinaryCompletedDrawBodies++;
    }
    private static boolean admitted(com.google.gson.JsonObject data) throws Exception {
        try {
            boolean result=admittedCurrent(data);if(!result)CERTIFICATE.invalidate();return result;
        }catch(Exception|LinkageError failure){CERTIFICATE.invalidate();throw failure;}
    }
    private static boolean admittedCurrent(com.google.gson.JsonObject data) throws Exception {
        var mode=BindingInputModes.mode(System.getProperties());if(data!=null)data.addProperty("bindingMode",mode.name());
        if(!predicate(data,"explicitNamedMode",mode!=BindingInputModes.Mode.REFUSED) ||
            !predicate(data,"renderThread",com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) ||
            !predicate(data,"settingsApplyNotSuspended",!MakeUpQuadLifecycle.suspended()))return false;
        String backend=((net.irisshaders.iris.mixin.GpuDeviceAccessor)com.mojang.blaze3d.systems.RenderSystem.getDevice()).getBackend().getDeviceInfo().backendName();
        if(data!=null)data.addProperty("backendName",backend);
        if(!predicate(data,"openGlBackend","OpenGL".equals(backend)) || !predicate(data,"exactApi",MakeUpQuadApiPins.exactApi()) ||
            !predicate(data,"classloaderPinsMatch",BindingInputPins.match()))return false;
        boolean loaded=com.pp.shaderlod.ShaderLodConfig.SPEC.isLoaded();if(!predicate(data,"coreLoaded",loaded))return false;
        boolean enabled=com.pp.shaderlod.ShaderLodConfig.ENABLED.get(),debug=com.pp.shaderlod.ShaderLodConfig.DEBUG.get();var preset=com.pp.shaderlod.ShaderLodConfig.PRESET.get();
        if(data!=null) {data.addProperty("coreEnabled",enabled);data.addProperty("coreDebug",debug);data.addProperty("corePreset",preset.name());}
        if(!predicate(data,"modeCoreState",BindingInputModes.coreAllows(mode,loaded,enabled,debug,preset.name())))return false;
        var client=net.minecraft.client.Minecraft.getInstance();
        if(!predicate(data,"worldPresent",client.level!=null) || !predicate(data,"overworld",client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD) ||
            !predicate(data,"noIrisFallback",!Iris.isFallback()) || !predicate(data,"emptyOptionQueue",Iris.getShaderPackOptionQueue().isEmpty()) ||
            !predicate(data,"packInUse",Iris.isPackInUseQuick()))return false;
        var resources=client.getResourcePackRepository().getSelectedIds();if(data!=null)data.add("actualResourceIds",DIAGNOSTIC_GSON.toJsonTree(resources));
        if(!predicate(data,"resourcesMatch",MakeUpQuadRuntime.resourcesMatch(resources)))return false;
        if(checkedStack==null) {
            var stack=new TreeMap<String,String>();for(var mod:net.neoforged.fml.ModList.get().getMods())stack.put(mod.getModId(),mod.getVersion().toString());
            actualStack=Map.copyOf(stack);checkedStack=MakeUpQuadRuntime.stackMatches(stack);
        }
        if(data!=null)data.add("actualStack",DIAGNOSTIC_GSON.toJsonTree(actualStack));
        if(!predicate(data,"stackMatches",checkedStack))return false;
        String name=Iris.getCurrentPackName(),requested=System.getProperty("shaderlod.dev.portRoundtripOriginal","");
        if(data!=null) {data.addProperty("currentPack",name);data.addProperty("requestedPack",requested);}
        if(mode==BindingInputModes.Mode.MAKEUP_CONTROL)return admittedControl(data,client,name,requested,resources,enabled,preset);
        if(!predicate(data,"requestedOriginalMatches",name.equals(requested) && "MakeUp-UltraFast-9.5g.zip".equals(requested)))return false;
        var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!predicate(data,"exactIrisPipeline",pipeline!=null && pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class))return false;
        var options=pack.getShaderPackOptions();var values=options.getOptionValues();
        if(!predicate(data,"exactMutableValues",values.getClass()==net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class))return false;
        var mutable=(net.irisshaders.iris.shaderpack.option.values.MutableOptionValues)values;
        long generation=MakeUpQuadLifecycle.generation();Object stamp=observedSourceStamp(client,name,generation);
        if(!CERTIFICATE.matches(pack,pipeline,client.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),resources,stamp)) {
            CERTIFICATE.invalidate();var actual=new TreeMap<String,String>();
            for(String key:options.getOptionSet().getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));
            for(String key:options.getOptionSet().getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            var certificate=MakeUpQuadEligibility.validate(Iris.getShaderpacksDirectory().resolve(name),actual);
            nativeOptionCount=actual.size();nativeAA=actual.get("AA_TYPE");certificateReason=certificate.reason();certificateRefreshes++;
            CERTIFICATE.remember(pack,pipeline,client.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),resources,stamp,
                nativeOptionCount==100 && "2".equals(nativeAA) && certificate.eligible());
        } else certificateReuses++;
        if(data!=null) {data.addProperty("nativeOptionCount",nativeOptionCount);data.addProperty("nativeAA",nativeAA);data.addProperty("sourceCertificateReason",certificateReason);
            data.addProperty("certificateRefreshes",certificateRefreshes);data.addProperty("certificateReuses",certificateReuses);data.addProperty("sourceStampReads",sourceStampReads);}
        return predicate(data,"native100AA2SourceCertificate",CERTIFICATE.eligible());
    }
    private static long controlStampTick=Long.MIN_VALUE,controlStampGeneration=Long.MIN_VALUE;
    private static String controlStampName,controlRequested;private static Object controlStampValue;
    private static boolean admittedControl(com.google.gson.JsonObject data,net.minecraft.client.Minecraft client,String name,String requested,java.util.Collection<String> resources,boolean enabled,com.pp.shaderlod.LodPreset preset)throws Exception{
        var pack=Iris.getCurrentPack().orElseThrow();var pipeline=Iris.getPipelineManager().getPipelineNullable();
        if(!predicate(data,"exactIrisPipeline",pipeline!=null&&pipeline.getClass()==net.irisshaders.iris.pipeline.IrisRenderingPipeline.class))return false;
        var options=pack.getShaderPackOptions();var values=options.getOptionValues();if(!predicate(data,"exactMutableValues",values.getClass()==net.irisshaders.iris.shaderpack.option.values.MutableOptionValues.class))return false;
        var mutable=(net.irisshaders.iris.shaderpack.option.values.MutableOptionValues)values;long generation=MakeUpQuadLifecycle.generation(),tick=System.nanoTime()/50_000_000L;
        boolean transition=Iris.getIrisConfig().getShaderPackName().orElse("").equals(requested)&&(!enabled||preset==com.pp.shaderlod.LodPreset.QUALITY);
        if(controlStampTick!=tick||controlStampGeneration!=generation||!java.util.Objects.equals(name,controlStampName)||!java.util.Objects.equals(requested,controlRequested)){
            controlStampValue=MakeUpControlCertificate.stamp(Iris.getShaderpacksDirectory(),name,requested);controlStampTick=tick;controlStampGeneration=generation;controlStampName=name;controlRequested=requested;sourceStampReads++;
        }
        // Transition is part of source admission; state changes must not reuse an active-state cache entry.
        Object stamp=java.util.List.of(controlStampValue,transition);
        if(!CERTIFICATE.matches(pack,pipeline,client.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),resources,stamp)){
            CERTIFICATE.invalidate();var actual=new TreeMap<String,String>();for(String key:options.getOptionSet().getBooleanOptions().keySet())actual.put(key,Boolean.toString(values.getBooleanValueOrDefault(key)));for(String key:options.getOptionSet().getStringOptions().keySet())actual.put(key,values.getStringValueOrDefault(key));
            var certificate=MakeUpControlCertificate.validate(Iris.getShaderpacksDirectory(),name,requested,actual,transition);nativeOptionCount=actual.size();nativeAA=actual.get("AA_TYPE");certificateReason=certificate.reason();certificateRefreshes++;
            CERTIFICATE.remember(pack,pipeline,client.level,values,generation,name,mutable.getBooleanValues(),mutable.getStringValues(),resources,stamp,certificate.eligible());
        }else certificateReuses++;
        if(data!=null){data.addProperty("controlSourceOptionCount",nativeOptionCount);data.addProperty("controlSourceAA",nativeAA);data.addProperty("controlSourceCertificateReason",certificateReason);data.addProperty("nativeReturnSelectionTransition",transition);data.addProperty("generatedSourceNotNativeBatchingAdmission",!name.equals(requested));}
        return predicate(data,"original100OrExactMaintainedGenerated104Source",CERTIFICATE.eligible());
    }

}
