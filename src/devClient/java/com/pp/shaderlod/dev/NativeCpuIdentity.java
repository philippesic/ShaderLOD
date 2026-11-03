package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.LodPreset;
import com.pp.shaderlod.integration.makeupquad.*;
import com.pp.shaderlod.measurement.*;
import com.pp.shaderlod.optimization.*;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shaderpack.option.values.MutableOptionValues;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/** Untimed read-only DEV witness. Never grants permission, changes lifecycle or admits a draw.
 * References and handles cover exactly three public world-terrain programs, not the whole render graph.
 */
final class NativeCpuIdentity {
    private static final Gson JSON=new Gson();
    // Reuse the existing hash-pinned complete native resource maps; no new admission.
    private static final Map<String,String> BSL_OPTIONS=com.pp.shaderlod.integration.bslquad.BslQuadEligibility.nativeOptions();
    private static final Map<String,String> SILDUR_OPTIONS=com.pp.shaderlod.integration.sildurquad.SildurQuadEligibility.nativeOptions();
    private static final Map<String,String> REIMAGINED_OPTIONS=com.pp.shaderlod.integration.reimaginedquad.ReimaginedQuadEligibility.nativeOptions();
    private static final Map<String,String> UNBOUND_OPTIONS=com.pp.shaderlod.integration.unboundquad.UnboundQuadEligibility.nativeOptions();
    private static final Map<String,String> PHOTON_OPTIONS=com.pp.shaderlod.integration.photonquad.PhotonQuadEligibility.nativeOptions();
    private static final Map<String,String> BLISS_OPTIONS=com.pp.shaderlod.integration.blissquad.BlissQuadEligibility.nativeOptions();
    static boolean nativeOptions(String selected,String family,Map<String,String> effective){
        return switch(selected){
            case "MakeUpNativeOriginal.zip" -> family.equals("makeup-ultra-fast-shaders") && effective.size()==100 && "1".equals(effective.get("AO")) && "5".equals(effective.get("AOSTEPS")) && "2".equals(effective.get("AA_TYPE"));
            case "BSL_v10.1.8.zip" -> family.equals("bsl-shaders") && effective.size()==384 && BSL_OPTIONS.size()==384 && effective.equals(BSL_OPTIONS);
            case "Sildur's Vibrant Shaders v2.02 Extreme.zip" -> family.equals("sildurs-vibrant-shaders") && effective.size()==115 && SILDUR_OPTIONS.size()==115 && effective.equals(SILDUR_OPTIONS);
            case "ComplementaryReimagined_r5.9.3.zip" -> family.equals("complementary-reimagined") && effective.size()==398 && REIMAGINED_OPTIONS.size()==398 && effective.equals(REIMAGINED_OPTIONS);
            case "ComplementaryUnbound_r5.9.3.zip" -> family.equals("complementary-unbound") && effective.size()==398 && UNBOUND_OPTIONS.size()==398 && effective.equals(UNBOUND_OPTIONS);
            case "photon_v1.3b.zip" -> family.equals("photon") && effective.size()==366 && PHOTON_OPTIONS.size()==366 && effective.equals(PHOTON_OPTIONS);
            case "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip" -> family.equals("bliss-shader") && effective.size()==463 && BLISS_OPTIONS.size()==463 && effective.equals(BLISS_OPTIONS);
            default -> false;
        };
    }
    private static boolean familyApi(String selected){
        return switch(selected){
            case "MakeUpNativeOriginal.zip" -> true; // Common exact Quad API is required below for every family.
            case "BSL_v10.1.8.zip" -> com.pp.shaderlod.optimization.bsl.BslAoApiPins.exactApi()
                && com.pp.shaderlod.optimization.bsl.BslResourceApiPins.exactApi();
            case "Sildur's Vibrant Shaders v2.02 Extreme.zip" -> com.pp.shaderlod.optimization.sildurao.SildurAoApiPins.exact();
            case "ComplementaryReimagined_r5.9.3.zip" -> com.pp.shaderlod.optimization.reimaginedao.ReimaginedAoApiPins.exact();
            case "ComplementaryUnbound_r5.9.3.zip" -> com.pp.shaderlod.optimization.unboundao.UnboundAoApiPins.exact();
            case "photon_v1.3b.zip" -> com.pp.shaderlod.optimization.photon.PhotonSpatialApiPins.exactApi();
            case "Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip" -> com.pp.shaderlod.optimization.blisspixel.BlissPixelApiPins.exact();
            default -> false;
        };
    }
    // Collision-free reference labels within this one bounded dev client; retain only observed objects.
    private static final IdentityHashMap<Object,Integer> labels=new IdentityHashMap<>();
    private static int label(Object o){return labels.computeIfAbsent(o,k->labels.size()+1);}
    /** Native archive/option equality is enforced by the maintained caller. This observation
     * includes no mutable core configuration, so Off/Apply/return may be compared. */
    static Map<String,Object> graph(){
        if(Iris.isFallback()||!(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline pipeline))
            throw new IllegalStateException("Current native Iris graph required");
        var pack=Iris.getCurrentPack().orElseThrow();
        var programs=pipeline.getSodiumPrograms();
        var refs=new ArrayList<Integer>(List.of(label(pack),label(pipeline),label(programs)));
        var handles=new ArrayList<Integer>();
        for(var pass:List.of(DefaultTerrainRenderPasses.SOLID,DefaultTerrainRenderPasses.CUTOUT,DefaultTerrainRenderPasses.TRANSLUCENT)){
            var program=programs.getProgram(pass);
            if(program==null||program.handle()<=0)throw new IllegalStateException("Native terrain program missing");
            refs.add(label(program));handles.add(program.handle());
        }
        return Map.of("references",List.copyOf(refs),"terrain_handles",List.copyOf(handles),
            "reloads",RuntimeMeasurement.reloadCount(),"selected",Iris.getCurrentPackName(),
            "scope","Current pipeline/pack and three world-terrain programs; native archive/options separately checked");
    }
    record State(long reloads, List<Object> objects, List<Integer> handles, Map<String,Object> facts) {
        boolean same(State other){
            if(other==null||reloads!=other.reloads||objects.size()!=other.objects.size()
                ||!handles.equals(other.handles)||!facts.equals(other.facts))return false;
            for(int i=0;i<objects.size();i++)if(objects.get(i)!=other.objects.get(i))return false;
            return true;
        }
        JsonObject report(){
            var r=JSON.toJsonTree(facts).getAsJsonObject();
            r.addProperty("reloads",reloads);
            r.add("object_references",JSON.toJsonTree(objects.stream().map(NativeCpuIdentity::label).toList()));
            r.add("handles",JSON.toJsonTree(handles));
            return r;
        }
    }
    static State read(){
        var client=Minecraft.getInstance();var core=OptimizationRuntimeContext.core();
        if(RendererWorkDiagnostics.ENABLED||Boolean.getBoolean("mixin.debug.export")||RuntimeMeasurement.requested()||com.pp.shaderlod.tuning.AutoTuner.active()||BenchmarkProfileComparison.active()
            ||core==null||!core.settings().enabled()||core.preset()!=LodPreset.PERFORMANCE
            ||!OptimizationEditTransaction.cpuOnlyRequests(OptimizationConfig.snapshot())
            ||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()||client.level==null||client.level.dimension()!=Level.OVERWORLD
            ||net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()
            ||!MakeUpQuadApiPins.exactApi()||!MakeUpQuadRuntime.loadedStackMatches()
            ||!MakeUpQuadRuntime.resourcesMatchPacks(client.getResourcePackRepository().getSelectedPacks(),
                net.minecraft.server.packs.repository.Pack::isHidden,net.minecraft.server.packs.repository.Pack::getId))
            throw new IllegalStateException("Untimed normal Root NF1.21.1 native CPU2 observation unavailable");
        var pipeline=Iris.getPipelineManager().getPipelineNullable();var pack=Iris.getCurrentPack().orElse(null);
        if(pipeline==null||pipeline.getClass()!=IrisRenderingPipeline.class||pack==null)throw new IllegalStateException("Native Iris pipeline missing");
        var programs=((IrisRenderingPipeline)pipeline).getSodiumPrograms();
        if(programs==null||programs.getClass()!=SodiumPrograms.class)throw new IllegalStateException("Pinned SodiumPrograms missing");
        var caps=org.lwjgl.opengl.GL.getCapabilities();
        if(!MakeUpQuadRuntime.capabilitiesAllow(org.lwjgl.system.Pointer.POINTER_SIZE,caps.OpenGL41,caps.glMultiDrawElementsBaseVertex))throw new IllegalStateException("Pinned capabilities unavailable");
        var optionSet=pack.getShaderPackOptions();var values=optionSet.getOptionValues();
        if(values.getClass()!=MutableOptionValues.class)throw new IllegalStateException("Pinned mutable options unavailable");
        var selected=Iris.getIrisConfig().getShaderPackName().orElse("");
        if(!selected.equals(Iris.getCurrentPackName())||!familyApi(selected))throw new IllegalStateException("Exact pinned native family/API required");
        var objects=new ArrayList<Object>(List.of(pack,pipeline,programs,client.level,values,optionSet.getOptionSet(),caps));
        var handles=new ArrayList<Integer>();
        for(var pass:List.of(DefaultTerrainRenderPasses.SOLID,DefaultTerrainRenderPasses.CUTOUT,DefaultTerrainRenderPasses.TRANSLUCENT)){
            var program=programs.getProgram(pass);
            if(program==null||program.handle()<=0)throw new IllegalStateException("World terrain GlProgram unavailable");
            objects.add(program);handles.add(program.handle());
        }
        var resources=new ArrayList<String>();
        for(var resource:client.getResourcePackRepository().getSelectedPacks()){
            objects.add(resource);resources.add(resource.getId()+":"+resource.isHidden());
        }
        var effective=new TreeMap<String,String>();
        for(var k:optionSet.getOptionSet().getBooleanOptions().keySet())effective.put(k,Boolean.toString(values.getBooleanValueOrDefault(k)));
        for(var k:optionSet.getOptionSet().getStringOptions().keySet())effective.put(k,values.getStringValueOrDefault(k));
        if(!nativeOptions(selected,OptimizationConfig.activeFamily(),effective))throw new IllegalStateException("Complete pinned native family options required");
        var mutable=(MutableOptionValues)values;var facts=new TreeMap<String,Object>();
        facts.put("scope","three public world-terrain programs only; not whole graph");
        facts.put("selected",selected);facts.put("family",OptimizationConfig.activeFamily());
        facts.put("scene",MeasurementPins.scene());facts.put("core_bands",core.settings());facts.put("preset",core.preset().name());
        facts.put("effective_options",Map.copyOf(effective));facts.put("boolean_values",Map.copyOf(mutable.getBooleanValues()));facts.put("string_values",Map.copyOf(mutable.getStringValues()));
        facts.put("resources",List.copyOf(resources));
        facts.put("renderer_work_counters",RendererWorkDiagnostics.ENABLED);facts.put("mixin_export",Boolean.getBoolean("mixin.debug.export"));
        return new State(RuntimeMeasurement.reloadCount(),List.copyOf(objects),List.copyOf(handles),Map.copyOf(facts));
    }
}
