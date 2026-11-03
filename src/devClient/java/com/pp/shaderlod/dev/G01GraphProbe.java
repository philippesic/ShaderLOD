package com.pp.shaderlod.dev;

import com.google.gson.*;
import com.google.common.collect.ImmutableSet;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.*;

/** Neutral, opt-in constructor/execution observation. No production-mod linkage or state writes. */
public final class G01GraphProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Object,JsonObject> CONSTRUCTORS = new WeakHashMap<>();
    private static final Map<Integer,JsonObject> PROGRAMS = new HashMap<>();
    private static long construction;
    private static Object owner, pipeline;
    private static int budget, completed, copyIndex;
    private static boolean active, inFinal;
    private static JsonArray frames = new JsonArray();
    private static JsonObject frame;
    private static String failure = "";
    private G01GraphProbe() {}
    public static boolean active() { return active; }
    public static void compiled(ProgramSource source, Program program, Map<?,String> transformed,
                                ImmutableSet<Integer> flipped, ImmutableSet<Integer> once) {
        if (program == null) return;
        var row = new JsonObject(); row.addProperty("name", source.getName()); row.addProperty("program", program.getProgramId());
        source.getVertexSource().ifPresent(s -> row.addProperty("preprocessed_vertex", s));
        source.getFragmentSource().ifPresent(s -> row.addProperty("preprocessed_fragment", s));
        for (var entry : transformed.entrySet()) if (entry.getValue() != null)
            row.addProperty("transformed_" + entry.getKey().toString().toLowerCase(Locale.ROOT), entry.getValue());
        row.add("flipped_input", JSON.toJsonTree(flipped)); row.add("flipped_once_input", JSON.toJsonTree(once));
        var d = source.getDirectives(); row.add("draw_buffers", JSON.toJsonTree(d.getDrawBuffers()));
        row.add("mips", JSON.toJsonTree(d.getMipmappedBuffers())); row.add("explicit_flips", JSON.toJsonTree(d.getExplicitFlips()));
        row.addProperty("join", "Actual createProgram arguments + TransformPatcher result + same callback returned Program");
        PROGRAMS.put(program.getProgramId(), row);
    }
    public static void constructed(Object finalOwner, WorldRenderingPipeline p, ProgramSet set, RenderTargets targets,
                                   ImmutableSet<Integer> flipped, ImmutableSet<Integer> once) {
        var row = new JsonObject(); row.addProperty("sequence", ++construction); row.addProperty("at_ns", System.nanoTime());
        row.addProperty("pipeline_identity", System.identityHashCode(p)); row.addProperty("program_set_identity", System.identityHashCode(set));
        row.addProperty("targets_identity", System.identityHashCode(targets)); row.add("flipped_input_order", JSON.toJsonTree(flipped));
        row.add("flipped_once_input", JSON.toJsonTree(once)); var rt = set.getPackDirectives().getRenderTargetDirectives();
        row.add("clear_buffers", JSON.toJsonTree(rt.getBuffersToBeCleared())); var directives = new JsonObject();
        for (var e : rt.getRenderTargetSettings().entrySet()) {
            var value = new JsonObject(); value.addProperty("clear", e.getValue().shouldClear());
            value.addProperty("format", e.getValue().getInternalFormat().toString()); value.addProperty("clear_color", e.getValue().getClearColor().toString());
            directives.add(String.valueOf(e.getKey()), value);
        }
        row.add("target_directives", directives);
        try { row.add("constructed_swap_passes", swaps(finalOwner)); }
        catch (Exception e) { row.addProperty("error", e.toString()); }
        row.addProperty("join", "Exact FinalPassRenderer constructor RETURN; args7/13 flip sets and same ProgramSet clear/persistence inputs, same owner swapPasses field");
        CONSTRUCTORS.put(finalOwner, row);
    }
    public static JsonObject request(JsonObject request) throws Exception {
        if (!Boolean.getBoolean("shaderlod.dev.g01ExposureProbe")) throw new IllegalStateException("Explicit untimed graph launch required");
        if (request.has("cancel") && request.get("cancel").getAsBoolean()) { active = false; inFinal = false; }
        var p = Iris.getPipelineManager().getPipelineNullable(); if (p == null || Iris.isFallback()) throw new IllegalStateException("Healthy loaded pipeline required");
        var finalOwner = field(p, "finalPassRenderer");
        if (request.has("frames")) {
            if (active) throw new IllegalStateException("Already armed"); budget = request.get("frames").getAsInt();
            if (budget < 1 || budget > 3) throw new IllegalArgumentException("Bounded1..3 final frames");
            owner = finalOwner; pipeline = p; completed = 0; copyIndex = 0; active = true; inFinal = false; frames = new JsonArray(); failure = "";
        }
        var result = new JsonObject(); result.addProperty("active", active); result.addProperty("completed_final_passes", completed);
        result.addProperty("failure", failure); result.add("frames", frames.deepCopy());
        if (!active || request.has("include_graph") && request.get("include_graph").getAsBoolean()) {
            result.add("constructor", CONSTRUCTORS.containsKey(finalOwner) ? CONSTRUCTORS.get(finalOwner).deepCopy() : JsonNull.INSTANCE);
            result.add("current_swap_passes", swaps(finalOwner)); result.add("targets", targets((RenderTargets)field(p, "renderTargets")));
            var renderers = new JsonArray();
            for (String name : List.of("beginRenderer", "shadowCompositeRenderer", "prepareRenderer", "deferredRenderer", "compositeRenderer", "finalPassRenderer")) {
                var row = new JsonObject(); row.addProperty("field", name);
                try {
                    var renderer = field(p, name); if (renderer == null) { row.addProperty("absent", true); renderers.add(row); continue; }
                    var passes = new ArrayList<Object>();
                    if (name.equals("finalPassRenderer")) { var pass = field(renderer, "finalPass"); if (pass != null) passes.add(pass); }
                    else passes.addAll((List<?>)field(renderer, "passes"));
                    var pp = new JsonArray();
                    for (var pass : passes) {
                        var item = new JsonObject(); var program = (Program)field(pass, "program"); item.addProperty("pass_identity", System.identityHashCode(pass));
                        if (program != null) { item.addProperty("program", program.getProgramId()); item.add("consumed_source_join", PROGRAMS.containsKey(program.getProgramId()) ? PROGRAMS.get(program.getProgramId()).deepCopy() : JsonNull.INSTANCE); }
                        for (String f : List.of("stageReadsFromAlt", "mipmappedBuffers", "drawBuffers")) {
                            try { item.add(f, JSON.toJsonTree(field(pass, f))); } catch (NoSuchFieldException absent) { item.addProperty(f + "_unavailable", true); }
                        }
                        pp.add(item);
                    }
                    row.add("passes", pp);
                } catch (NoSuchFieldException absent) { row.addProperty("field_absent", true); }
                renderers.add(row);
            }
            result.add("consumed_renderers", renderers);
        }
        result.addProperty("scope", "Neutral constructor inputs + actual successful copy calls; no content readback, no timing, no installation tax inference");
        if (request.has("include_bank_aliases") && request.get("include_bank_aliases").getAsBoolean()) result.add("bank_aliases", G01BankAliasProbe.capture());
        return result;
    }
    public static void begin(Object finalOwner) {
        if (!active) return;
        if (owner != finalOwner || pipeline != Iris.getPipelineManager().getPipelineNullable()) { fail("Owner/pipeline changed"); return; }
        inFinal = true; copyIndex = 0; frame = new JsonObject(); frame.addProperty("begin_ns", System.nanoTime()); frame.add("copies", new JsonArray());
    }
    public static void copied(Object finalOwner, int target, int level, int xo, int yo, int x, int y, int width, int height) {
        if (!active || !inFinal || owner != finalOwner) return;
        try {
            var passes = (List<?>)field(finalOwner, "swapPasses"); if (copyIndex >= passes.size()) throw new IllegalStateException("Copy count exceeds constructed list");
            var swap = passes.get(copyIndex++); var row = swap(swap); row.addProperty("returned_ns", System.nanoTime());
            int destination = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int source = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            row.addProperty("executed_destination_texture", destination); row.addProperty("executed_source_texture", source); row.addProperty("executed_read_fbo", readFbo);
            row.add("copy_call", JSON.toJsonTree(new int[]{target,level,xo,yo,x,y,width,height}));
            row.addProperty("join_matches", destination == (int)field(swap,"targetTexture") && source == ((GlFramebuffer)field(swap,"from")).getColorAttachment(0)
                && readFbo == ((GlFramebuffer)field(swap,"from")).getId() && width == (int)field(swap,"width") && height == (int)field(swap,"height"));
            frame.getAsJsonArray("copies").add(row);
        } catch (Exception e) { fail(e.toString()); }
    }
    public static void end(Object finalOwner) {
        if (!active || !inFinal || owner != finalOwner) return;
        try { frame.addProperty("list_complete", copyIndex == ((List<?>)field(finalOwner,"swapPasses")).size()); }
        catch (Exception e) { fail(e.toString()); return; }
        frame.addProperty("end_ns", System.nanoTime()); frames.add(frame); completed++; inFinal = false; if (completed == budget) active = false;
    }
    private static void fail(String why) { failure = why; active = false; inFinal = false; }
    private static JsonArray swaps(Object finalOwner) throws Exception {
        var rows = new JsonArray(); for (var swap : (List<?>)field(finalOwner,"swapPasses")) rows.add(swap(swap)); return rows;
    }
    private static JsonObject swap(Object swap) throws Exception {
        var row = new JsonObject(); for (String name : List.of("target","width","height","targetTexture")) row.addProperty(name,(int)field(swap,name));
        var from = (GlFramebuffer)field(swap,"from"); row.addProperty("from_fbo", from.getId()); row.addProperty("from_texture", from.getColorAttachment(0)); return row;
    }
    private static JsonArray targets(RenderTargets targets) {
        var rows = new JsonArray(); for (int index=0;index<targets.getRenderTargetCount();index++) { var t=targets.get(index); if(t==null)continue;
            var row=new JsonObject();row.addProperty("index",index);row.addProperty("main",t.getMainTexture());row.addProperty("alt",t.getAltTexture());row.addProperty("width",t.getWidth());row.addProperty("height",t.getHeight());row.addProperty("format",t.getInternalFormat().toString());rows.add(row); }
        return rows;
    }
    private static Object field(Object owner,String name)throws Exception { for(Class<?> c=owner.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(NoSuchFieldException absent){} throw new NoSuchFieldException(name); }
}
