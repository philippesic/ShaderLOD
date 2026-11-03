package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** One opt-in, read-only receipt after an explicitly reported final Apply. No timing evidence. */
public final class SettledConsumerUniforms {
    private static final String REQUEST = System.getProperty("shaderlod.dev.settledConsumerUniforms");
    private static State state;
    private static boolean stopped;
    private SettledConsumerUniforms() {}

    // Fixed normal-benchmark one-shot; independent of the legacy startup REQUEST observer.
    private static SkyRead benchmarkSky;
    // One bounded witness, published only after a successful actual MakeUp draw readback.
    private static SkyRead makeUpWitness;
    private enum BenchmarkConsumer { BLISS_NATIVE_AO, PHOTON_ORDINARY_TRACE, UNBOUND_SKY, BLISS_SHADOW, UNBOUND_REFLECTION_METRIC, BLISS_CLOUD_EXTENT, SILDUR_AO_SKY, SILDUR_SHADOW, SILDUR_CLOUD, MAKEUP_CLOUD, MAKEUP_VOLUMETRIC, MAKEUP_AO }
    private static final class SkyRead {
        final Path output;final String expectedPack;final BenchmarkConsumer consumer;final long deadline=System.nanoTime()+60_000_000_000L;
        final JsonObject result=new JsonObject();
        Object renderer,pack,world;WorldRenderingPipeline pipeline;Program program;int[] buffers;boolean inDraw;long compiledAt;
        ProgramSource source;Object glCapabilities;long glContext,reloads,witnessAt;int programId;
        Map<String,String> options;
        String observationStage="armed";
        com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Certificate volumetricCertificate;
        SkyRead(Path output,String expectedPack,BenchmarkConsumer consumer){this.output=output;this.expectedPack=expectedPack;this.consumer=consumer;}
        boolean makeupAo(){return consumer==BenchmarkConsumer.MAKEUP_AO;}
        boolean replayConsumer(){return volumetric()||makeupAo();}
        boolean volumetric(){return consumer==BenchmarkConsumer.MAKEUP_VOLUMETRIC;}
        boolean sildur(){return switch(consumer){case SILDUR_AO_SKY,SILDUR_SHADOW,SILDUR_CLOUD->true;default->false;};}
        boolean nativeConsumer(){return consumer==BenchmarkConsumer.BLISS_NATIVE_AO||consumer==BenchmarkConsumer.PHOTON_ORDINARY_TRACE||makeupAo();}
        CompositePass group(){return consumer==BenchmarkConsumer.PHOTON_ORDINARY_TRACE||consumer==BenchmarkConsumer.UNBOUND_SKY||consumer==BenchmarkConsumer.MAKEUP_CLOUD||makeupAo()||sildur()?CompositePass.DEFERRED:CompositePass.COMPOSITE;}
        String sourceName(){return switch(consumer){case BLISS_NATIVE_AO->"composite1";case PHOTON_ORDINARY_TRACE->"deferred4";case UNBOUND_SKY->"deferred1";case BLISS_SHADOW->"composite1";case UNBOUND_REFLECTION_METRIC,MAKEUP_VOLUMETRIC->"composite";case BLISS_CLOUD_EXTENT->"composite3";case SILDUR_AO_SKY,SILDUR_SHADOW,SILDUR_CLOUD,MAKEUP_CLOUD,MAKEUP_AO->"deferred";};}
        String family(){if(consumer==BenchmarkConsumer.BLISS_NATIVE_AO)return "bliss-shader";if(consumer==BenchmarkConsumer.PHOTON_ORDINARY_TRACE)return "photon";if(sildur())return "sildurs-vibrant-shaders";if(consumer==BenchmarkConsumer.MAKEUP_CLOUD||replayConsumer())return "makeup-ultra-fast-shaders";return consumer==BenchmarkConsumer.BLISS_SHADOW||consumer==BenchmarkConsumer.BLISS_CLOUD_EXTENT?"bliss-shader":"complementary-unbound";}
        String flag(){return switch(consumer){case BLISS_NATIVE_AO,MAKEUP_AO->"shaderlodOptAoBudget";case PHOTON_ORDINARY_TRACE->"shaderlodOptReflectionTraceBudget";case UNBOUND_SKY->"shaderlodOptSkyAtmosphereOwnership";case BLISS_SHADOW->"shaderlodOptShadowSampleBudget";case UNBOUND_REFLECTION_METRIC->"shaderlodOptReflectionDeferredMetric";case BLISS_CLOUD_EXTENT->"shaderlodOptCloudSecondaryExtentCache";case SILDUR_AO_SKY->"shaderlodOptAoSkyElision";case SILDUR_SHADOW->"shaderlodOptShadowSampleBudget";case SILDUR_CLOUD,MAKEUP_CLOUD->"shaderlodOptCloudPrimaryBudget";case MAKEUP_VOLUMETRIC->"shaderlodOptVolumetricLightBudget";};}
        String description(){return switch(consumer){case MAKEUP_AO->"world0/deferred:dbao same-pass native5/static4; per-pixel four eligibility unproved";case BLISS_NATIVE_AO->"world0/composite1:dimensions/composite.fsh SSAO native7/distance-budget; no pixel coverage proof";case PHOTON_ORDINARY_TRACE->"world0/deferred4:get_specular_reflections/trace_specular_ray; actual opaque reflective coverage unproven, water requires raster receipt";case UNBOUND_SKY->"world0/deferred1:GetClouds/GetVolumetricClouds/GetSky";case BLISS_SHADOW->"world0/composite1:dimensions/composite.fsh VariablePenumbraSearch blocker block";case UNBOUND_REFLECTION_METRIC->"world0/composite:program/composite.glsl GetReflection final dist/lodFactor";case BLISS_CLOUD_EXTENT->"world0/composite3:dimensions/composite2.fsh renderClouds/renderLayer secondary ray/plane extent";case SILDUR_AO_SKY->"deferred:SSDO unused sky/liquid AO";case SILDUR_SHADOW->"deferred:funShadows native PCF quadrature";case SILDUR_CLOUD->"deferred:funClouds3D primary quadrature";case MAKEUP_VOLUMETRIC->"world0/composite:common/composite_fragment.glsl ssGodrays cheap rays native4/Performance3";case MAKEUP_CLOUD->"world0/deferred:common/deferred_fragment.glsl get_cloud primary quadrature";};}
        List<String> markers(){return switch(consumer){
            case MAKEUP_AO->List.of("dbao(","shaderlodMakeupNativeAo(","shaderlodAoSamples",flag());
            case BLISS_NATIVE_AO->List.of("shaderlodBlissNativeSSAO(","shaderlod_bliss_distance_budget(","FlatNormals",flag());
            case PHOTON_ORDINARY_TRACE->List.of("get_specular_reflections(","trace_specular_ray(","raymarch_depth_buffer(","shaderlodNativeIntersectionCount",flag());
            case UNBOUND_SKY->List.of("GetVolumetricClouds(","GetSky(","shaderlodSkyPrefixValid",flag());
            case BLISS_SHADOW->List.of("shaderlodShadowSearchCount","shaderlod_bliss_distance_budget(","avgBlockerDepth",flag());
            case UNBOUND_REFLECTION_METRIC->List.of("GetReflection(","shaderlodMetric","shaderlodSampled","lodFactor",flag());
            case SILDUR_AO_SKY->List.of("funSSDO(","shaderlodAoUnused",flag());
            case SILDUR_SHADOW->List.of("funShadows(","shaderlod_sildur_distance_count(",flag());
            case SILDUR_CLOUD->List.of("funClouds3D(","shaderlod_sildur_primary_cloud_count(",flag());
            case MAKEUP_VOLUMETRIC->List.of("ssGodrays(","shaderlod_makeup_light_budget(","shaderlodLightSamples",flag());
            case MAKEUP_CLOUD->List.of("get_cloud(","shaderlod_makeup_cloud_kernel(","shaderlod_makeup_cloud_budget(",flag());
            case BLISS_CLOUD_EXTENT->List.of("renderClouds(","renderLayer(","shaderlodExtentSelected","shaderlodExtentReady","shaderlodExtentSun","shaderlodExtentPlane",flag());
        };}
    }
    private static boolean skyTimingOwned(){
        var pair=com.pp.shaderlod.measurement.RuntimeMeasurement.pair();
        return com.pp.shaderlod.tuning.AutoTuner.active()||BenchmarkProfileComparison.active()
            ||com.pp.shaderlod.measurement.RuntimeMeasurement.requested()||pair!=null&&(pair.active()||pair.restorationPending());
    }
    public static void armBenchmarkBlissNativeAo(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.BLISS_NATIVE_AO);}
    public static void armBenchmarkPhotonOrdinaryTrace(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.PHOTON_ORDINARY_TRACE);}
    public static void armBenchmarkSky(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.UNBOUND_SKY);}
    public static void armBenchmarkBlissShadow(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.BLISS_SHADOW);}
    public static void armBenchmarkUnboundReflectionMetric(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.UNBOUND_REFLECTION_METRIC);}
    public static void armBenchmarkBlissCloudExtent(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.BLISS_CLOUD_EXTENT);}
    public static void armBenchmarkMakeUpAo(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.MAKEUP_AO);}
    public static void armBenchmarkMakeUpCloud(Path output,String expectedPack)throws Exception {armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.MAKEUP_CLOUD);}
    public static void armBenchmarkMakeUpVolumetric(Path output,String expectedPack)throws Exception {
        armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.MAKEUP_VOLUMETRIC);
    }
    /** Whole selected archive verification plus actual Iris raw/options/properties/stage replay.
     * Kept local: this dev observation grants no runtime consumer permission. */
    private static void qualifyMakeUpReplay(SkyRead s,ProgramSource source)throws Exception {
        var set=source.getParent();var pack=Iris.getCurrentPack().orElseThrow();
        if(set==null||set.getPack()!=pack
                ||!(pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView pv)
                ||!(set instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.SetView sv))
            throw new IllegalStateException("Missing actual MakeUp composite input observers");
        var input=pv.shaderlod$makeupPackInput();var actual=sv.shaderlod$makeupSetInput();
        if(input==null||actual==null||actual.pack()!=pack||actual.provider()!=input.provider()
                ||actual.properties()!=input.properties()||!"/world0".equals(actual.directory().getPathString())
                ||!input.options().current(pack.getShaderPackOptions()))throw new IllegalStateException("Composite input identity/Overworld graph mismatch");
        var values=input.options().values();
        if(s.makeupAo() && (values.size()!=111
                ||!values.equals(com.pp.shaderlod.optimization.makeupao.MakeUpAoContract.generated("0"))))
            throw new IllegalStateException("Requires exact native100/generated111 AO1/AOSTEPS5/AA2 and all compile graphs zero");
        if(s.volumetric() && (!"1".equals(values.get("VOL_LIGHT"))||!"2".equals(values.get("AA_TYPE"))
                ||!"1".equals(values.get("SHADERLOD_REFINEMENT_ADAPTER"))))
            throw new IllegalStateException("Requires existing VOL_LIGHT1/AA2 generated graph1");
        for(var macro:input.glslDefines())if(Set.of("DISTANT_HORIZONS","VOXY","VOXY_PATCH").contains(macro.key()))
            throw new IllegalStateException("Excluded volumetric macro "+macro.key());
        var snapshot=com.pp.shaderlod.packs.PackSelectionService.captureGeneratedRendererInputs(Iris.getShaderpacksDirectory().resolve(s.expectedPack),values);
        if(snapshot==null||snapshot.provenance()==null
                ||!com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.matchesRaw(snapshot,input,s.family())
                ||!snapshot.matchesIrisProperties("shaders/shaders.properties",input.propertyText().get("shaders.properties"))
                ||!snapshot.matchesIrisProperties("shaders/dimension.properties",input.propertyText().get("dimension.properties")))
            throw new IllegalStateException("Complete selected generated source/recipe/native pins/raw graph join refused");
        var stages=com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.replay(input.options(),input.glslDefines(),actual.directory(),s.sourceName());
        if(stages==null||stages.vertex()==null||stages.fragment()==null||stages.geometry()!=null||stages.control()!=null||stages.evaluation()!=null
                ||!stages.equals(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Stages.of(source)))
            throw new IllegalStateException("Actual composite stages differ from pinned Iris replay");
        String base=actual.directory().getPathString()+"/"+s.sourceName();
        String[] extensions={".vsh",".gsh",".tcs",".tes",".fsh"};
        String[] expected={stages.vertex(),stages.geometry(),stages.control(),stages.evaluation(),stages.fragment()};
        for(int i=0;i<extensions.length;i++)if(!java.util.Objects.equals(expected[i],input.provider().apply(net.irisshaders.iris.shaderpack.include.AbsolutePackPath.fromAbsolutePath(base+extensions[i]))))
            throw new IllegalStateException("Actual composite provider stages differ");
        s.volumetricCertificate=new com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Certificate(source,set,input,s.expectedPack,stages,com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount());
        s.result.add("verified_generated_provenance",new GsonBuilder().create().toJsonTree(snapshot.provenance()));
        s.result.addProperty("complete_generated_source_and_iris_replay_verified",true);
        s.result.addProperty("actual_program_directory",actual.directory().getPathString());
        s.result.add("full_effective_options",new GsonBuilder().create().toJsonTree(values));
        if(s.volumetric())s.result.add("cheap_ray_source_semantics",makeUpCheapRaySemantics(stages.fragment()));
        if(s.makeupAo())s.result.add("same_pass_ao_source_semantics",makeUpAoSemantics(stages.fragment()));
    }
    /** Exact preprocessed kernel identities after complete archive/provider/replay qualification. */
    static String makeUpAoFunction(String source,String signature){
        String compact=source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*","").replaceAll("\\s+","");
        String key=signature.replaceAll("\\s+","");int start=compact.indexOf(key);
        if(start<0||compact.indexOf(key,start+key.length())>=0)throw new IllegalStateException("Missing/duplicate AO definition "+signature);
        int end=compact.indexOf('{',start)+1,depth=1;
        if(end==0)throw new IllegalStateException("AO body absent");
        while(depth>0&&end<compact.length()){char c=compact.charAt(end++);if(c=='{')depth++;if(c=='}')depth--;}
        if(depth!=0)throw new IllegalStateException("Unbalanced AO body");return compact.substring(start,end);
    }
    static JsonObject makeUpAoSemantics(String raw)throws Exception {
        boolean specialized=raw.contains("float shaderlodMakeupStaticAo4(");
        var kernels=new LinkedHashMap<String,String>();
        kernels.put("float shaderlodMakeupNativeAo(","75dd8f633954000987608a38d3a0bb702b5f232843caa55b3bb2e0450617a500");
        kernels.put("float dbao(",specialized?"c4f3b7b99bc66234e6d7a9774816daccf5ebbc31711db63d3d228a1608cf8a8e":"19c80d09c4eb8ff7a7e99e258f69aa8fb108d86d7bbc506fc8977c4162b4f881");
        if(specialized)kernels.put("float shaderlodMakeupStaticAo4(","9a4ce6d0f2673e03b871990c56c5b7364c3fd2100ae92955ef63cb4d0c041507");
        if(specialized)kernels.put("float shaderlodMakeupNativeAo5Prepared(","57a503442665be9b6a210e5b38b3fef013eb7a3dc0ca639fbf53c6b4dbf11a8a");
        var observed=new JsonObject();
        for(var entry:kernels.entrySet()){
            String actual=hash(makeUpAoFunction(raw,entry.getKey()));
            if(!entry.getValue().equals(actual))throw new IllegalStateException("AO kernel changed: "+entry.getKey());
            observed.addProperty(entry.getKey(),actual);
        }
        if(!makeUpAoFunction(raw,"void main(").contains("final_ao=mix(dbao(dither),1.0,ao_att);"))
            throw new IllegalStateException("Active deferred AO caller absent");
        var result=new JsonObject();result.add("kernel_sha256",observed);
        result.addProperty("static_candidate",specialized);result.addProperty("native5_source_depth_reads",11);
        result.addProperty("reduced4_source_depth_reads",9);result.addProperty("four_tap_pixel_coverage_proved",false);
        return result;
    }
    /** Strict source-specific assertions, after native Iris macro expansion; never token-only admission. */
    static JsonObject makeUpCheapRaySemantics(String fragment)throws Exception {
        String compact=fragment.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*","").replaceAll("\\s+","");
        int start=compact.indexOf("floatssGodrays(floatdither){");
        if(start<0)throw new IllegalStateException("Cheap-ray definition absent");
        int open=compact.indexOf('{',start),end=open+1,depth=1;
        while(depth>0&&end<compact.length()){char c=compact.charAt(end++);if(c=='{')depth++;if(c=='}')depth--;}
        String body=compact.substring(start,end);
        String nativeLoop="for(inti=0;i<4;i++){depth=texture2D(depthtex1,dither2d).x;dither2d+=ray_step;light+=step(comp,depth);}";
        String reducedLoop="for(inti=0;i<shaderlodLightSamples;i++){if(shaderlodLightSamples<4)dither2d=texcoord+ray_step*(dither+float(i)*float(4-1)/float(shaderlodLightSamples-1));depth=texture2D(depthtex1,dither2d).x;dither2d+=ray_step;light+=step(comp,depth);}";
        String expected="floatssGodrays(floatdither){floatlight=0.0;floatcomp=1.0-(near/(far*far));vec2ray_step=vec2(lightpos-texcoord)*0.2;vec2dither2d=texcoord+(ray_step*dither);floatdepth;intshaderlodLightSamples=4;shaderlodLightSamples=shaderlod_makeup_light_budget(4,2);if(shaderlodLightSamples==4){"+nativeLoop+"returnlight/4;}else{"+reducedLoop+"}returnlight/shaderlodLightSamples;}";
        if(!body.equals(expected)||!compact.contains("floatvolumetricLight=ssGodrays(dither);")
                ||compact.contains("floatget_volumetric_light("))throw new IllegalStateException("Exact native4/reduced endpoint-remap/caller semantics absent");
        var result=new JsonObject();result.addProperty("native_samples",4);result.addProperty("performance_samples",3);
        result.addProperty("full_literal_kernel_and_denominator",true);result.addProperty("reduced_endpoint_remap_and_denominator",true);
        result.addProperty("active_ssGodrays_caller",true);result.addProperty("ssGodrays_sha256",hash(body));
        return result;
    }
    private static void verifyMakeUpReplayLifetime(SkyRead s)throws Exception {
        s.observationStage="verifyMakeUpReplayLifetime:condition";
        var client=Minecraft.getInstance();
        if(s.volumetricCertificate==null||s.source==null||s.world!=client.level||client.level==null
                ||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                ||!s.volumetricCertificate.current(Iris.getCurrentPack().orElse(null),Iris.getCurrentPackName(),com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount())
                ||!Iris.getShaderPackOptionQueue().isEmpty()||!s.options.equals(fullOptions())
                ||s.glContext==0||s.glContext!=org.lwjgl.glfw.GLFW.glfwGetCurrentContext()
                ||s.glCapabilities!=org.lwjgl.opengl.GL.getCapabilities()||s.program.getProgramId()!=s.programId
                ||!GL20.glIsProgram(s.programId)||GL20.glGetProgrami(s.programId,GL20.GL_DELETE_STATUS)!=GL11.GL_FALSE
                ||GL20.glGetProgrami(s.programId,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE
                ||!s.volumetricCertificate.stages().equals(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Stages.of(s.source)))
        {
            s.observationStage="verifyMakeUpReplayLifetime:refusal-diagnostics";
            var check=new JsonObject();var certificate=s.volumetricCertificate;
            s.result.add("lifetime_refusal_checks",check);
            check.addProperty("certificate_present",certificate!=null);
            check.addProperty("source_present",s.source!=null);
            check.addProperty("world_same",s.world==client.level);
            check.addProperty("overworld",client.level!=null&&client.level.dimension()==net.minecraft.world.level.Level.OVERWORLD);
            check.addProperty("current_reload",com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount());
            check.addProperty("captured_reload",certificate==null?-1:certificate.epoch());
            check.addProperty("certificate_current",certificate!=null&&certificate.current(Iris.getCurrentPack().orElse(null),Iris.getCurrentPackName(),com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount()));
            check.addProperty("options_queue_empty",Iris.getShaderPackOptionQueue().isEmpty());
            check.addProperty("full_options_equal",s.options.equals(fullOptions()));
            check.addProperty("gl_context_equal",s.glContext!=0&&s.glContext==org.lwjgl.glfw.GLFW.glfwGetCurrentContext());
            check.addProperty("gl_capabilities_equal",s.glCapabilities==org.lwjgl.opengl.GL.getCapabilities());
            try{check.addProperty("program_handle_equal",s.program.getProgramId()==s.programId);}
            catch(RuntimeException unavailable){
                check.addProperty("program_handle_equal",false);
                check.addProperty("program_handle_error",unavailable.toString());
            }
            if(s.glContext!=0&&s.glContext==org.lwjgl.glfw.GLFW.glfwGetCurrentContext()
                    &&s.glCapabilities==org.lwjgl.opengl.GL.getCapabilities()){
                check.addProperty("program_live",GL20.glIsProgram(s.programId));
                if(GL20.glIsProgram(s.programId)){
                    check.addProperty("program_delete_status",GL20.glGetProgrami(s.programId,GL20.GL_DELETE_STATUS));
                    check.addProperty("program_link_status",GL20.glGetProgrami(s.programId,GL20.GL_LINK_STATUS));
                }
            }
            check.addProperty("stages_equal",certificate!=null&&s.source!=null&&certificate.stages().equals(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Stages.of(s.source)));
            s.result.add("lifetime_refusal_checks",check);
            throw new IllegalStateException("MakeUp volumetric current compile/draw lifetime changed");
        }
    }
    private static Map<String,String> fullOptions(){
        var pack=Iris.getCurrentPack().orElseThrow();var options=pack.getShaderPackOptions();var values=options.getOptionValues();
        var result=new java.util.TreeMap<String,String>();
        options.getOptionSet().getBooleanOptions().keySet().forEach(key->result.put(key,Boolean.toString(values.getBooleanValueOrDefault(key))));
        options.getOptionSet().getStringOptions().keySet().forEach(key->result.put(key,values.getStringValueOrDefault(key)));
        return Map.copyOf(result);
    }
    private static void verifyMakeUpLifetime(SkyRead s)throws Exception {
        var client=Minecraft.getInstance();
        if(s==null||s.consumer!=BenchmarkConsumer.MAKEUP_CLOUD||s.source==null||s.program==null
                ||s.witnessAt==0||System.nanoTime()-s.witnessAt>600_000_000_000L
                ||s.pack!=Iris.getCurrentPack().orElse(null)||s.pipeline!=Iris.getPipelineManager().getPipelineNullable()
                ||s.world!=client.level||client.level==null||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD
                ||!s.expectedPack.equals(Iris.getCurrentPackName())||Iris.isFallback()||!Iris.getShaderPackOptionQueue().isEmpty()
                ||s.reloads!=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount()
                ||s.glContext==0||s.glContext!=org.lwjgl.glfw.GLFW.glfwGetCurrentContext()
                ||s.glCapabilities!=org.lwjgl.opengl.GL.getCapabilities()||!s.options.equals(fullOptions())
                ||s.program.getProgramId()!=s.programId||!GL20.glIsProgram(s.programId)
                ||GL20.glGetProgrami(s.programId,GL20.GL_DELETE_STATUS)!=GL11.GL_FALSE
                ||GL20.glGetProgrami(s.programId,GL20.GL_LINK_STATUS)!=GL11.GL_TRUE
                ||!s.result.get("fragment_sha256").getAsString().equals(hash(s.source.getFragmentSource().orElseThrow())))
            throw new IllegalStateException("MakeUp retained compile/draw witness expired or changed");
    }
    public static void requireMakeUpCloudWitness(String expectedPack)throws Exception {
        if(skyTimingOwned()||benchmarkSky!=null)throw new IllegalStateException("Cloud live control requires an unowned observation/timing lane");
        try{
            verifyMakeUpLifetime(makeUpWitness);
            if(!makeUpWitness.expectedPack.equals(expectedPack))throw new IllegalStateException("MakeUp witness pack mismatch");
        }catch(Exception failure){makeUpWitness=null;throw failure;}
    }
    public static void reobserveMakeUpCloud(Path output,String expectedPack)throws Exception {
        requireMakeUpCloudWitness(expectedPack);var prior=makeUpWitness;
        armBenchmarkConsumer(output,expectedPack,BenchmarkConsumer.MAKEUP_CLOUD);var s=benchmarkSky;
        s.renderer=prior.renderer;s.pipeline=prior.pipeline;s.pack=prior.pack;s.world=prior.world;s.program=prior.program;
        s.buffers=prior.buffers.clone();s.source=prior.source;s.options=prior.options;s.glContext=prior.glContext;
        s.glCapabilities=prior.glCapabilities;s.reloads=prior.reloads;s.programId=prior.programId;s.witnessAt=prior.witnessAt;
        s.compiledAt=System.nanoTime();
        for(String key:List.of("source_name","pass_group","fragment_sha256","transformed_fragment_sha256","raw_primary_cloud_excerpt","scope_of_budget_excerpt"))
            if(prior.result.has(key))s.result.add(key,prior.result.get(key).deepCopy());
        s.result.addProperty("retained_witness",true);s.result.addProperty("prior_witness_receipt",prior.output.toString());
    }
    public static void armBenchmarkSildur(Path output,String expectedPack,String flag)throws Exception {
        var consumer=switch(flag){case "ao_sky_elision"->BenchmarkConsumer.SILDUR_AO_SKY;case "shadow_sample_budget"->BenchmarkConsumer.SILDUR_SHADOW;case "cloud_primary_budget"->BenchmarkConsumer.SILDUR_CLOUD;default->throw new IllegalArgumentException("Explicit Sildur consumer required");};
        armBenchmarkConsumer(output,expectedPack,consumer);
    }
    private static void armBenchmarkConsumer(Path output,String expectedPack,BenchmarkConsumer consumer)throws Exception {
        if(skyTimingOwned()||benchmarkSky!=null)throw new IllegalStateException("Sky observation requires an unowned timing lane");
        var pending=new SkyRead(output,expectedPack,consumer);
        if(!pending.family().equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())
            ||expectedPack.isBlank()||!Path.of(expectedPack).getFileName().toString().equals(expectedPack))throw new IllegalArgumentException("Explicit selected-pack basename for the fixed consumer family required");
        Files.writeString(output,"{\"status\":\"PENDING\"}\n",java.nio.file.StandardOpenOption.CREATE_NEW);
        benchmarkSky=pending;
        benchmarkSky.result.addProperty("expected_pack",expectedPack);
        benchmarkSky.result.addProperty("consumer",benchmarkSky.description());
        benchmarkSky.result.addProperty("consumer_family",benchmarkSky.family());
        benchmarkSky.result.addProperty("consumer_flag",benchmarkSky.flag());
        benchmarkSky.result.addProperty("scope","Untimed named linked-consumer scalar observation; no executed-work/quality/speed claim");
    }
    public static void pollBenchmarkSky(){skyPending();}
    private static SkyRead skyPending(){
        var s=benchmarkSky;if(s==null)return null;
        if(skyTimingOwned()){finishSky(s,"REFUSED_TIMING_OWNED",null);return null;}
        if(System.nanoTime()>s.deadline){finishSky(s,"DEADLINE_EXCEEDED",null);return null;}
        return s;
    }
    /** Bounded dev-only failure evidence; never dereference a GL resource while reporting failure. */
    static void recordSkyFailure(JsonObject result,String stage,Exception failure){
        result.addProperty("failure_stage",stage);
        var frames=new JsonArray();var stack=failure.getStackTrace();
        for(int i=0;i<Math.min(stack.length,24);i++){
            String frame=stack[i].toString();frames.add(frame.substring(0,Math.min(frame.length(),512)));
        }
        result.add("failure_stack",frames);result.addProperty("failure_stack_truncated",stack.length>24);
    }
    private static void finishSky(SkyRead s,String status,Exception failure){
        if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD){
            if("OBSERVED".equals(status))makeUpWitness=s;else makeUpWitness=null;
        }
        benchmarkSky=null;s.result.addProperty("status",status);s.result.addProperty("at",Instant.now().toString());
        if(failure!=null){s.result.addProperty("error",failure.toString());recordSkyFailure(s.result,s.observationStage,failure);}
        try{var temp=Files.createTempFile(s.output.toAbsolutePath().getParent(),"sky-consumer-",".tmp");
            Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(s.result)+"\n");
            Files.move(temp,s.output,java.nio.file.StandardCopyOption.ATOMIC_MOVE,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception error){com.mojang.logging.LogUtils.getLogger().warn("Sky consumer receipt failed",error);}
    }
    private static void skyCompiled(Object renderer,WorldRenderingPipeline pipeline,CompositePass group,ProgramSource source,Program program,Map<?,String> transformed){
        var s=skyPending();if(s==null||group!=s.group()||source==null||program==null||!s.sourceName().equals(source.getName())||!s.expectedPack.equals(Iris.getCurrentPackName()))return;
        try{
            s.observationStage="skyCompiled:source-and-identity";
            if(s.program!=null)throw new IllegalStateException("Pending sky program identity changed");
            String raw=source.getFragmentSource().orElseThrow();String patched=transformed.entrySet().stream().filter(e->"FRAGMENT".equals(e.getKey().toString())).map(Map.Entry::getValue).findFirst().orElseThrow();
            s.result.addProperty("source_name",source.getName());s.result.addProperty("pass_group",group.name());
            s.result.addProperty("fragment_sha256",hash(raw));s.result.addProperty("transformed_fragment_sha256",hash(patched));
            for(String marker:s.markers())
                if(!raw.contains(marker)||!patched.contains(marker)){s.result.addProperty("missing_marker",marker);finishSky(s,"REFUSED_SOURCE_PATH",null);return;}
            if(s.replayConsumer())qualifyMakeUpReplay(s,source);
            if(s.makeupAo()){
                String transformedAo=patched.replaceAll("\\s+","");
                // Preserve actual compiler output even when the diagnostic shape check refuses it.
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".input.fsh"),raw);
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".transformed.fsh"),patched);
                int callerAt=transformedAo.lastIndexOf("dbao(");
                s.result.addProperty("transformed_ao_caller_excerpt",callerAt<0?"absent":transformedAo.substring(Math.max(0,callerAt-100),Math.min(transformedAo.length(),callerAt+180)));
                if(!java.util.regex.Pattern.compile("final_ao=mix\\(dbao\\(dither\\),1(?:\\.0*)?[fF]?,ao_att\\);").matcher(transformedAo).find())
                    throw new IllegalStateException("Transformed active AO caller absent");
                if(raw.contains("float shaderlodMakeupStaticAo4(")){
                    String dispatch=makeUpAoFunction(patched,"float dbao(");
                    if(!java.util.regex.Pattern.compile("if\\(shaderlodAoSamples==4\\)\\{?returnshaderlodMakeupStaticAo4\\(dither,linearDepth,farAndCheck,scaleFactor\\);").matcher(dispatch).find()
                            ||!java.util.regex.Pattern.compile("if\\(shaderlodAoSamples==5\\)\\{?returnshaderlodMakeupNativeAo5Prepared\\(dither,linearDepth,farAndCheck,scaleFactor\\);").matcher(dispatch).find())
                        throw new IllegalStateException("Transformed finite AO dispatch absent");
                }
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".input.vsh"),source.getVertexSource().orElseThrow());
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".transformed.vsh"),transformed.entrySet().stream().filter(e->"VERTEX".equals(e.getKey().toString())).map(Map.Entry::getValue).findFirst().orElseThrow());
                s.result.addProperty("four_tap_pixel_coverage_proved",false);
            }
            if(s.sildur()){
                var pack=Iris.getCurrentPack().orElse(null);
                var macros=new JsonObject();
                if(pack instanceof com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.PackView view){
                    var input=view.shaderlod$makeupPackInput();
                    if(input!=null)for(var pair:input.glslDefines())if(Set.of("IS_IRIS","MC_GL_VERSION","MC_GLSL_VERSION").contains(pair.key()))macros.addProperty(pair.key(),pair.value());
                }
                s.result.add("actual_glsl_macros",macros);
                s.result.addProperty("transformed_has_core_texture_lod",java.util.regex.Pattern.compile("textureLod\\s*\\(\\s*depthtex0").matcher(patched).find());
                s.result.addProperty("raw_has_native_ssdo_fastpath",raw.contains("float shaderlodSildurNativeSSDO("));
                s.result.addProperty("transformed_has_native_ssdo_fastpath",patched.contains("shaderlodSildurNativeSSDO("));
                int at=raw.indexOf("shaderlodAoUnused");
                s.result.addProperty("raw_sky_elision_excerpt",at<0?"absent":raw.substring(Math.max(0,at-120),Math.min(raw.length(),at+1400)));
                s.result.addProperty("raw_version_excerpt",raw.substring(0,Math.min(raw.length(),160)));
                s.result.addProperty("raw_has_depth_sky_gate",java.util.regex.Pattern.compile("shaderlodAoUnused\\s*=\\s*shaderlodAoUnused\\s*\\|\\|").matcher(raw).find());
            }
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD){
                int at=raw.indexOf("vec3 get_cloud(");
                s.result.addProperty("raw_primary_cloud_excerpt",at<0?"absent":raw.substring(at,Math.min(raw.length(),at+3500)));
                s.result.addProperty("scope_of_budget_excerpt","Actual preprocessed selected count/dispatch; not a hardware-issued iteration counter");
            }
            if(s.nativeConsumer()){
                // Complete selected compiler inputs, not markers promoted to executed work.
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".input.fsh"),raw);
                publishConsumerSource(s.output.resolveSibling(s.output.getFileName()+".transformed.fsh"),patched);
                s.observationStage="skyCompiled:program-handle";
                s.options=fullOptions();s.source=source;s.programId=program.getProgramId();s.glContext=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
                s.glCapabilities=org.lwjgl.opengl.GL.getCapabilities();s.reloads=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount();
                s.result.add("full_effective_options",new GsonBuilder().create().toJsonTree(s.options));
                s.result.addProperty("coverage_proved",false);
                s.result.addProperty("input_fragment_path",s.output.resolveSibling(s.output.getFileName()+".input.fsh").toString());
                s.result.addProperty("transformed_fragment_path",s.output.resolveSibling(s.output.getFileName()+".transformed.fsh").toString());
                s.result.addProperty("count_scope","Linked source/uniform predicate only; not issued taps, active fragments, hit rate or speed");
            }
            s.observationStage="skyCompiled:capture";
            s.compiledAt=System.nanoTime();s.renderer=renderer;s.pipeline=pipeline;s.program=program;s.pack=Iris.getCurrentPack().orElse(null);s.world=Minecraft.getInstance().level;s.buffers=source.getDirectives().getDrawBuffers().clone();
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD||s.volumetric()){
                s.source=source;s.options=fullOptions();s.glContext=org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
                s.glCapabilities=org.lwjgl.opengl.GL.getCapabilities();s.programId=program.getProgramId();
                s.reloads=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount();s.witnessAt=System.nanoTime();
            }
        }catch(Exception failure){finishSky(s,"REFUSED_IDENTITY",failure);}
    }
    private static void skyEnter(Object renderer,WorldRenderingPipeline pipeline,CompositePass group,List<?> passes){
        var s=skyPending();if(s==null)return;s.inDraw=false;if(s.program==null||group!=s.group())return;
        var client=Minecraft.getInstance();if(client.level==null||client.screen!=null||!client.isWindowActive())return;
        if((s.nativeConsumer()||s.sildur()||s.consumer==BenchmarkConsumer.MAKEUP_CLOUD||s.volumetric())&&(System.nanoTime()-s.compiledAt<3_000_000_000L||!client.levelRenderer.hasRenderedAllSections()))return;
        try{
            s.observationStage="skyEnter:owner-identity";
            if(renderer!=s.renderer||pipeline!=s.pipeline||Iris.getPipelineManager().getPipelineNullable()!=pipeline
                ||Iris.getCurrentPack().orElse(null)!=s.pack||client.level!=s.world||Iris.isFallback()||!s.expectedPack.equals(Iris.getCurrentPackName())
                ||!s.family().equals(com.pp.shaderlod.optimization.OptimizationConfig.activeFamily())
                ||client.level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)throw new IllegalStateException("Pending sky pipeline/pack/world changed");
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD)verifyMakeUpLifetime(s);
            if(s.replayConsumer())verifyMakeUpReplayLifetime(s);
            s.observationStage="skyEnter:pass-identity";
            for(Object object:passes)if(object instanceof SpatialPassAccessor pass&&s.sourceName().equals(pass.shaderlod$name())){
                if(pass.shaderlod$program()!=s.program||!Arrays.equals(s.buffers,pass.shaderlod$drawBuffers()))throw new IllegalStateException("Actual "+s.sourceName()+" pass/program changed");
                s.inDraw=true;return;
            }
            throw new IllegalStateException("Actual "+s.sourceName()+" pass absent");
        }catch(Exception failure){finishSky(s,"REFUSED_IDENTITY",failure);}
    }
    private static void skyBeforeQuad(){
        var s=skyPending();if(s==null||!s.inDraw)return;
        try{
            s.observationStage="skyBeforeQuad:readback";
            if(s.volumetric()||s.nativeConsumer()){
                int error=GL11.glGetError();s.result.addProperty("consumer_readback_error_before",error);
                if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("CONSUMER_READBACK_PREEXISTING_ERROR_"+error);
            }
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD)verifyMakeUpLifetime(s);
            if(s.replayConsumer())verifyMakeUpReplayLifetime(s);
            int id=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);if(id!=s.program.getProgramId())return;
            if(s.nativeConsumer()&&(s.source==null||s.programId!=id||!GL20.glIsProgram(id)||GL20.glGetProgrami(id,GL20.GL_DELETE_STATUS)!=GL11.GL_FALSE||s.glContext==0||!s.options.equals(fullOptions())||!Iris.getShaderPackOptionQueue().isEmpty()
                    ||s.reloads!=com.pp.shaderlod.measurement.RuntimeMeasurement.reloadCount()
                    ||s.glContext!=org.lwjgl.glfw.GLFW.glfwGetCurrentContext()||s.glCapabilities!=org.lwjgl.opengl.GL.getCapabilities()
                    ||!hash(s.source.getFragmentSource().orElseThrow()).equals(s.result.get("fragment_sha256").getAsString())))
                throw new IllegalStateException("Native-consumer compile/lifetime/options changed");
            boolean linked=GL20.glGetProgrami(id,GL20.GL_LINK_STATUS)==GL11.GL_TRUE;
            var inventory=new LinkedHashMap<String,PixelHistorySession.UniformShape>();
            int count=linked?GL20.glGetProgrami(id,GL20.GL_ACTIVE_UNIFORMS):0;
            int max=linked?GL20.glGetProgrami(id,GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH):0;
            if(count<0||count>4096||max<0||max>4096||count>0&&max<1)throw new IllegalStateException("Unbounded uniform inventory");
            try(var stack=MemoryStack.stackPush()){var size=stack.mallocInt(1);var type=stack.mallocInt(1);
                for(int i=0;i<count;i++){String name=GL20.glGetActiveUniform(id,i,max,size,type);inventory.put(name,new PixelHistorySession.UniformShape(type.get(0),size.get(0)));}}
            boolean valid=linked;var fields=new JsonObject();
            var scalarNames=new java.util.LinkedHashSet<>(List.of("shaderlodBridgeVersion","shaderlodEnabled","shaderlodPreset",s.flag()));
            if(s.sildur())scalarNames.addAll(List.of("shaderlodOptimizationFlagsVersion","shaderlodOptAoSkyElision","shaderlodOptAoBudget","shaderlodOptAoDepthReuse","shaderlodOptShadowSampleBudget","shaderlodOptCloudPrimaryBudget"));
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD||s.volumetric()||s.nativeConsumer())scalarNames.add("shaderlodOptimizationFlagsVersion");
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD)scalarNames.add("shaderlodOptCloudOpacityExit");
            if(s.makeupAo())scalarNames.add("shaderlodOptAoDepthReuse");
            if(s.consumer==BenchmarkConsumer.BLISS_NATIVE_AO)scalarNames.addAll(List.of("shaderlodOptAoDepthReuse","shaderlodOptAoSkyElision"));
            if(s.consumer==BenchmarkConsumer.PHOTON_ORDINARY_TRACE)scalarNames.addAll(List.of("shaderlodOptReflectionProjectedRefinement","shaderlodOptReflectionTraceFade","shaderlodOptReflectionRefinement"));
            for(String name:scalarNames){
                var observed=PixelHistorySession.observeUniform(name,inventory,new PixelHistorySession.UniformReader(){public int location(String n){return linked?GL20.glGetUniformLocation(id,n):-1;}public int integer(int location){return GL20.glGetUniformi(id,location);}});
                fields.add(name,new GsonBuilder().create().toJsonTree(observed));valid&="read".equals(observed.status());
            }
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            s.result.addProperty("linked",linked);s.result.addProperty("current_program",id);s.result.addProperty("program_object_identity",System.identityHashCode(s.program));
            s.result.addProperty("pipeline_object_identity",System.identityHashCode(s.pipeline));s.result.addProperty("pack_object_identity",System.identityHashCode(s.pack));
            s.result.addProperty("selected_pack",Iris.getCurrentPackName());s.result.addProperty("actual_pass_program_identity_verified",true);
            s.result.add("draw_buffers",new GsonBuilder().create().toJsonTree(s.buffers));s.result.add("viewport",new GsonBuilder().create().toJsonTree(viewport));s.result.add("required_scalar_int_uniforms",fields);
            if(s.nativeConsumer()){
                var floats=new JsonObject();
                for(String name:List.of("shaderlodNear","shaderlodFar","shaderlodTransition")){
                    var shape=inventory.get(name);int location=GL20.glGetUniformLocation(id,name);
                    if(shape==null||shape.type()!=GL11.GL_FLOAT||shape.size()!=1||location<0){valid=false;floats.add(name,com.google.gson.JsonNull.INSTANCE);}
                    else{float value=GL20.glGetUniformf(id,location);valid&=Float.isFinite(value);if(Float.isFinite(value))floats.addProperty(name,value);else floats.add(name,com.google.gson.JsonNull.INSTANCE);}
                }
                s.result.add("core_band_float_uniforms",floats);
                if(valid)valid=validConsumerBands(floats.get("shaderlodNear").getAsFloat(),floats.get("shaderlodFar").getAsFloat(),floats.get("shaderlodTransition").getAsFloat());
                var expected=new LinkedHashMap<String,Integer>();
                expected.put("shaderlodBridgeVersion",2);expected.put("shaderlodEnabled",1);expected.put("shaderlodPreset",1);expected.put("shaderlodOptimizationFlagsVersion",1);
                for(String name:scalarNames)if(name.startsWith("shaderlodOpt")&&!name.equals("shaderlodOptimizationFlagsVersion"))
                    expected.put(name,name.equals(s.flag())?com.pp.shaderlod.optimization.OptimizationConfig.snapshot().uniform(com.pp.shaderlod.optimization.OptimizationId.fromKey(s.consumer==BenchmarkConsumer.BLISS_NATIVE_AO||s.makeupAo()?"ao_budget":"reflection_trace_budget")):0);
                for(var e:expected.entrySet()){var field=fields.getAsJsonObject(e.getKey());valid&=field!=null&&field.has("integerValue")&&!field.get("integerValue").isJsonNull()&&field.get("integerValue").getAsInt()==e.getValue();}
                valid&=expected.get(s.flag())==0||expected.get(s.flag())==1;
                s.result.addProperty("normal_animation_clock",!ShaderAnimationClock.fixed());valid&=!ShaderAnimationClock.fixed();
            }
            if(s.volumetric()){
                int requested=com.pp.shaderlod.optimization.OptimizationConfig.snapshot().uniform(com.pp.shaderlod.optimization.OptimizationId.VOLUMETRIC_LIGHT_BUDGET);
                for(var expected:Map.of("shaderlodBridgeVersion",2,"shaderlodEnabled",1,"shaderlodPreset",1,"shaderlodOptimizationFlagsVersion",1,s.flag(),requested).entrySet()){
                    var field=fields.getAsJsonObject(expected.getKey());
                    valid&=field!=null&&field.has("integerValue")&&!field.get("integerValue").isJsonNull()&&field.get("integerValue").getAsInt()==expected.getValue();
                }
                valid&=requested==0||requested==1;
                s.result.addProperty("selected_cheap_ray_samples",valid?(requested==1?3:4):null);
                s.result.addProperty("selected_count_is_source_and_uniform_inference",true);
                s.result.addProperty("actual_dimension",clientDimension());
                s.result.addProperty("normal_animation_clock",!ShaderAnimationClock.fixed());
                valid&=!ShaderAnimationClock.fixed();
            }
            if(s.consumer==BenchmarkConsumer.MAKEUP_CLOUD){
                var clock=observeAnimationClock(inventory,new AnimationClockReader(){
                    public int location(String name){return linked?GL20.glGetUniformLocation(id,name):-1;}
                    public float value(int location){return GL20.glGetUniformf(id,location);}
                });
                s.result.add("frame_time_counter_uniform",new GsonBuilder().create().toJsonTree(clock));
                var animationClock=new JsonObject();
                animationClock.addProperty("fixed",ShaderAnimationClock.fixed());
                animationClock.addProperty("source",ShaderAnimationClock.source());
                animationClock.addProperty("fixed_value",ShaderAnimationClock.fixedValue());
                animationClock.addProperty("current_frame_time_counter",SystemTimeUniforms.TIMER.getFrameTimeCounter());
                animationClock.addProperty("native_last_frame_time",SystemTimeUniforms.TIMER.getLastFrameTime());
                animationClock.addProperty("native_iris_frame_counter",SystemTimeUniforms.COUNTER.getAsInt());
                animationClock.addProperty("scope",ShaderAnimationClock.SCOPE);
                boolean clockVerified=ShaderAnimationClock.fixed()&&"read".equals(clock.status())
                        &&clock.floatValue()!=null&&clock.floatValue().floatValue()==ShaderAnimationClock.fixedValue().floatValue()
                        &&SystemTimeUniforms.TIMER.getFrameTimeCounter()==ShaderAnimationClock.fixedValue().floatValue();
                animationClock.addProperty("fixed_uniform_verified",clockVerified);
                s.result.add("shader_animation_clock",animationClock);
                if(ShaderAnimationClock.fixed())valid&=clockVerified;
                for(var expected:Map.of("shaderlodBridgeVersion",2,"shaderlodEnabled",1,"shaderlodPreset",1,"shaderlodOptimizationFlagsVersion",1,
                        s.flag(),com.pp.shaderlod.optimization.OptimizationConfig.snapshot().uniform(com.pp.shaderlod.optimization.OptimizationId.CLOUD_PRIMARY_BUDGET)).entrySet()){
                    var field=fields.getAsJsonObject(expected.getKey());
                    valid&=field!=null&&field.has("integerValue")&&field.get("integerValue").getAsInt()==expected.getValue();
                }
                s.result.addProperty("same_program_reload_count",s.reloads);
                s.result.add("full_effective_options",new GsonBuilder().create().toJsonTree(s.options));
            }
            if(s.sildur()){
                s.result.addProperty("gl_arb_shader_texture_lod",org.lwjgl.opengl.GL.getCapabilities().GL_ARB_shader_texture_lod);
                var depth=com.pp.shaderlod.optimization.sildurao.SildurAoGlProof.binding(id,"depthtex0");
                s.result.add("depthtex0",new GsonBuilder().create().toJsonTree(depth));
                s.result.addProperty("depth_nonmipped_sampling",depth!=null&&depth.base()==0&&depth.compare()==GL11.GL_NONE&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(depth.min())&&Set.of(GL11.GL_NEAREST,GL11.GL_LINEAR).contains(depth.mag()));
                if(s.consumer==BenchmarkConsumer.SILDUR_AO_SKY&&depth!=null&&depth.width()>0&&depth.height()>0&&(long)depth.width()*depth.height()<=16_777_216L){
                    int unit=com.pp.shaderlod.optimization.sildurao.SildurAoGlProof.integer(id,"depthtex0"),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                    if(unit>=0){try{
                        GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
                        int alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
                        if((alignment!=1&&alignment!=4)||GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING)!=0)
                            throw new IllegalStateException("DEPTH_READBACK_PACK_BUFFER_OR_ALIGNMENT");
                        for(int name:new int[]{GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST})
                            if(GL11.glGetInteger(name)!=0)throw new IllegalStateException("DEPTH_READBACK_PACK_STATE_"+name);
                        int before=GL11.glGetError();s.result.addProperty("depth_readback_error_before",before);
                        if(before!=GL11.GL_NO_ERROR)throw new IllegalStateException("DEPTH_READBACK_PREEXISTING_ERROR_"+before);
                        var pixels=org.lwjgl.BufferUtils.createFloatBuffer(depth.width()*depth.height());
                        GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,pixels);
                        int after=GL11.glGetError();s.result.addProperty("depth_readback_error_after",after);
                        if(after!=GL11.GL_NO_ERROR)throw new IllegalStateException("DEPTH_READBACK_ERROR_"+after);
                        long sky=0,geometry=0,invalid=0;
                        for(int i=0;i<pixels.capacity();i++){float z=pixels.get(i);if(!Float.isFinite(z))invalid++;else if(z>=1.0f)sky++;else if(z>=0.0f)geometry++;else invalid++;}
                        var coverage=new JsonObject();coverage.addProperty("sky_depth_pixels",sky);coverage.addProperty("geometry_depth_pixels",geometry);coverage.addProperty("invalid_depth_pixels",invalid);coverage.addProperty("total_pixels",pixels.capacity());
                        coverage.addProperty("scope","Untimed current native depth0 texture readback; sky eligibility requires captured source gate and controls, not an executed-loop count");
                        s.result.add("depth_coverage",coverage);
                    }finally{GL13.glActiveTexture(active);}}
                }

            }
            if(s.volumetric()||s.nativeConsumer()){
                int error=GL11.glGetError();s.result.addProperty("consumer_readback_error_after",error);
                if(error!=GL11.GL_NO_ERROR)throw new IllegalStateException("CONSUMER_READBACK_ERROR_"+error);
            }
            finishSky(s,valid?"OBSERVED":"UNKNOWN_REQUIRED_UNIFORM",null);
        }catch(Exception failure){finishSky(s,"REFUSED_READBACK",failure);}
    }

    static boolean validConsumerBands(float near,float far,float transition){
        return Float.isFinite(near)&&Float.isFinite(far)&&Float.isFinite(transition)
            &&near>0&&far>near&&far<=4096&&transition>=0&&transition<=far-near&&transition*.5f<=near;
    }
    /** Complete bounded UTF-8 export, recoverable on retry, with atomic-only publication. */
    static void publishConsumerSource(Path output,String source)throws java.io.IOException {
        byte[] complete=source.getBytes(StandardCharsets.UTF_8);
        if(complete.length==0||complete.length>16*1024*1024)throw new java.io.IOException("Consumer source absent or exceeds 16 MiB");
        if(Files.exists(output)){
            if(Files.size(output)!=complete.length)throw new java.io.IOException("Existing consumer source size differs");
            try(var input=Files.newInputStream(output)){if(!Arrays.equals(complete,input.readNBytes(complete.length+1)))throw new java.io.IOException("Existing consumer source differs");}
            return;
        }
        Path temporary=Files.createTempFile(output.toAbsolutePath().getParent(),"consumer-source-",".tmp");
        try{Files.write(temporary,complete);Files.move(temporary,output,java.nio.file.StandardCopyOption.ATOMIC_MOVE);}
        finally{Files.deleteIfExists(temporary);}
    }

    private static String clientDimension(){return Minecraft.getInstance().level.dimension().location().toString();}

    interface AnimationClockReader { int location(String name); float value(int location); }
    record AnimationClockObservation(String kind, String activeName, Integer type, Integer size,
            Integer location, Float floatValue, String status, String error) {}
    /** Read-only FLOAT_SCALAR evidence; missing/array/wrong-type paths never imply a frozen clock. */
    static AnimationClockObservation observeAnimationClock(Map<String,PixelHistorySession.UniformShape> inventory,
            AnimationClockReader reader) {
        String name="frameTimeCounter";
        String active=inventory.containsKey(name)?name:inventory.containsKey(name+"[0]")?name+"[0]":null;
        var shape=active==null?null:inventory.get(active);Integer location=null;Float value=null;
        String status;
        try{
            location=reader.location(name);
            if(shape==null)status="missing_or_inactive";
            else if(!name.equals(active)||shape.type()!=GL11.GL_FLOAT||shape.size()!=1)status="wrong_type_or_size";
            else if(location<0)status="location_unknown";
            else{value=reader.value(location);status=Float.isFinite(value)?"read":"nonfinite_value";}
            // JSON cannot encode NaN/Infinity; keep the explicit refusal instead.
            return new AnimationClockObservation("FLOAT_SCALAR",active,shape==null?null:shape.type(),shape==null?null:shape.size(),
                    location,value!=null&&Float.isFinite(value)?value:null,status,null);
        }catch(RuntimeException failure){
            return new AnimationClockObservation("FLOAT_SCALAR",active,shape==null?null:shape.type(),shape==null?null:shape.size(),
                    location,null,"read_failed",failure.toString());
        }
    }

    private static State requested() {
        if (REQUEST == null || stopped) return null;
        if (state == null) try { state = new State(Path.of(REQUEST)); }
        catch (Exception failure) { stopped = true; com.mojang.logging.LogUtils.getLogger().warn("Settled consumer request rejected", failure); }
        return state;
    }

    /** Explicit dev Apply only; resets settlement after every successful Apply, including no-op. */
    public static void afterApply() {
        if (REQUEST != null && state != null && state.complete) { stopped = false; state.complete = false; }
        var s = requested();
        if (s == null) return;
        try {
            if (++s.applyGeneration > 8) throw new IllegalStateException("EXPLICIT_APPLY_LIMIT_EXCEEDED");
            if (!s.captures.isEmpty()) {
                var prior = new JsonObject(); prior.addProperty("apply_generation", s.applyGeneration - 1);
                prior.add("observations", s.captures.deepCopy()); s.superseded.add(prior);
            }
            while (!s.captures.isEmpty()) s.captures.remove(s.captures.size() - 1);
            s.captured.clear(); s.context = null; s.stableAt = 0;
            s.appliedAt = System.nanoTime();
            s.report.addProperty("apply_generation", s.applyGeneration);
            s.report.addProperty("last_apply_observed_utc", Instant.now().toString());
            s.write("waiting_for_stable_selected_pipeline");
        } catch (Exception failure) { fail(s, failure); }
    }

    public static void compiledProgram(Object renderer, WorldRenderingPipeline pipeline, CompositePass group,
            ProgramSource source, Program program, Map<?, String> transformed) {
        skyCompiled(renderer,pipeline,group,source,program,transformed);
        var s = requested();
        if (s == null || source == null || program == null || !s.allowed(group, source.getName())
                || !s.expectedPack.equals(Iris.getCurrentPackName())) return;
        try {
            String fragment = source.getFragmentSource().orElseThrow();
            String transformedFragment = transformed.entrySet().stream()
                    .filter(e -> "FRAGMENT".equals(e.getKey().toString())).map(Map.Entry::getValue)
                    .findFirst().orElseThrow();
            // Named pinned adapter path, not a declaration-only source match.
            String marker = s.workArea ? "pinned-effect-rate-pilot" : s.samplerOnly ? "atmosphere_scattering" : s.adapter.equals("photon-live-pixel-budget") ? "shaderlod_live_pixel_scale" : "shaderlod_effect_budget";
            if (s.samplerOnly || s.workArea) s.requireArchive();
            // Failure evidence only: stage actual strings before the original acceptance expression.
            JsonObject sourceDiagnostic = s.workArea
                    ? pilotSourceDiagnostic(s, ++s.compilationSequence, renderer, pipeline, group, source,
                            program, fragment, transformedFragment) : null;
            if (!(s.workArea ? pilotConsumerCall(s.adapter, source.getName(), fragment) : consumerCall(s.adapter, fragment))
                    || !(s.workArea ? pilotConsumerCall(s.adapter, source.getName(), transformedFragment) : consumerCall(s.adapter, transformedFragment))
                    || (s.samplerOnly && s.compileBoundary && (!fragment.contains("shaderlod_sky_owned_atmosphere(")
                        || !transformedFragment.contains("shaderlod_sky_owned_atmosphere(")
                        || !fragment.contains("textureLod(depthtex0,")
                        || !transformedFragment.contains("textureLod(customtex0,")))) {
                if (sourceDiagnostic != null && !s.report.has("failed_source_diagnostic"))
                    s.report.add("failed_source_diagnostic", sourceDiagnostic);
                throw new IllegalStateException("EXPECTED_ADAPTER_CONSUMER_PATH_ABSENT_" + source.getName());
            }
            var raw = new JsonObject();
            raw.addProperty("source_name", source.getName()); raw.addProperty("pass_group", group.name());
            raw.addProperty("fragment_sha256", hash(fragment));
            raw.addProperty("transformed_fragment_sha256", hash(transformedFragment));
            raw.addProperty("consumer_marker", marker);
            if (s.samplerOnly || s.workArea) {
                raw.addProperty("preprocessed_fragment", fragment);
                raw.addProperty("transformed_fragment", transformedFragment);
            }
            s.programs.put(program, new Compiled(renderer, pipeline, program, source.getName(),
                    source.getDirectives().getDrawBuffers().clone(), raw));
            if (s.programs.size() > 32) throw new IllegalStateException("COMPILED_IDENTITY_LIMIT");
            s.stableAt = 0;
            if (s.compileBoundary) {
                s.appliedAt = System.nanoTime();
                s.report.addProperty(s.workArea ? "selected_pilot_compile_completed_utc" : "selected_deferred4_compile_completed_utc", Instant.now().toString());
                s.write("waiting_for_stable_compiled_selected_pipeline");
            }
        } catch (Exception failure) { fail(s, failure); }
    }

    public static void enterRenderer(Object renderer, WorldRenderingPipeline pipeline, CompositePass group, List<?> passes) {
        skyEnter(renderer,pipeline,group,passes);
        var s = requested();
        if (s == null) return;
        s.context = null;
        if (s.appliedAt == 0) return;
        try {
            long now = System.nanoTime();
            if (now - s.appliedAt > s.deadlineNanos) throw new IllegalStateException("SETTLEMENT_DEADLINE_EXCEEDED");
            var client = Minecraft.getInstance();
            if (client.level == null || client.player == null || client.screen != null || client.isPaused()
                    || Iris.isFallback() || Iris.getPipelineManager().getPipelineNullable() != pipeline
                    || !s.expectedPack.equals(Iris.getCurrentPackName())) { s.stableAt = 0; return; }
            Object pack = Iris.getCurrentPack().orElse(null);
            if (pack == null) { s.stableAt = 0; return; }
            if (s.pipeline != pipeline || s.pack != pack || s.world != client.level || s.stableAt == 0) {
                if (!s.captured.isEmpty()) throw new IllegalStateException("IDENTITY_CHANGED_DURING_CAPTURE");
                s.pipeline = pipeline; s.pack = pack; s.world = client.level; s.stableAt = now;
            }
            if (now - s.stableAt < s.settleNanos) return;
            var selected = new IdentityHashMap<Program, Compiled>();
            for (Object object : passes) {
                if (!(object instanceof SpatialPassAccessor pass) || !s.allowed(group, pass.shaderlod$name())) continue;
                var compiled = s.programs.get(pass.shaderlod$program());
                if (compiled == null || compiled.renderer != renderer || compiled.pipeline != pipeline
                        || !compiled.name.equals(pass.shaderlod$name())
                        || !Arrays.equals(compiled.drawBuffers, pass.shaderlod$drawBuffers()))
                    throw new IllegalStateException("ACTUAL_PASS_SOURCE_IDENTITY_MISMATCH");
                selected.put(compiled.program, compiled);
            }
            s.context = selected;
        } catch (Exception failure) { fail(s, failure); }
    }

    public static void leaveRenderer() { if(benchmarkSky!=null)benchmarkSky.inDraw=false; if (REQUEST != null && state != null) state.context = null; }

    /** Existing quad HEAD is after the pinned Program.use and CustomUniforms.push calls. */
    public static void beforeQuadDraw() {
        skyBeforeQuad();
        var s = requested();
        if (s == null || s.context == null || s.context.isEmpty()) return;
        try {
            if (s.context.values().stream().allMatch(c -> s.captured.containsKey(c.name))) return;
            int programId = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            Compiled selected = null;
            for (var c : s.context.values()) if (c.program.getProgramId() == programId) {
                if (selected != null) throw new IllegalStateException("AMBIGUOUS_REAL_PROGRAM_ID");
                selected = c;
            }
            if (selected == null || s.captured.containsKey(selected.name)) return;
            if (selected.pipeline != s.pipeline || Iris.getPipelineManager().getPipelineNullable() != s.pipeline)
                throw new IllegalStateException("PIPELINE_CHANGED_AT_CONSUMER");
            var raw = selected.raw.deepCopy();
            raw.addProperty("timestamp_utc", Instant.now().toString());
            raw.addProperty("current_program", programId);
            raw.addProperty("program_object_identity", System.identityHashCode(selected.program));
            raw.addProperty("pipeline_object_identity", System.identityHashCode(selected.pipeline));
            raw.addProperty("selected_pack_object_identity", System.identityHashCode(s.pack));
            raw.addProperty("actual_pass_program_identity_verified", true);
            raw.addProperty("iris_frame_counter", SystemTimeUniforms.COUNTER.getAsInt());
            raw.addProperty("frame_time_counter", SystemTimeUniforms.TIMER.getFrameTimeCounter());
            raw.addProperty(s.compileBoundary ? "milliseconds_after_selected_compile" : "milliseconds_after_last_apply", (System.nanoTime() - s.appliedAt) / 1e6);
            raw.addProperty("stable_pipeline_milliseconds", (System.nanoTime() - s.stableAt) / 1e6);
            boolean linked = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_TRUE;
            raw.addProperty("linked", linked);
            var inventory = new LinkedHashMap<String, PixelHistorySession.UniformShape>();
            if (linked) {
                int count = GL20.glGetProgrami(programId, GL20.GL_ACTIVE_UNIFORMS);
                int length = GL20.glGetProgrami(programId, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
                if (count < 0 || count > 4096 || length < 0 || length > 4096 || (count > 0 && length < 1))
                    throw new IllegalStateException("UNBOUNDED_UNIFORM_INVENTORY");
                try (var stack = MemoryStack.stackPush()) {
                    var size = stack.mallocInt(1); var type = stack.mallocInt(1);
                    for (int i = 0; i < count; i++) {
                        String name = GL20.glGetActiveUniform(programId, i, length, size, type);
                        if (inventory.put(name, new PixelHistorySession.UniformShape(type.get(0), size.get(0))) != null)
                            throw new IllegalStateException("DUPLICATE_ACTIVE_UNIFORM");
                    }
                }
            }
            var uniforms = new JsonObject(); boolean valid = linked;
            for (String name : s.required) {
                var observed = PixelHistorySession.observeUniform(name, inventory, new PixelHistorySession.UniformReader() {
                    public int location(String field) { return linked ? GL20.glGetUniformLocation(programId, field) : -1; }
                    public int integer(int location) { return GL20.glGetUniformi(programId, location); }
                });
                var field = new JsonObject();
                field.addProperty("active_name", observed.activeName()); field.addProperty("type", observed.type());
                field.addProperty("size", observed.size()); field.addProperty("location", observed.location());
                field.addProperty("integer_value", observed.integerValue()); field.addProperty("read_status", observed.status());
                field.addProperty("error", observed.error()); uniforms.add(name, field);
                valid &= observed.status().equals("read");
            }
            raw.add("required_scalar_int_uniforms", uniforms);
            if (s.samplerOnly) {
                s.requireArchive();
                raw.add("sampler_state", com.pp.shaderlod.dev.timing.CloudSamplerStateProbe.joinedSamplerState());
                raw.add("pinned_archive", s.report.get("pinned_archive").deepCopy());
            }
            if (s.workArea) {
                s.requireArchive();
                raw.add("pinned_archive", s.report.get("pinned_archive").deepCopy());
                try {
                    raw.add("work_area", observePilotWorkArea(s, selected, programId, inventory, raw));
                    raw.remove("work_area_query_diagnostic");
                } catch (Exception failure) {
                    raw.addProperty("evidence_kind", "failed_work_area_diagnostic_only");
                    raw.addProperty("accepted_observation", false);
                    s.report.add("failed_work_area_diagnostic", raw);
                    throw failure;
                }
            }
            s.captures.add(raw); s.captured.put(selected.name, selected);
            s.write(valid ? "capturing_named_consumers" : "failed_required_uniform_readback");
            if (!valid) throw new IllegalStateException("REQUIRED_SCALAR_INT_UNIFORM_UNKNOWN");
            if (s.captured.keySet().equals(s.consumers)) { s.write(s.workArea ? "complete_source_joined_work_area_observation" : s.samplerOnly ? "complete_source_joined_sampler_observation" : "complete_typed_consumer_observation"); s.complete = true; stopped = true; }
        } catch (Exception failure) { fail(s, failure); }
    }

    private static void fail(State s, Exception failure) {
        stopped = true; s.report.addProperty("failure", failure.toString());
        try { s.write("failed"); } catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
        com.mojang.logging.LogUtils.getLogger().warn("Settled consumer observer stopped", failure);
    }
    private static String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
    private static boolean consumerCall(String adapter, String fragment) {
        if (adapter.equals("photon-atmosphere-lut"))
            return fragment.contains("atmosphere_scattering(") && fragment.contains("draw_sky(");
        if (adapter.equals("bliss-shader"))
            return java.util.regex.Pattern.compile("\\bSAMPLECOUNT\\s*=\\s*shaderlod_effect_budget\\s*\\(").matcher(fragment).find();
        var calls = java.util.regex.Pattern.compile("\\bshaderlod_live_pixel_scale\\s*\\(").matcher(fragment);
        // Definition plus at least one call in the pinned pixel-budget path.
        return calls.find() && calls.find();
    }
    private static String pilotArchiveName(String adapter) {
        if (adapter.equals("complementary-half-vl-pilot")) return "ComplementaryReimagined_r5.9.3_VLRate_r4_phase.zip";
        if (adapter.equals("bsl-half-ao-pilot")) return "BSL-AO-half-enabled-r1d.zip";
        throw new IllegalArgumentException("BSL pilot is awaiting frozen source contract");
    }
    private static String pilotArchiveSha(String adapter) {
        if (adapter.equals("complementary-half-vl-pilot")) return "7aab6df838161c94de8906f51f4e4d22a929cbd5f86e1bac88fb1fc80b4125a4";
        if (adapter.equals("bsl-half-ao-pilot")) return "4a4d5b3b7693d137d889eba02a54fc11e9ebe57643489c0fa430da4380f7f468";
        throw new IllegalArgumentException("BSL pilot is awaiting frozen source contract");
    }
    private static Map<String, String> pilotPins(String adapter) {
        if (adapter.equals("complementary-half-vl-pilot")) return Map.ofEntries(
                Map.entry("shaders/lib/atmospherics/volumetricLight/volumetricLight.glsl", "059fc2d0b6c44471d29c1264409c81ceb1ad6816fd88639cbc1bd420bddcd995"),
                Map.entry("shaders/lib/common.glsl", "26f75723c66f07ab539c1b737d1f98b5a77911634d7e98a8612d135c142aab83"),
                Map.entry("shaders/program/composite1.glsl", "330c47dc1a0b5ea1790643aac6fcb9f9c13acdc77d0683705a47bb4c8dea87d0"),
                Map.entry("shaders/shaders.properties", "6f0bc362ec33dd56ceee3385ce62197e93b2dbf2569c84b5317aaad5fd4c0f19"),
                Map.entry("shaders/world0/composite1.fsh", "893d6ad90d31d4dd6fd857bd75a5fc3b29017889f00d2b747a762977436b779d"),
                Map.entry("shaders/world0/composite1.vsh", "65af69ac2273bd2324dc6437ca312d7d5960d75e8436fddc0e44dfc441b3ed25"),
                Map.entry("shaders/world0/composite2.fsh", "caf4237c9f3a53d78b18c5ae73650a301e3fd2ba5220d8458614c370156b4c8f"),
                Map.entry("shaders/world0/composite2.vsh", "eb54c4158af328c6515d0294141b9e26d7d76742fab868f3aafb74e5cc71b1cb"),
                Map.entry("shaders/program/shaderlod_vl_rate.glsl", "d27c33e917ddbe19e9654ae3d64a1a4439943338b3af53627d9e26845ff13d13"),
                Map.entry("shaders/lib/shaderlod/vl_rate_resolve.glsl", "4a292832e913b53392bf45c56245f09574dd279f9a7caafb1832045a5737637d"));
        if (adapter.equals("bsl-half-ao-pilot")) return Map.ofEntries(
                Map.entry("shaders/world0/deferred.fsh", "ee78c8d053617ce5e21c8824df86a542d4fe58d36ef1fad8e138a7e2da3d910d"),
                Map.entry("shaders/world0/deferred1.fsh", "3095370970d6ced710c8d859f44908b7c12a3f946fddcb53a274aee3660579d3"),
                Map.entry("shaders/program/deferred.glsl", "14b346b338a3d5d083690fe059cd96ca5fb054ac5835ea6ed682da19358c3352"),
                Map.entry("shaders/program/deferred1.glsl", "25825d5a8f5c5249ab172b71079610bfa815a9738851a68a69b45baf692495fc"),
                Map.entry("shaders/lib/settings.glsl", "06857bf51f4a7e29aa257b66d6d977fc8a20cac6e69b254fa26cd4c4711e1aea"),
                Map.entry("shaders/shaders.properties", "74a45a62c60f169f8e66419ac7a65489735e335f208d2f39c41de09240d725d6"),
                Map.entry("shaders/lib/shaderlod/bsl_ao_kernel.glsl", "6e6df637b472d3ec51ab7565ddf01a4d5cfccc0186bb714d96beb51c0771b2de"),
                Map.entry("shaders/lib/shaderlod/bsl_ao_reconstruct.glsl", "c3afc1836dfd9b4759974cf82e12337a2bc6956b5410dcbfd4b469e5c8d7582e"));
        throw new IllegalArgumentException("BSL pilot is awaiting frozen source contract");
    }
    private static boolean pilotConsumerCall(String adapter, String name, String fragment) {
        if (adapter.equals("complementary-half-vl-pilot"))
            return name.equals("composite1") ? fragment.contains("GetVolumetricLight(") && fragment.contains("texelFetch(depthtex0, scenePixel")
                    : fragment.contains("ShaderLODResolveVL(") && fragment.contains("texelFetch(colortex14,") && fragment.contains("texelFetch(colortex15,");
        return name.equals("deferred") ? fragment.contains("AmbientOcclusion(z, depthtex0") && fragment.contains("GetLinearDepth(z, gbufferProjectionInverse)")
                : fragment.contains("shaderlodBslHalfAo(z)") && (fragment.contains("texture2DLod(colortex10,") || fragment.contains("textureLod(colortex10,"));
    }
    /** Diagnostic construction cannot replace the original consumer exception or acceptance. */
    private static JsonObject pilotSourceDiagnostic(State s, int sequence, Object renderer,
            WorldRenderingPipeline pipeline, CompositePass group, ProgramSource source, Program program,
            String fragment, String transformedFragment) {
        var result = new JsonObject();
        try {
            result.addProperty("evidence_kind", "failed_compilation_source_diagnostic_only");
            result.addProperty("accepted_observation", false);
            result.addProperty("compilation_callback_sequence", sequence);
            result.addProperty("timestamp_utc", Instant.now().toString());
            result.addProperty("adapter", s.adapter);
            result.addProperty("source_name", source.getName());
            result.addProperty("pass_group", group.name());
            result.addProperty("expected_pack_name", s.expectedPack);
            result.addProperty("pipeline_object_identity", System.identityHashCode(pipeline));
            result.addProperty("renderer_object_identity", System.identityHashCode(renderer));
            result.addProperty("program_object_identity", System.identityHashCode(program));
            result.addProperty("apply_generation", s.applyGeneration);
            result.addProperty("explicit_apply_observed", s.applyGeneration > 0);
            result.addProperty("compile_boundary_enabled", s.compileBoundary);
            result.addProperty("preprocessed_fragment", fragment);
            result.addProperty("transformed_fragment", transformedFragment);
            result.add("preprocessed_literal_predicates", pilotLiteralPredicates(s.adapter, source.getName(), fragment));
            result.add("transformed_literal_predicates", pilotLiteralPredicates(s.adapter, source.getName(), transformedFragment));
            result.addProperty("preprocessed_pilot_consumer_call", pilotConsumerCall(s.adapter, source.getName(), fragment));
            result.addProperty("transformed_pilot_consumer_call", pilotConsumerCall(s.adapter, source.getName(), transformedFragment));
            var boundary = new JsonObject();
            boundary.addProperty("preprocessed_shaderlod_sky_owned_atmosphere_call", fragment.contains("shaderlod_sky_owned_atmosphere("));
            boundary.addProperty("transformed_shaderlod_sky_owned_atmosphere_call", transformedFragment.contains("shaderlod_sky_owned_atmosphere("));
            boundary.addProperty("preprocessed_textureLod_depthtex0_call", fragment.contains("textureLod(depthtex0,"));
            boundary.addProperty("transformed_textureLod_customtex0_call", transformedFragment.contains("textureLod(customtex0,"));
            boundary.addProperty("photon_clause_applicable", s.samplerOnly && s.compileBoundary);
            boundary.addProperty("existing_compile_boundary_clause_passes", !(s.samplerOnly && s.compileBoundary)
                    || (fragment.contains("shaderlod_sky_owned_atmosphere(")
                        && transformedFragment.contains("shaderlod_sky_owned_atmosphere(")
                        && fragment.contains("textureLod(depthtex0,")
                        && transformedFragment.contains("textureLod(customtex0,")));
            result.add("compile_boundary_literal_predicates", boundary);
            result.addProperty("first_failing_clause", !pilotConsumerCall(s.adapter, source.getName(), fragment)
                    ? "preprocessed_pilot_consumer_call" : !pilotConsumerCall(s.adapter, source.getName(), transformedFragment)
                    ? "transformed_pilot_consumer_call" : !boundary.get("existing_compile_boundary_clause_passes").getAsBoolean()
                    ? "compile_boundary_literal_clause" : "none");
            result.add("pinned_archive", s.report.get("pinned_archive").deepCopy());
            result.addProperty("fragment_sha256", hash(fragment));
            result.addProperty("transformed_fragment_sha256", hash(transformedFragment));
            var client = Minecraft.getInstance();
            var selectedPack = Iris.getCurrentPack().orElse(null);
            var currentPipeline = Iris.getPipelineManager().getPipelineNullable();
            result.addProperty("selected_pack_name", Iris.getCurrentPackName());
            result.addProperty("selected_pack_present", selectedPack != null);
            result.addProperty("selected_pack_object_identity", System.identityHashCode(selectedPack));
            result.addProperty("current_pipeline_present", currentPipeline != null);
            result.addProperty("current_pipeline_object_identity", System.identityHashCode(currentPipeline));
            result.addProperty("callback_pipeline_is_current_pipeline", pipeline == currentPipeline);
            result.addProperty("pipeline_class", pipeline == null ? null : pipeline.getClass().getName());
            result.addProperty("renderer_class", renderer == null ? null : renderer.getClass().getName());
            result.addProperty("program_id", program.getProgramId());
            result.add("source_draw_buffers", new com.google.gson.Gson().toJsonTree(source.getDirectives().getDrawBuffers()));
            result.addProperty("world_present", client.level != null);
            result.addProperty("player_present", client.player != null);
            result.addProperty("world_object_identity", System.identityHashCode(client.level));
            result.addProperty("world_dimension", client.level == null ? null : client.level.dimension().location().toString());
            result.addProperty("screen_class", client.screen == null ? null : client.screen.getClass().getName());
            result.addProperty("paused", client.isPaused());
            result.addProperty("iris_fallback", Iris.isFallback());
            result.addProperty("phase", client.level == null ? "no_world_selected_compilation"
                    : client.screen == null ? "world_without_screen_selected_compilation" : "world_with_screen_selected_compilation");
            result.addProperty("phase_policy", "Callback-time state only; no later pipeline, settled draw, source execution or quality inference");
        } catch (Exception diagnosticFailure) {
            result.addProperty("diagnostic_error", diagnosticFailure.toString());
        }
        return result;
    }
    private static JsonObject pilotLiteralPredicates(String adapter, String name, String fragment) {
        String[] literals;
        if (adapter.equals("complementary-half-vl-pilot"))
            literals = name.equals("composite1")
                    ? new String[]{"GetVolumetricLight(", "texelFetch(depthtex0, scenePixel"}
                    : new String[]{"ShaderLODResolveVL(", "texelFetch(colortex14,", "texelFetch(colortex15,"};
        else literals = name.equals("deferred")
                    ? new String[]{"AmbientOcclusion(z, depthtex0", "GetLinearDepth(z, gbufferProjectionInverse)"}
                    : new String[]{"shaderlodBslHalfAo(z)", "texture2DLod(colortex10,", "textureLod(colortex10,"};
        var result = new JsonObject();
        for (String literal : literals) result.addProperty(literal, fragment.contains(literal));
        return result;
    }
    private static JsonObject textureSize(int texture) {
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int old = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var result = new JsonObject();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            result.addProperty("texture_id", texture);
            result.addProperty("width", GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
            result.addProperty("height", GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
            result.addProperty("internal_format", GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT));
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, old); }
        if (GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) != active || GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != old)
            throw new IllegalStateException("TEXTURE_QUERY_STATE_NOT_RESTORED");
        result.addProperty("texture_binding_before", old); result.addProperty("texture_binding_after", GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D));
        result.addProperty("active_unit_before", active); result.addProperty("active_unit_after", GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE));
        result.addProperty("texture_binding_restored", true);
        return result;
    }
    private static JsonObject observePilotWorkArea(State s, Compiled selected, int program, Map<String, PixelHistorySession.UniformShape> inventory, JsonObject attemptedDraw) throws Exception {
        boolean bsl = s.adapter.equals("bsl-half-ao-pilot");
        boolean producer = selected.name.equals(bsl ? "deferred" : "composite1");
        int[] targets = bsl ? new int[]{10} : new int[]{14,15};
        int[] formats = bsl ? new int[]{GL30.GL_RG32F} : new int[]{GL30.GL_RGBA16F, GL30.GL_RGBA32F};
        var client = Minecraft.getInstance(); var window = client.getWindow();
        String glVersion = GL11.glGetString(GL11.GL_VERSION);
        if (glVersion == null || !glVersion.startsWith("4.1 Metal - ") || !org.lwjgl.opengl.GL.getCapabilities().OpenGL41)
            throw new IllegalStateException("PILOT_REQUIRES_KNOWN_MAC_OPENGL_4_1_MODE");
        if (org.lwjgl.glfw.GLFW.glfwGetWindowMonitor(window.getWindow()) == 0 || !client.isWindowActive() || !window.isFullscreen() || client.getMainRenderTarget().width != 1920
                || client.getMainRenderTarget().height != 1200 || window.getWidth() != 1920 || window.getHeight() != 1200)
            throw new IllegalStateException("PILOT_REQUIRES_FOCUSED_NATIVE_1920_1200");
        int[] viewport = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int activeBefore = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), bindingBefore = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int programBefore = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), fboBefore = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int expectedWidth = producer ? 960 : 1920, expectedHeight = producer ? 600 : 1200;
        var result = new JsonObject(); result.addProperty("gl_version", glVersion); result.addProperty("gl_renderer", GL11.glGetString(GL11.GL_RENDERER)); result.add("actual_viewport", new com.google.gson.Gson().toJsonTree(viewport));
        result.addProperty("draw_framebuffer", fboBefore);
        result.addProperty("current_program_at_query", programBefore);
        result.addProperty("active_unit_at_query", activeBefore);
        result.addProperty("texture_binding_at_query", bindingBefore);
        attemptedDraw.add("work_area_query_diagnostic", result);
        if (!Arrays.equals(viewport, new int[]{0,0,expectedWidth,expectedHeight}))
            throw new IllegalStateException("PILOT_ACTUAL_VIEWPORT_MISMATCH_" + selected.name + "_" + Arrays.toString(viewport));
        result.add("iris_draw_buffers", new com.google.gson.Gson().toJsonTree(selected.drawBuffers));
        result.addProperty("role", producer ? "quarter-area-effect-producer" : "full-native-reconstruction-consumer");
        var resources = new JsonArray();
        if (producer) {
            if (!Arrays.equals(selected.drawBuffers, targets)) throw new IllegalStateException("PILOT_PRODUCER_DRAW_BUFFER_MISMATCH");
            for (int i = 0; i < targets.length; i++) {
                int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + i);
                int type = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
                int texture = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                int level = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment, GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL);
                if (type != GL11.GL_TEXTURE || texture <= 0 || level != 0) throw new IllegalStateException("PILOT_ATTACHMENT_NOT_LEVEL_ZERO_TEXTURE");
                var resource = textureSize(texture); resource.addProperty("attachment", attachment); resource.addProperty("object_type", type);
                resource.addProperty("level", level); resource.addProperty("source_colortex", targets[i]);
                if (resource.get("width").getAsInt() != (bsl ? 1920 : 960) || resource.get("height").getAsInt() != (bsl ? 1200 : 600)
                        || resource.get("internal_format").getAsInt() != formats[i]) throw new IllegalStateException("PILOT_PRODUCER_PHYSICAL_TEXTURE_MISMATCH");
                s.producerTextures.put(targets[i], texture); resources.add(resource);
            }
        } else {
            if (s.producerTextures.size() != targets.length) throw new IllegalStateException("PILOT_CONSUMER_WITHOUT_PRODUCER_RECEIPT");
            for (int i = 0; i < targets.length; i++) {
                String name = "colortex" + targets[i]; var shape = inventory.get(name);
                if (shape == null || shape.type() != GL20.GL_SAMPLER_2D || shape.size() != 1) throw new IllegalStateException("PILOT_CONSUMER_NOT_SCALAR_SAMPLER2D_" + name);
                int location = GL20.glGetUniformLocation(program, name);
                if (location < 0) throw new IllegalStateException("PILOT_CONSUMER_SAMPLER_INACTIVE_" + name);
                int unit = GL20.glGetUniformi(program, location), active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                if (unit < 0 || unit >= GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)) throw new IllegalStateException("PILOT_SAMPLER_UNIT_OUTSIDE_RANGE");
                try {
                    GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                    if (texture != s.producerTextures.get(targets[i])) throw new IllegalStateException("PILOT_PRODUCER_CONSUMER_TEXTURE_ID_MISMATCH");
                    var resource = textureSize(texture); resource.addProperty("sampler", name); resource.addProperty("sampler_location", location);
                    resource.addProperty("sampler_unit", unit); resource.addProperty("producer_attachment_identity_matched", true);
                    if (resource.get("width").getAsInt() != (bsl ? 1920 : 960) || resource.get("height").getAsInt() != (bsl ? 1200 : 600)
                            || resource.get("internal_format").getAsInt() != formats[i]) throw new IllegalStateException("PILOT_CONSUMER_PHYSICAL_TEXTURE_MISMATCH");
                    resources.add(resource);
                } finally { GL13.glActiveTexture(active); }
            }
        }
        int[] viewportAfter = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewportAfter);
        int activeAfter = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), bindingAfter = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int programAfter = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), fboAfter = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (activeBefore != activeAfter || bindingBefore != bindingAfter || programBefore != programAfter || fboBefore != fboAfter
                || !Arrays.equals(viewport, viewportAfter)) throw new IllegalStateException("PILOT_OBSERVER_CHANGED_RENDER_STATE");
        result.addProperty("active_unit_before", activeBefore); result.addProperty("active_unit_after", activeAfter);
        result.addProperty("texture_binding_before", bindingBefore); result.addProperty("texture_binding_after", bindingAfter);
        result.addProperty("current_program_before", programBefore); result.addProperty("current_program_after", programAfter);
        result.addProperty("draw_framebuffer_before", fboBefore); result.addProperty("draw_framebuffer_after", fboAfter);
        result.add("viewport_after", new com.google.gson.Gson().toJsonTree(viewportAfter));
        result.addProperty("all_query_state_restored", true);
        result.add("resources", resources); result.addProperty("query_policy", "Untimed getters with exact texture/active-unit restoration; no shader/resource/settings edits");
        return result;
    }
    private record Compiled(Object renderer, WorldRenderingPipeline pipeline, Program program, String name,
                            int[] drawBuffers, JsonObject raw) {}
    private static final class State {
        final String[] required;
        final boolean samplerOnly, compileBoundary, workArea;
        final Map<Integer, Integer> producerTextures = new LinkedHashMap<>();
        final String archiveSha256;
        final Map<String, String> memberPins;
        final String expectedPack, adapter;
        final Set<String> consumers;
        final long settleNanos, deadlineNanos;
        final Path output;
        final JsonObject report = new JsonObject();
        final JsonArray captures = new JsonArray();
        final JsonArray superseded = new JsonArray();
        final IdentityHashMap<Program, Compiled> programs = new IdentityHashMap<>();
        final Map<String, Compiled> captured = new LinkedHashMap<>();
        Map<Program, Compiled> context;
        Object pipeline, pack, world;
        long appliedAt, stableAt;
        int applyGeneration, compilationSequence;
        boolean complete;
        State(Path request) throws Exception {
            if (Files.size(request) > 8192) throw new IllegalArgumentException("Request exceeds 8192 bytes");
            var json = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
            expectedPack = json.get("expectedPack").getAsString(); adapter = json.get("adapter").getAsString();
            samplerOnly = adapter.equals("photon-atmosphere-lut");
            workArea = adapter.equals("bsl-half-ao-pilot") || adapter.equals("complementary-half-vl-pilot");
            compileBoundary = (samplerOnly || workArea) && json.has("settlementBoundary")
                    && json.get("settlementBoundary").getAsString().equals("compiled-selected-pipeline");
            if (json.has("settlementBoundary") && !compileBoundary)
                throw new IllegalArgumentException("Unrecognized sampler settlement boundary");
            if (workArea && !compileBoundary) throw new IllegalArgumentException("Pilot requires actual selected compilation boundary");
            required = (samplerOnly || workArea) ? new String[0] : new String[]{"shaderlodBridgeVersion", "shaderlodEnabled", "shaderlodPreset"};
            archiveSha256 = (samplerOnly || workArea) ? json.get("expectedArchiveSha256").getAsString() : "";
            var pins = samplerOnly ? new java.util.HashMap<>(Map.of(
                    "shaders/program/d4_deferred_shading.fsh", "212081e707c20c78e17cbfd3380b2f22eb696cc1aa14bdf006c07d59f53f67e0",
                    "shaders/include/sky/atmosphere.glsl", "d6033728e8f6301d9b68b5a294ae06dcfc3ae5315208ab3e25fbbe826714ba79",
                    "shaders/shaders.properties", "f8cc8df60f930612e8817e4176a34886c1a9de778c15427a5468ecbc4f71ee9a")) : new java.util.HashMap<String, String>();
            if (compileBoundary && samplerOnly) {
                pins.put("shaders/program/d4_deferred_shading.fsh", "585309bef18670fe45a8f577905dda1e8f0d836a41d1c5a5e71d538b6ee69249");
                pins.put("shaders/include/shaderlod/sky_owned_atmosphere.glsl", "e42edbe39dade0ac34a2f4a16923e90d4ea15d3440340f0919a8fa102d937374");
            }
            if (workArea) pins = new java.util.HashMap<>(pilotPins(adapter));
            memberPins = Map.copyOf(pins);
            if (workArea && (!expectedPack.equals(pilotArchiveName(adapter))
                    || !archiveSha256.equals(pilotArchiveSha(adapter))
                    || !new com.google.gson.Gson().toJsonTree(memberPins).equals(json.get("memberSha256"))))
                throw new IllegalArgumentException("Unrecognized pinned effect-rate pilot");
            if (samplerOnly && (!expectedPack.equals(compileBoundary ? "Photon-SkyOwnedAtmosphere.zip" : "ShaderLOD-photon-893704058bb7a4fc.zip")
                    || !archiveSha256.equals(compileBoundary ? "9fb44e779fa261b74ba8e2d0f18945ea2feb4b6a2dd120727b962e87db47889b" : "970d670116e28202ae6c1067cb086294995f02ad02a3b58ef2664d208590b57f")
                    || !new com.google.gson.Gson().toJsonTree(memberPins).equals(json.get("memberSha256"))))
                throw new IllegalArgumentException("Unrecognized pinned atmosphere baseline");
            if (expectedPack.isBlank() || !Path.of(expectedPack).getFileName().toString().equals(expectedPack))
                throw new IllegalArgumentException("Expected selected pack must be a basename");
            Set<String> allowed = switch (adapter) {
                case "photon-live-pixel-budget" -> Set.of("deferred2", "composite4");
                case "photon-atmosphere-lut" -> Set.of("deferred4");
                case "bsl-half-ao-pilot" -> Set.of("deferred", "deferred1");
                case "complementary-half-vl-pilot" -> Set.of("composite1", "composite2");
                case "bliss-shader" -> Set.of("deferred", "composite", "composite2");
                default -> throw new IllegalArgumentException("Unsupported explicit adapter");
            };
            var names = new java.util.HashSet<String>();
            for (var name : json.getAsJsonArray("consumers")) if (!names.add(name.getAsString()))
                throw new IllegalArgumentException("Duplicate consumer");
            consumers = Set.copyOf(names);
            if (workArea && !consumers.equals(allowed)) throw new IllegalArgumentException("Pilot requires producer and reconstruction consumer");
            if (consumers.isEmpty() || !allowed.containsAll(consumers)) throw new IllegalArgumentException("Consumer outside pinned allowlist");
            int settle = json.get("settleMillis").getAsInt(), deadline = json.get("deadlineMillis").getAsInt();
            if (settle < (samplerOnly || workArea ? 3000 : 1000) || deadline <= settle || deadline > 120000) throw new IllegalArgumentException("Invalid bounded settlement interval");
            settleNanos = settle * 1000000L; deadlineNanos = deadline * 1000000L;
            output = Path.of(json.get("output").getAsString()).toAbsolutePath();
            report.addProperty("schema", 1); report.add("request", json); report.add("consumers", captures);
            report.add("superseded_apply_observations", superseded);
            report.addProperty("hook", "Iris 1.8.12+1.21.1 NeoForge quad HEAD after Program.use/CustomUniforms.push");
            report.addProperty("scope", workArea ? "Pinned actual producer draw viewport/FBO plus reconstruction sampler texture join; not fragment invocations, quality or performance" : samplerOnly ? "Actual bound sampler state in the pinned source-joined deferred4 draw; no typed uniform delivery, execution counts, quality or performance proof" : "Typed linked uniform observation in named source-joined consumers; not executed loop counts, GPU work, quality or performance");
            report.addProperty("settlement", compileBoundary ? "Actual selected named createProgram completion plus unchanged selected pack object/pipeline/world and ready world for settleMillis; no explicit Apply or temporal convergence" : "Explicit final afterApply receipt plus unchanged selected pack object/pipeline/world and world readiness for settleMillis; not temporal convergence");
            report.addProperty("gl_errors", "No glGetError calls; driver error state remains unconsumed");
            write(compileBoundary ? (workArea ? "waiting_for_selected_named_compilation" : "waiting_for_selected_deferred4_compilation") : "waiting_for_explicit_final_apply");
        }
        void requireArchive() throws Exception {
            Path archive = Iris.getShaderpacksDirectory().resolve(expectedPack);
            if (!archiveSha256.equals(WorkloadInputs.hash(archive))) throw new IllegalStateException("ATMOSPHERE_ARCHIVE_CHANGED");
            try (var zip = new java.util.zip.ZipFile(archive.toFile())) {
                for (var pin : memberPins.entrySet()) {
                    var entry = zip.getEntry(pin.getKey());
                    if (entry == null) throw new IllegalStateException("ATMOSPHERE_MEMBER_MISSING");
                    String actual;
                    try (var stream = zip.getInputStream(entry)) {
                        actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                    }
                    if (!pin.getValue().equals(actual)) throw new IllegalStateException("ATMOSPHERE_MEMBER_CHANGED");
                }
            }
            var pins = new JsonObject(); pins.addProperty("archive_sha256", archiveSha256);
            pins.add("member_sha256", new com.google.gson.Gson().toJsonTree(memberPins));
            report.add("pinned_archive", pins);
        }
        boolean allowed(CompositePass group, String name) {
            return consumers.contains(name) && (name.startsWith("deferred") ? group == CompositePass.DEFERRED : group == CompositePass.COMPOSITE);
        }
        void write(String status) throws Exception {
            report.addProperty("status", status); Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
        }
    }
}
