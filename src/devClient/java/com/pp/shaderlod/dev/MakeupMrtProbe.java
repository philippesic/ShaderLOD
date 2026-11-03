package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.pp.shaderlod.dev.mixin.SpatialPassAccessor;
import java.lang.reflect.Array;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.*;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.*;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

/** NEVER-TIME: one native100 MakeUp frame, no replay, allocation of targets, or shader changes. */
public final class MakeupMrtProbe {
    private static final String OUT = System.getProperty("shaderlod.dev.makeupMrtProbe", "");
    private static final String NATIVE = "0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8";
    private static final String CANDIDATE = "931b50d55785fd1d0b6acd72840b740fa201305c21fe0e639958955d2b96e7f5";
    private static final String OPTIONS = "d9a7a3f046896b8df518efb4c46bc25cda6d8d696b4393b395367406155e897b";
    private static final List<String> ORDER = List.of("prepare", "deferred", "composite", "composite1", "composite2", "final");
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Gson CANON = new GsonBuilder().disableHtmlEscaping().create();
    private record Code(Object owner, WorldRenderingPipeline pipeline, Object pack, Program program,
                        String name, int[] outputs, Set<Integer> mips, JsonObject source) {}
    private record Pass(Code code, Object pass, Set<Integer> alt, GlFramebuffer fbo) {}
    private static final Map<Program,Code> CODES = new IdentityHashMap<>();
    private static final Map<Map<?,String>,JsonObject> SOURCES = new IdentityHashMap<>();
    private static final JsonObject REPORT = new JsonObject();
    private static final JsonArray EVENTS = new JsonArray();
    private static final List<Pass> selected = new ArrayList<>();
    private static final Map<Integer,Integer> writes = new HashMap<>();
    private static Path directory; private static String packName; private static Object pack;
    private static WorldRenderingPipeline pipeline; private static RenderTargets targets;
    private static Pass pending; private static JsonObject state; private static JsonArray swaps;
    private static int frame, next, drawReturns, swapCalls, groupEnd, sourceBytes;
    private static final Set<Integer> mipReturns = new HashSet<>();
    private static final Map<Integer,RenderTarget> targetObjects = new HashMap<>();
    private static final Map<Integer,List<Integer>> targetIds = new HashMap<>();
    private static boolean freshOutput;
    private static long context, thread; private static boolean pinned, candidate, active, done;
    private MakeupMrtProbe() {}
    public static boolean enabled() { return !OUT.isBlank() && !done; }

    /** Called immediately after TransformPatcher returns, before ProgramBuilder links. */
    public static void transformed(Map<?,String> stages) {
        if (!enabled()) return;
        try {
            pins(); require(SOURCES.size() < 24, "SOURCE_RECEIPT_LIMIT");
            JsonObject receipt = new JsonObject();
            for (var e : stages.entrySet()) if (e.getValue() != null) {
                String key = e.getKey().toString(); require(Set.of("VERTEX", "FRAGMENT").contains(key), "EXTRA_STAGE");
                String text = e.getValue(); sourceBytes += text.getBytes(StandardCharsets.UTF_8).length;
                require(sourceBytes <= 6 * 1024 * 1024, "SOURCE_BYTES_LIMIT");
                receipt.addProperty("transformed_" + key.toLowerCase(Locale.ROOT), text);
                receipt.addProperty("transformed_" + key.toLowerCase(Locale.ROOT) + "_sha256", hash(text.getBytes(StandardCharsets.UTF_8)));
            }
            require(receipt.size() == 4, "VERTEX_FRAGMENT_REQUIRED"); SOURCES.put(stages, receipt);
        } catch (Exception e) { fail(e); }
    }
    public static void compiled(Object owner, WorldRenderingPipeline p, CompositePass group,
            ProgramSource s, Program program, Map<?,String> stages) {
        if (!enabled() || group == CompositePass.BEGIN) return; code(owner, p, s, program, stages);
    }
    public static void finalCompiled(Object owner, WorldRenderingPipeline p, ProgramSource s, Program program, Map<?,String> stages) {
        if (enabled()) code(owner, p, s, program, stages);
    }
    private static void code(Object owner, WorldRenderingPipeline p, ProgramSource s, Program program, Map<?,String> stages) {
        try {
            require(!active && s != null && program != null && ORDER.contains(s.getName()), "UNEXPECTED_PROGRAM_OR_RELOAD");
            JsonObject source = SOURCES.remove(stages); require(source != null, "BEFORE_LINK_RECEIPT_MISSING");
            require(s.getGeometrySource().isEmpty(), "GEOMETRY_STAGE");
            for (var e : Map.of("preprocessed_vertex", s.getVertexSource().orElseThrow(),
                    "preprocessed_fragment", s.getFragmentSource().orElseThrow()).entrySet()) {
                sourceBytes += e.getValue().getBytes(StandardCharsets.UTF_8).length;
                require(sourceBytes <= 6 * 1024 * 1024, "SOURCE_BYTES_LIMIT"); source.addProperty(e.getKey(), e.getValue());
                source.addProperty(e.getKey() + "_sha256", hash(e.getValue().getBytes(StandardCharsets.UTF_8)));
            }
            require(!CODES.containsKey(program) && CODES.size() < 24, "PROGRAM_LIMIT_OR_REUSE");
            if (s.getName().equals("prepare")) require(!s.getFragmentSource().orElseThrow().matches("(?s).*\\bdiscard\\b.*"), "PREPARE_DISCARD");
            if (s.getName().equals("composite2")) { String f = s.getFragmentSource().orElseThrow();
                require(f.matches("(?s).*gl_FragData\\s*\\[0]\\s*=\\s*blockColor\\s*;.*"), "C2_SHARED_EXPRESSION");
                require(f.matches("(?s).*gl_FragData\\s*\\[1]\\s*=\\s*blockColor\\s*;.*") == !candidate, "C2_DUPLICATE_EXPRESSION");
            }
            CODES.put(program, new Code(owner, p, s.getParent().getPack(), program, s.getName(),
                s.getDirectives().getDrawBuffers().clone(), Set.copyOf(s.getDirectives().getMipmappedBuffers()), source));
            JsonArray inventory = REPORT.has("compiled_inventory") ? REPORT.getAsJsonArray("compiled_inventory") : new JsonArray();
            JsonObject receipt = new JsonObject(); receipt.addProperty("name",s.getName()); receipt.addProperty("program",program.getProgramId()); receipt.addProperty("program_object_identity",System.identityHashCode(program)); receipt.add("source",source); inventory.add(receipt); REPORT.add("compiled_inventory",inventory);
        } catch (Exception e) { fail(e); }
    }
    private static void pins() throws Exception {
        if (pinned) return;
        directory = Path.of(OUT); require(directory.isAbsolute() && !Files.exists(directory, LinkOption.NOFOLLOW_LINKS), "FRESH_ABSOLUTE_DIRECTORY_REQUIRED"); freshOutput = true;
        packName = Iris.getCurrentPackName(); Path archive = Iris.getShaderpacksDirectory().resolve(packName);
        require(Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(archive), "REGULAR_ARCHIVE_REQUIRED");
        String sha = LoadedArtifactEvidence.fileSha256(archive); require(Set.of(NATIVE, CANDIDATE).contains(sha), "ARCHIVE_PIN"); candidate = CANDIDATE.equals(sha);
        TreeMap<String,String> options = new TreeMap<>(); WorkloadInputs.effectiveOptions().entrySet().forEach(e -> options.put(e.getKey(), e.getValue().getAsString()));
        require(options.size() == 100 && OPTIONS.equals(hash(CANON.toJson(options).getBytes(StandardCharsets.UTF_8))), "NATIVE100_PIN");
        require("2".equals(options.get("AA_TYPE")) && "false".equals(options.get("DOF")) && "false".equals(options.get("DEBUG_MODE")), "NATIVE_PATH_PIN");
        for (String flag : List.of("gpuTiming", "gpuPassTiming", "cpuTiming", "cpuLayerTiming"))
            require("false".equals(System.getProperty("shaderlod.dev." + flag)), "NEVER_TIME_EXPLICIT_FALSE:" + flag);
        for (String flag : List.of("benchmark", "measureOnly", "measureWorkload", "pixelHistoryCapture", "pixelCoverage", "spatialLayoutProbe", "workTrace", "physicalResolution"))
            require(!Boolean.getBoolean("shaderlod.dev." + flag), "OTHER_OBSERVER:" + flag);
        for (String flag : List.of("complementaryCopyGraph", "compositeCopyWitness", "bslMipParity", "bslMipWitness", "rasterActivationProbe", "settledConsumerUniforms", "makeupCloudWitness", "blissChainWitness", "sildurReceiverWitness"))
            require(System.getProperty("shaderlod.dev." + flag, "").isBlank(), "OTHER_WITNESS:" + flag);
        TreeMap<String,String> versions = new TreeMap<>(); ModList.get().getMods().forEach(m -> versions.put(m.getModId(), m.getVersion().toString()));
        require("1.21.1".equals(versions.get("minecraft")) && "21.1.253".equals(versions.get("neoforge")), "LOADER_PIN");
        int iris = 0; for (var f : ModList.get().getModFiles()) if (f.getMods().stream().anyMatch(m -> m.getModId().equals("iris"))) {
            iris++; require("3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83".equals(LoadedArtifactEvidence.fileSha256(f.getFile().getFilePath())), "IRIS_PIN");
        }
        require(iris == 1, "IRIS_COUNT"); REPORT.addProperty("archive_sha256", sha); REPORT.addProperty("pack_name", packName);
        REPORT.add("effective_options", JSON.toJsonTree(options)); REPORT.add("loaded_versions", JSON.toJsonTree(versions)); pinned = true;
    }
    public static void enter(Object owner, WorldRenderingPipeline p, CompositePass group, List<?> passes, RenderTargets all) {
        if (!enabled() || group == CompositePass.BEGIN) return;
        var mc = Minecraft.getInstance();
        if (!active && (group != CompositePass.PREPARE || mc.level == null || mc.screen != null || mc.isPaused() || !mc.isWindowActive())) return;
        try {
            if (!active) {
                pins(); require(next == 0 && selected.isEmpty(), "ONE_ATTEMPT");
                Files.createDirectory(directory); active = true; pipeline = p; targets = all; pack = Iris.getCurrentPack().orElseThrow();
                for (int n : new int[]{1,3}) { RenderTarget rt = targets.get(n); require(rt != null,"RT1_RT3_REQUIRED"); targetObjects.put(n,rt); targetIds.put(n,List.of(rt.getMainTexture(),rt.getAltTexture())); }
                REPORT.add("target_ids",JSON.toJsonTree(targetIds));
                frame = SystemTimeUniforms.COUNTER.getAsInt(); context = GLFW.glfwGetCurrentContext(); thread = Thread.currentThread().threadId();
                REPORT.addProperty("frame", frame); REPORT.addProperty("context", context); REPORT.addProperty("thread", thread);
                REPORT.addProperty("pipeline_identity", System.identityHashCode(p)); REPORT.addProperty("targets_identity", System.identityHashCode(all));
            }
            validate(); require(p == pipeline && all == targets && pending == null && next == selected.size(), "GROUP_JOIN");
            List<String> names = switch (group) { case PREPARE -> List.of("prepare"); case DEFERRED -> List.of("deferred"); case COMPOSITE -> ORDER.subList(2,5); default -> List.of(); };
            require(passes.size() == names.size() && ORDER.get(next).equals(names.getFirst()), "GROUP_ORDER_OR_COUNT");
            for (int i = 0; i < passes.size(); i++) {
                require(passes.get(i) instanceof SpatialPassAccessor, "PASS_ACCESSOR"); var pass = (SpatialPassAccessor)passes.get(i);
                Code c = CODES.get(pass.shaderlod$program()); require(c != null && c.owner == owner && c.pipeline == p && c.pack == pack && names.get(i).equals(c.name), "SOURCE_PROGRAM_JOIN");
                require(c.name.equals(pass.shaderlod$name()) && Arrays.equals(c.outputs, pass.shaderlod$drawBuffers()) && c.mips.equals(pass.shaderlod$mipmappedBuffers()), "PASS_DIRECTIVES_JOIN");
                selected.add(new Pass(c, pass, Set.copyOf(pass.shaderlod$stageReadsFromAlt()), (GlFramebuffer)field(pass,"framebuffer")));
            }
            groupEnd = selected.size(); event("group_enter").addProperty("group", group.toString());
        } catch (Exception e) { fail(e); }
    }
    public static void generationReturned(RenderTarget target, boolean alt) {
        if (!active || done) return;
        try { validate(); require(pending == null && next < selected.size(),"MIP_ORDER"); Pass s = selected.get(next);
            int n = s.code.name.equals("composite") ? 1 : s.code.name.equals("composite1") ? 0 : -1;
            require(n >= 0 && targets.get(n) == target && s.alt.contains(n) == alt && s.code.mips.equals(Set.of(n)) && mipReturns.add(n),"ACTUAL_MIP_TARGET");
            int t = physical(n,alt); require(t == writes.get(n),"MIP_LATEST_WRITE"); JsonObject im = image(t,n);
            require(im.get("min").getAsInt() == GL11.GL_LINEAR_MIPMAP_LINEAR && im.get("level1_width").getAsInt() == 960,"MIP_STATE"); event("native_mip_return").add("image",im);
        } catch (Exception e) { fail(e); }
    }
    public static void leave() { if (active && !done) try { validate(); require(next == groupEnd && pending == null, "GROUP_INCOMPLETE"); event("group_return"); } catch (Exception e) { fail(e); } }
    public static void beforeQuad() {
        if (!active || done) return;
        try { validate(); require(pending == null && next < selected.size(), "UNEXPECTED_QUAD"); pending = selected.get(next);
            require(ORDER.get(next).equals(pending.code.name) && mipReturns.equals(pending.code.mips), "DRAW_ORDER_OR_MIP_RETURNS"); mipReturns.clear(); state = drawState(pending); drawReturns = 0;
            JsonObject e = event("quad_head"); e.add("state", state); e.addProperty("compiled_inventory_join", pending.code.name); e.addProperty("program_object_identity", System.identityHashCode(pending.code.program));
        } catch (Exception e) { fail(e); }
    }
    public static void nativeDrawReturned() { if (active && !done) try { validate(); require(pending != null && ++drawReturns == 1, "NATIVE_DRAW_COUNT"); event("native_draw_return"); } catch (Exception e) { fail(e); } }
    public static void afterQuad() {
        if (!active || done) return;
        try { validate(); require(pending != null && drawReturns == 1 && state.equals(drawState(pending)), "NATIVE_DRAW_STATE_JOIN");
            for (var v : state.getAsJsonArray("outputs")) { JsonObject o = v.getAsJsonObject(); int n = o.get("logical").getAsInt(); if (n >= 0) writes.put(n, o.get("texture").getAsInt()); }
            if (pending.code.name.equals("composite2") && !candidate) {
                byte[] a = packed(writes.get(1)), b = packed(writes.get(3));
                Files.write(directory.resolve("control-rt1-u10f11f11f.bin"), a, StandardOpenOption.CREATE_NEW);
                Files.write(directory.resolve("control-rt3-u10f11f11f.bin"), b, StandardOpenOption.CREATE_NEW);
                REPORT.addProperty("packed_rt1_sha256", hash(a)); REPORT.addProperty("packed_rt3_sha256", hash(b));
                REPORT.addProperty("packed_bytes_each", a.length); REPORT.addProperty("packed_equal", Arrays.equals(a,b)); require(Arrays.equals(a,b), "PACKED_OUTPUT_MISMATCH");
            }
            event("quad_return"); next++; pending = null; state = null;
        } catch (Exception e) { fail(e); }
    }
    public static void finalEnterReflected(Object owner, WorldRenderingPipeline p, RenderTargets all, List<?> nativeSwaps, GlFramebuffer colorHolder) {
        if (!active || done) return;
        try { validate(); require(next == 5 && selected.size() == 5 && p == pipeline && all == targets, "FINAL_CHAIN_JOIN");
            Object pass = field(owner,"finalPass"); Code c = CODES.get((Program)field(pass,"program"));
            require(c != null && c.owner == owner && c.pipeline == pipeline && c.pack == pack && c.name.equals("final"), "FINAL_SOURCE_JOIN");
            @SuppressWarnings("unchecked") Set<Integer> alt = (Set<Integer>)field(pass,"stageReadsFromAlt");
            require(c.mips.isEmpty() && c.mips.equals(field(pass,"mipmappedBuffers")), "FINAL_MIPS"); selected.add(new Pass(c,pass,Set.copyOf(alt),colorHolder));
            swaps = new JsonArray(); Set<Integer> membership = new HashSet<>();
            for (Object swap : nativeSwaps) { int n = (Integer)field(swap,"target"); require(membership.add(n), "DUPLICATE_SWAP");
                JsonObject e = new JsonObject(); e.addProperty("logical",n); e.addProperty("texture",(Integer)field(swap,"targetTexture")); e.addProperty("fbo",((GlFramebuffer)field(swap,"from")).getId()); swaps.add(e);
            }
            REPORT.add("swap_list",swaps); require(membership.contains(3) && membership.contains(1) == !candidate, "RT1_RT3_SWAP_MEMBERSHIP"); event("final_enter");
        } catch (Exception e) { fail(e); }
    }
    public static void swapCopy() {
        if (!active || done) return;
        try { validate(); require(next == 6 && pending == null && swapCalls < swaps.size(), "SWAP_ORDER"); JsonObject s = swaps.get(swapCalls).getAsJsonObject();
            int t = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), f = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            require(t == s.get("texture").getAsInt() && f == s.get("fbo").getAsInt(), "ACTUAL_SWAP_CALL_JOIN");
            int n = s.get("logical").getAsInt(); RenderTarget rt = targets.get(n); require(t == rt.getMainTexture(), "SWAP_DESTINATION_MAIN");
            int input = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            require(input == writes.getOrDefault(n,-1) && input != t, "SWAP_SOURCE_CURRENT_WRITE");
            event("swap_copy_call").add("swap",s); swapCalls++;
        } catch (Exception e) { fail(e); }
    }
    public static void fallbackCopy() { if (active && !done) fail(new IllegalStateException("FINAL_FALLBACK_COPY")); }
    public static void finalLeave() {
        if (!active || done) return;
        try { validate(); require(next == 6 && pending == null && swapCalls == swaps.size(), "FINAL_INCOMPLETE");
            clean("final_return"); event("final_return"); REPORT.addProperty("status",candidate ? "candidate_one_frame_graph_observed" : "control_one_frame_graph_and_packed_equality_observed"); done = true; active = false; finish();
        } catch (Exception e) { fail(e); }
    }
    private static JsonObject drawState(Pass s) throws Exception {
        clean("draw_before"); Object computes = field(s.pass,"computes"); for (int i = 0; i < Array.getLength(computes); i++) require(Array.get(computes,i) == null,"COMPUTE");
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == s.code.program.getProgramId() && GL20.glGetProgrami(s.code.program.getProgramId(),GL20.GL_LINK_STATUS) == GL11.GL_TRUE,"CURRENT_LINKED_PROGRAM");
        require(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == s.fbo.getId() && GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE,"DRAW_FBO");
        int[] vp = new int[4]; GL11.glGetIntegerv(GL11.GL_VIEWPORT,vp); require(Arrays.equals(vp,new int[]{0,0,1920,1200}),"FULL_VIEWPORT");
        require(!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) && !GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD),"FULL_COVERAGE_STATE");
        require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glIsEnabled(GL11.GL_STENCIL_TEST),"FULL_COVERAGE_DEPTH_STENCIL");
        JsonObject result = new JsonObject(); result.addProperty("pass",s.code.name); result.addProperty("program",s.code.program.getProgramId()); result.addProperty("draw_fbo",s.fbo.getId()); result.add("viewport",JSON.toJsonTree(vp));
        int[] expected = switch (s.code.name) { case "prepare" -> new int[]{1,7}; case "deferred" -> new int[]{1,4}; case "composite" -> new int[]{0,1,6}; case "composite1" -> new int[]{1}; case "composite2" -> candidate ? new int[]{3} : new int[]{1,3}; default -> new int[0]; };
        boolean last = s.code.name.equals("final"); require(last || Arrays.equals(s.code.outputs,expected),"OUTPUT_GRAPH");
        JsonArray outputs = new JsonArray(); int count = last ? 1 : expected.length;
        for (int i=0;i<count;i++) {
            int attachment = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i); require(attachment >= GL30.GL_COLOR_ATTACHMENT0,"OUTPUT_ATTACHMENT");
            require(GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE)==GL11.GL_TEXTURE && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL)==0 && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL32.GL_FRAMEBUFFER_ATTACHMENT_LAYERED)==0,"LEVEL0_TEXTURE_OUTPUT");
            int t = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER,attachment,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME), n = last ? -1 : expected[i];
            if(n>=0) { require(t==physical(n,!s.alt.contains(n)),"OUTPUT_FLIP_JOIN");
                if(n==1) require(t==physical(1,Set.of("prepare","composite","composite2").contains(s.code.name)),"RT1_NATIVE_LEDGER");
                if(n==3) require(t==physical(3,true),"RT3_CURRENT_ALT");
            } else require(t==Minecraft.getInstance().getMainRenderTarget().getColorTextureId(),"FINAL_SURFACE");
            JsonObject o=new JsonObject(); o.addProperty("logical",n); o.addProperty("texture",t); o.add("image",image(t,n)); outputs.add(o);
            ByteBuffer mask=ByteBuffer.allocateDirect(4); GL30.glGetBooleani_v(GL11.GL_COLOR_WRITEMASK,i,mask); require(mask.get(0)!=0&&mask.get(1)!=0&&mask.get(2)!=0&&mask.get(3)!=0&&!GL30.glIsEnabledi(GL11.GL_BLEND,i),"OUTPUT_BLEND_OR_MASK");
        }
        int max=GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS); require(max<=32,"DRAW_BUFFER_LIMIT"); for(int i=count;i<max;i++) require(GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i)==GL11.GL_NONE,"EXTRA_OUTPUT"); result.add("outputs",outputs);
        result.addProperty("dither",GL11.glIsEnabled(GL11.GL_DITHER)); result.addProperty("framebuffer_srgb",GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB));
        JsonObject samplers=new JsonObject(); for(int n:new int[]{1,3,6}) {
            String name="colortex"+n; int loc=GL20.glGetUniformLocation(s.code.program.getProgramId(),name); if(loc<0) continue;
            int unit=GL20.glGetUniformi(s.code.program.getProgramId(),loc), saved=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), t;
            require(unit>=0&&unit<GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS),"SAMPLER_UNIT");
            try { GL13.glActiveTexture(GL13.GL_TEXTURE0+unit); require(GL11.glGetInteger(GL33.GL_SAMPLER_BINDING)==0,"SAMPLER_OVERRIDE"); t=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); } finally { GL13.glActiveTexture(saved); }
            require(t==physical(n,s.alt.contains(n)),"SAMPLER_FLIP_JOIN"); if(n==1&&writes.containsKey(n)) require(t==writes.get(n),"RT1_LATEST_WRITE");
            if(s.code.name.equals("composite2")&&n==3) require(t==physical(3,false)&&t!=physical(3,!s.alt.contains(3)),"RT3_PREVIOUS_MAIN_NO_FEEDBACK");
            if(last&&n==(candidate?3:1)) require(t==writes.get(n),"FINAL_CURRENT_WRITE");
            samplers.add(name,image(t,n));
        }
        if(s.code.name.equals("composite2")) require(samplers.has("colortex3"),"C2_HISTORY_SAMPLER");
        if(last) { require(samplers.has(candidate?"colortex3":"colortex1")&&!samplers.has(candidate?"colortex1":"colortex3"),"FINAL_SAMPLER_IDENTITY"); JsonObject im=samplers.getAsJsonObject(candidate?"colortex3":"colortex1"); require(im.get("min").getAsInt()==GL11.GL_LINEAR&&im.get("mag").getAsInt()==GL11.GL_LINEAR,"FINAL_FILTER"); }
        result.add("samplers",samplers); clean("draw_after"); return result;
    }
    private static int physical(int n,boolean alt) { RenderTarget rt=targets.get(n); require(rt!=null&&rt.getMainTexture()!=rt.getAltTexture(),"TARGET_ALIAS"); return alt?rt.getAltTexture():rt.getMainTexture(); }
    private static JsonObject image(int t,int n) {
        int activeUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); JsonObject r=new JsonObject();
        try { GL11.glBindTexture(GL11.GL_TEXTURE_2D,t); r.addProperty("texture",t);
            for(var e:Map.of("width",GL11.GL_TEXTURE_WIDTH,"height",GL11.GL_TEXTURE_HEIGHT,"format",GL11.GL_TEXTURE_INTERNAL_FORMAT).entrySet()) r.addProperty(e.getKey(),GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,e.getValue()));
            require(r.get("width").getAsInt()==1920&&r.get("height").getAsInt()==1200,"IMAGE_EXTENT"); if(n==1||n==3) require(r.get("format").getAsInt()==GL30.GL_R11F_G11F_B10F,"PACKED_FORMAT");
            for(var e:Map.of("min",GL11.GL_TEXTURE_MIN_FILTER,"mag",GL11.GL_TEXTURE_MAG_FILTER,"base",GL12.GL_TEXTURE_BASE_LEVEL,"max",GL12.GL_TEXTURE_MAX_LEVEL,"wrap_s",GL11.GL_TEXTURE_WRAP_S,"wrap_t",GL11.GL_TEXTURE_WRAP_T).entrySet()) r.addProperty(e.getKey(),GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D,e.getValue()));
            require(r.get("base").getAsInt()==0&&r.get("max").getAsInt()==1000&&r.get("wrap_s").getAsInt()==GL12.GL_CLAMP_TO_EDGE&&r.get("wrap_t").getAsInt()==GL12.GL_CLAMP_TO_EDGE,"IMAGE_LEVEL_OR_WRAP");
            r.addProperty("level1_width",GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,1,GL11.GL_TEXTURE_WIDTH));
        } finally { GL13.glActiveTexture(activeUnit); GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding); require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==activeUnit&&GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==binding,"IMAGE_STATE_RESTORE"); }
        return r;
    }
    private static byte[] packed(int texture) {
        clean("readback_before"); int activeUnit=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),pbo=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int[] keys={GL11.GL_PACK_ALIGNMENT,GL11.GL_PACK_ROW_LENGTH,GL11.GL_PACK_SKIP_ROWS,GL11.GL_PACK_SKIP_PIXELS,GL12.GL_PACK_IMAGE_HEIGHT,GL12.GL_PACK_SKIP_IMAGES,GL11.GL_PACK_SWAP_BYTES,GL11.GL_PACK_LSB_FIRST};
        JsonObject e=event("packed_readback"); e.addProperty("texture",texture); e.addProperty("format",GL11.GL_RGB); e.addProperty("type",GL30.GL_UNSIGNED_INT_10F_11F_11F_REV); e.addProperty("level",0); e.addProperty("bytes",1920*1200*4); e.add("saved_bindings",JSON.toJsonTree(new int[]{activeUnit,binding,pbo}));
        int[] old=new int[keys.length]; for(int i=0;i<keys.length;i++) old[i]=GL11.glGetInteger(keys[i]); e.add("pack_keys",JSON.toJsonTree(keys)); e.add("saved_pack",JSON.toJsonTree(old)); ByteBuffer data=MemoryUtil.memAlloc(1920*1200*4);
        try { GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0); for(int k:keys) GL11.glPixelStorei(k,k==GL11.GL_PACK_ALIGNMENT?1:0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture); GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,GL30.GL_UNSIGNED_INT_10F_11F_11F_REV,data); clean("readback_read"); byte[] bytes=new byte[data.remaining()]; data.get(bytes); return bytes;
        } finally { MemoryUtil.memFree(data); for(int i=0;i<keys.length;i++) GL11.glPixelStorei(keys[i],old[i]); GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,pbo); GL13.glActiveTexture(activeUnit); GL11.glBindTexture(GL11.GL_TEXTURE_2D,binding);
            require(GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)==activeUnit&&GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)==binding&&GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING)==pbo,"READBACK_BINDINGS_RESTORE"); for(int i=0;i<keys.length;i++) require(GL11.glGetInteger(keys[i])==old[i],"PACK_STATE_RESTORE"); clean("readback_restored"); e.addProperty("restored_and_error_clean",true);
        }
    }
    private static void validate() { var mc=Minecraft.getInstance(); require(active&&mc.level!=null&&"minecraft:overworld".equals(mc.level.dimension().location().toString())&&mc.screen==null&&!mc.isPaused()&&mc.isWindowActive()&&!Iris.isFallback(),"WORLD_STATE");
        require(Iris.getCurrentPack().orElse(null)==pack&&packName.equals(Iris.getCurrentPackName())&&Iris.getPipelineManager().getPipelineNullable()==pipeline,"PACK_PIPELINE"); for (int n : new int[]{1,3}) { RenderTarget rt = targets.get(n); require(rt == targetObjects.get(n) && targetIds.get(n).equals(List.of(rt.getMainTexture(),rt.getAltTexture())),"TARGET_IDENTITY_CHANGED"); } require(context!=0&&GLFW.glfwGetCurrentContext()==context&&Thread.currentThread().threadId()==thread&&SystemTimeUniforms.COUNTER.getAsInt()==frame,"FRAME_CONTEXT");
        require(mc.getMainRenderTarget().width==1920&&mc.getMainRenderTarget().height==1200&&mc.getWindow().getWidth()==1920&&mc.getWindow().getHeight()==1200,"SURFACE_EXTENT"); }
    private static Object field(Object o,String name) throws Exception { for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass()) try { var f=c.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch(NoSuchFieldException ignored) {} throw new NoSuchFieldException(name); }
    private static void clean(String site) { int error=GL11.glGetError(); REPORT.addProperty("gl_error_"+site,error); require(error==GL11.GL_NO_ERROR,"GL_ERROR:"+site+":"+error); }
    private static JsonObject event(String kind) { require(EVENTS.size()<48,"EVENT_LIMIT"); JsonObject e=new JsonObject(); e.addProperty("kind",kind); e.addProperty("next",next); e.addProperty("frame",frame); EVENTS.add(e); return e; }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean yes,String reason) { if(!yes) throw new IllegalStateException(reason); }
    private static void fail(Exception e) { done=true; active=false; REPORT.addProperty("status","unknown_failed_one_attempt"); REPORT.addProperty("failure",e.toString()); finish(); }
    private static void finish() { try { require(freshOutput,"NO_FRESH_OUTPUT_RESERVATION"); if(!Files.exists(directory)) Files.createDirectory(directory); REPORT.add("events",EVENTS); REPORT.addProperty("never_time",true); REPORT.addProperty("scope","One frame; source receipts are observed, not independently pinned transformed sources. No history, quality, cost, mod linkage or compatibility acceptance."); String raw=JSON.toJson(REPORT); require(raw.getBytes(StandardCharsets.UTF_8).length<=16*1024*1024,"REPORT_BYTES_LIMIT"); Files.writeString(directory.resolve("probe.json"),raw,StandardOpenOption.CREATE_NEW); } catch(Exception e) { com.mojang.logging.LogUtils.getLogger().warn("MakeUp probe evidence write failed",e); } }
}
