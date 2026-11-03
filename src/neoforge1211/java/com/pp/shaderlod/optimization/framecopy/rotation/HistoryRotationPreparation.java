package com.pp.shaderlod.optimization.framecopy.rotation;

import com.pp.shaderlod.optimization.CompileOptionControl;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.framecopy.MakeUpDeadCopyAdmission;
import com.pp.shaderlod.optimization.mips.DeadMipAdmission;
import com.pp.shaderlod.packs.PackSelectionService;
import java.lang.reflect.Field;
import java.util.*;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import org.lwjgl.opengl.*;

/** One pinned preparation caller. All expensive inspection is explicit and untimed. */
public final class HistoryRotationPreparation {
    private HistoryRotationPreparation() {}
    private static final boolean STARTUP = Boolean.getBoolean(HistoryRotationRuntime.STARTUP_PROPERTY);
    private static Object pipeline, composite, finalOwner;
    private static RenderTargets targets;
    private static Program c2Program;
    private static net.irisshaders.iris.targets.RenderTarget cachedRt3;
    private static int cachedA, cachedB, depthVersion, depthTexture;
    private static Object depthFormat;
    private static HistoryRotationPlan.Epoch cached;
    private static HistoryRotationPlan.QuadBinding quad;
    private static long allocation;
    private static boolean requested, configured, observingDraw;
    private static int programId, framebuffersSeen;
    private static String status = "DEFAULT_OFF";
    private static List<String> missing = List.of("EXPLICIT_SHADOW_REQUEST");
    private static List<Attachment> attachments = List.of();
    private static final IdentityHashMap<Object, Link> LINKS = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Input> INPUTS = new IdentityHashMap<>();
    private static final Set<Object> PENDING_CLEAR = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> FULL_CLEAR = Collections.newSetFromMap(new IdentityHashMap<>());
    private record Input(ProgramSource source, String rawVertex, String rawFragment,
                         String vertex, String fragment, String geometry) {}
    private record Link(Input input, Program program) {}
    /** These remaining claims require independent transformed/source/live evidence, not handle matches. */
    public record Closure(long generation, long allocation, int program, boolean allLiveAliases,
                          boolean currentConsumedGeneratedSource, boolean transformedSemantics, boolean immutableQuadAndActualCall, boolean transformedLifecycleScope, String evidence) {}
    private static Closure closure;
    public record ConsumedSource(String rawVertex, String rawFragment, String compiledVertex, String compiledFragment) {}
    public record Attachment(String owner, int framebuffer, int slot, int texture, int liveTexture, boolean historyAlias) {}
    public record Report(String status, long generation, long allocation, int program, int framebuffersSeen,
                         boolean sourceProgramJoin, HistoryRotationPlan.QuadBinding quad, ConsumedSource consumedSource, List<String> missingJoins,
                         List<Attachment> attachments, HistoryRotationRuntime.Snapshot runtime) {}

    /** Render thread only. Root's thin dev control can call this without constructing a Plan.Request. */
    public static Report request(String action) {
        if (!STARTUP) return snapshot(); // no render assertion, pins, context query or allocation
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        switch (action) {
            case "shadow", "prepare" -> {
                HistoryRotationRuntime.closeBeforeMutation("NEW_SHADOW_REQUEST");
                requested = true; status = "WAITING_FOR_POST_CLEAR_FRAME_BOUNDARY";
            }
            case "enable" -> {
                if (cached != null && context() && cached == currentEpoch()) HistoryRotationRuntime.enable();
                else status = "REFUSED_CURRENT_NATIVE_CONTEXT";
            }
            case "disable" -> { requested = false; HistoryRotationRuntime.disable(); status = "DISABLED"; }
            case "status" -> {}
            default -> throw new IllegalArgumentException("Expected shadow/prepare/enable/disable/status");
        }
        return snapshot();
    }
    /** Does not manufacture proof: bind an independently reviewed closure to this exact cached graph. */
    public static Report attest(Closure evidence) {
        if (!STARTUP) return snapshot();
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if (cached == null || evidence == null || evidence.generation != cached.generation()
                || evidence.allocation != cached.allocation() || c2Program == null
                || evidence.program != c2Program.getProgramId() || evidence.evidence == null || evidence.evidence.isBlank())
            throw new IllegalArgumentException("CLOSURE_MUST_NAME_CURRENT_OBSERVED_GRAPH_AND_EVIDENCE");
        closure = evidence; return snapshot();
    }
    /** Actual ProgramBuilder.begin arguments, after patchComposite; only the one c2 candidate is retained. */
    public static void compileInput(Object owner, ProgramSource source, String name, String vertex, String geometry, String fragment) {
        if (!STARTUP || source == null || !HistoryRotationPlan.HISTORY_PASS.equals(name) || !name.equals(source.getName())) return;
        if (INPUTS.size() >= 8 || LINKS.size() >= 8) { status = "REFUSED_SOURCE_OBSERVER_BOUND_RELOAD_REQUIRED"; return; }
        if (vertex == null || fragment == null || (long)vertex.length() + fragment.length() > 2 * 1024 * 1024) {
            INPUTS.remove(owner); LINKS.remove(owner); status = "REFUSED_SOURCE_BOUND"; return;
        }
        INPUTS.put(owner, new Input(source, source.getVertexSource().orElse(null), source.getFragmentSource().orElse(null), vertex, fragment, geometry));
    }
    public static void programReturned(Object owner, ProgramSource source, Program program) {
        if (!STARTUP || source == null || !HistoryRotationPlan.HISTORY_PASS.equals(source.getName())) return;
        Input input = INPUTS.remove(owner);
        if (input != null && input.source == source && program != null) LINKS.put(owner, new Link(input, program));
    }
    /** Native onFullClear only marks intention; callers mark completion after the enclosing clear loop returns. */
    public static void beforeFullClear(Object target) {
        if (!STARTUP) return;
        HistoryRotationRuntime.beforeTargetsDestroy(target);
        FULL_CLEAR.remove(target); PENDING_CLEAR.add(target);
        if (target == targets) invalidate("FULL_CLEAR");
    }
    public static void nativeClearCompleted(Object target) { if (STARTUP && PENDING_CLEAR.remove(target)) FULL_CLEAR.add(target); }
    public static void beforeFrame(Object actualPipeline) {
        if (!STARTUP) return;
        if (cached != null && (actualPipeline != pipeline || currentEpoch() == null)) close("FRAME_CONTEXT_CHANGED");
    }
    /** Exact beginLevelRendering seam: all native clear/resize/setup completed, before beginRenderer.renderAll. */
    public static void afterClearBeforeGraph(Object actualPipeline, RenderTargets actualTargets) {
        if (!STARTUP) return;
        // This is after the native clear loop; onFullClear has not been mistaken for completion.
        if (!actualTargets.isFullClearRequired()) nativeClearCompleted(actualTargets);
        if (requested) {
            requested = false;
            try { inspect(actualPipeline, actualTargets); }
            catch (Exception | LinkageError refused) { status = "REFUSED_PREPARATION:" + refused.getMessage(); missing = List.of(status); }
        }
        HistoryRotationRuntime.beginFrame(actualPipeline);
    }
    private static void inspect(Object actualPipeline, RenderTargets actualTargets) throws Exception {
        if (pipeline != actualPipeline || targets != actualTargets || cached == null) {
            HistoryRotationRuntime.closeBeforeMutation("PREPARE_NEW_GRAPH");
            pipeline = actualPipeline; targets = actualTargets; allocation++; closure = null; quad = null;
            composite = field(pipeline, "compositeRenderer"); finalOwner = field(pipeline, "finalPassRenderer");
        }
        if (field(pipeline, "pack") != Iris.getCurrentPack().orElse(null)) throw new IllegalStateException("PIPELINE_PACK_IDENTITY");
        if (!context()) throw new IllegalStateException("NATIVE100_AA2_OW_G1_PEERS_SOURCE_CONTEXT");
        var rt3 = targets.get(3);
        cached = new HistoryRotationPlan.Epoch(pipeline, Iris.getCurrentPack().orElse(null), Minecraft.getInstance().level,
            OptimizationConfig.generation(), allocation, rt3.getWidth(), rt3.getHeight(), true, true, true, true);
        cachedRt3 = rt3; cachedA = rt3.getMainTexture(); cachedB = rt3.getAltTexture();
        depthVersion = (int) field(targets, "cachedDepthBufferVersion"); depthTexture = (int) field(targets, "currentDepthTexture");
        depthFormat = field(targets, "currentDepthFormat");
        c2Program = null;
        for (Object pass : (List<?>) field(composite, "passes")) if (HistoryRotationPlan.HISTORY_PASS.equals(field(pass, "name"))) {
            if (c2Program != null) throw new IllegalStateException("NONUNIQUE_C2");
            c2Program = (Program) field(pass, "program");
        }
        programId = c2Program == null ? 0 : c2Program.getProgramId();
        if (c2Program == null) throw new IllegalStateException("NO_LIVE_C2_PROGRAM");
        var rows = new ArrayList<Attachment>();
        @SuppressWarnings("unchecked") var owned = (List<GlFramebuffer>) field(targets, "ownedFramebuffers");
        int a = rt3.getMainTexture(), b = rt3.getAltTexture();
        framebuffersSeen = owned.size();
        if (owned.size() > 256) throw new IllegalStateException("FBO_CENSUS_BOUND:"+owned.size());
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            for (GlFramebuffer fbo : owned) {
                @SuppressWarnings("unchecked") var map = (Map<Integer,Integer>) field(fbo, "attachments");
                if (map.size() > 32 || rows.size() + map.size() > 2048) throw new IllegalStateException("ATTACHMENT_CENSUS_BOUND");
                com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo.getId());
                for (var e : map.entrySet()) {
                    int live = GL30.glGetFramebufferAttachmentParameteri(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + e.getKey(), GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                    if (live != e.getValue()) throw new IllegalStateException("LIVE_FBO_METADATA_ALIAS_MISMATCH");
                    rows.add(new Attachment("RenderTargets.ownedFramebuffers", fbo.getId(), e.getKey(), e.getValue(), live, live == a || live == b));
                }
            }
        } finally { com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead); }
        attachments = List.copyOf(rows);
        var gaps = new ArrayList<String>();
        boolean linked = sourceJoin();
        if (!linked) gaps.add("ACTUAL_C2_PROGRAMBUILDER_SOURCE_LINK_RELOAD_WITH_HOOKS");
        boolean scoped = closure != null && closure.generation == cached.generation() && closure.allocation == allocation
            && closure.program == c2Program.getProgramId();
        boolean alias = scoped && closure.allLiveAliases;
        boolean generatedSource = scoped && closure.currentConsumedGeneratedSource;
        boolean transformed = scoped && closure.transformedSemantics;
        boolean draw = scoped && closure.immutableQuadAndActualCall
            && quad != null && Set.of(GL11.GL_UNSIGNED_BYTE, GL11.GL_UNSIGNED_SHORT, GL11.GL_UNSIGNED_INT).contains(quad.indexType());
        if (!alias) gaps.add("FULL_LIVE_ALIAS_CLOSURE:geometry/Extended/Fallback/Sodium/shadow/custom-samplers/images/clear/fallbackTex/host/other-targets");
        if (!generatedSource) gaps.add("SELECTED_GENERATED_ARCHIVE_TO_ACTUAL_CONSUMED_C2_SOURCE_EQUALITY");
        if (!transformed) gaps.add("CONSUMED_TRANSFORMED_C2_VERTEX_FRAGMENT_SEMANTICS");
        if (!draw) gaps.add("IMMUTABLE_QUAD_PAYLOAD_AND_ACTUAL_TRANSFORMED_NATIVE_DRAW_CALL");
        if (quad == null) gaps.add("FIRST_CANONICAL_C2_QUAD_BINDING_OBSERVATION_THEN_REPEAT_SHADOW_REQUEST");
        boolean lifecycle = HistoryRotationPins.hooksVerified() && scoped && closure.transformedLifecycleScope;
        if (!lifecycle) gaps.add("ALL_DEDICATED_TRANSFORMED_LIFECYCLE_HOOKS_AND_OUTER_SCOPE_WITNESS");
        boolean sampling = sampling(a, rt3.getWidth(), rt3.getHeight()) && sampling(b, rt3.getWidth(), rt3.getHeight()) && "R11F_G11F_B10F".equals(rt3.getInternalFormat().name());
        if (!sampling) gaps.add("NATIVE_RT3_FORMAT_FILTER_WRAP_LOD0");
        boolean cleared = FULL_CLEAR.contains(targets) && !targets.isFullClearRequired();
        if (!cleared) gaps.add("RETURNED_NATIVE_FULL_CLEAR");
        if (GL11.glGetError() != GL11.GL_NO_ERROR) gaps.add("PREPARATION_GL_ERROR");
        missing = List.copyOf(gaps);
        if (!gaps.isEmpty()) { status = "REFUSED_MISSING_JOINS"; return; }
        quad = new HistoryRotationPlan.QuadBinding(quad.vertexArray(), quad.elementBuffer(), quad.primitive(), quad.count(), quad.indexType(), quad.indexOffset(), draw, quad.attributes());
        var proof = new HistoryRotationPlan.Proof(true, generatedSource, alias, linked, transformed, draw, lifecycle, sampling, cleared, closure.evidence);
        if (!configured) { HistoryRotationRuntime.configureAdmission(() -> cached != null && context() && currentEpoch() == cached); configured = true; }
        boolean prepared = HistoryRotationRuntime.prepare(new HistoryRotationPlan.Request(targets, composite, finalOwner, cached, proof, quad), HistoryRotationPreparation::currentEpoch);
        status = prepared ? "SHADOW_PREPARED" : "REFUSED_RUNTIME_PREPARATION";
    }
    /** Only cheap identity/generation/extent reads on normal frames; no reflection, scans or new Epoch. */
    private static HistoryRotationPlan.Epoch currentEpoch() {
        if (cached == null || cached.pipeline() != Iris.getPipelineManager().getPipelineNullable()
                || cached.pack() != Iris.getCurrentPack().orElse(null) || cached.world() != Minecraft.getInstance().level
                || cached.generation() != OptimizationConfig.generation() || targets == null || targets.get(3) != cachedRt3
                || cachedRt3.getMainTexture() != cachedA || cachedRt3.getAltTexture() != cachedB || targets.get(3).getWidth() != cached.width()
                || targets.get(3).getHeight() != cached.height()) return null;
        return cached;
    }
    /** Setup-only, loaded values (never queued values); reuse admission with actual platform/source context. */
    /** Shared pinned native100/AA2 generated-source admission; never prepares the old rotation plan. */
    public static boolean pinnedCurrentContext() { return context(); }
    private static boolean context() {
        try {
            var pack = Iris.getCurrentPack().orElse(null); var level = Minecraft.getInstance().level;
            String selected = Iris.getIrisConfig().getShaderPackName().orElse(null);
            if (pack == null || level == null || selected == null || Iris.isFallback() || !selected.equals(Iris.getCurrentPackName())) return false;
            var directory = Iris.getShaderpacksDirectory().toAbsolutePath().normalize(); var archive = directory.resolve(selected).normalize();
            if (!archive.startsWith(directory) || archive.equals(directory)) return false;
            boolean generated = "makeup-ultra-fast-shaders".equals(PackSelectionService.verifyGeneratedRendererGraph(archive, DeadMipAdmission.effective(pack)));
            var values = pack.getShaderPackOptions().getOptionValues();
            var typed = new HashMap<String,CompileOptionControl.NativeOption>();
            pack.getShaderPackOptions().getOptionSet().getBooleanOptions().forEach((k,e) -> typed.put(k, new CompileOptionControl.NativeOption("BOOLEAN", Set.of("false","true"), Boolean.toString(e.getOption().getDefaultValue()), Boolean.toString(values.getBooleanValueOrDefault(k)))));
            pack.getShaderPackOptions().getOptionSet().getStringOptions().forEach((k,e) -> typed.put(k, new CompileOptionControl.NativeOption("STRING", Set.copyOf(e.getOption().getAllowedValues()), e.getOption().getDefaultValue(), values.getStringValueOrDefault(k))));
            var mods = ModList.get(); String iris = mods.getModContainerById("iris").map(m -> m.getModInfo().getVersion().toString()).orElse("");
            var c = new MakeUpDeadCopyAdmission.Context(SharedConstants.getCurrentVersion().getName(), "neoforge", MakeUpDeadCopyAdmission.canonicalIrisMetadata(iris), HistoryRotationPins.exactApi(), generated, level.dimension().location().toString(), mods.isLoaded("distanthorizons"), mods.isLoaded("voxy"));
            return MakeUpDeadCopyAdmission.eligible(c, typed) && "1".equals(typed.get(CompileOptionControl.MAKEUP_DEAD_FRAME_COPY).value());
        } catch (Exception | LinkageError refused) { return false; }
    }
    private static boolean sourceJoin() {
        Link link = LINKS.get(composite);
        if (link == null || link.program != c2Program || link.input.source.getParent().getPack() != cached.pack()) return false;
        Input i = link.input;
        return i.geometry == null && i.vertex != null && i.fragment != null && i.rawVertex != null && i.rawFragment != null
            && i.rawVertex.equals(i.source.getVertexSource().orElse(null)) && i.rawFragment.equals(i.source.getFragmentSource().orElse(null));
    }
    /** Finite canonical observation only; caller must independently close payload/call immutability. */
    public static void observeQuad(Object owner) {
        if (!STARTUP || HistoryRotationRuntime.prepared() || owner != composite || cached == null || c2Program == null
                || GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != programId) return;
        observingDraw = true;
        if (quad != null) return;
        try {
            var attributes = new ArrayList<HistoryRotationPlan.Attribute>();
            int count = GL20.glGetProgrami(c2Program.getProgramId(), GL20.GL_ACTIVE_ATTRIBUTES);
            if (count <= 0 || count > 16) throw new IllegalStateException("ATTRIBUTE_CENSUS_BOUND");
            var size = org.lwjgl.BufferUtils.createIntBuffer(1); var type = org.lwjgl.BufferUtils.createIntBuffer(1);
            for (int index = 0; index < count; index++) {
                String name = GL20.glGetActiveAttrib(programId, index, size, type);
                int location = GL20.glGetAttribLocation(c2Program.getProgramId(), name);
                var pointer = org.lwjgl.BufferUtils.createPointerBuffer(1);
                GL20.glGetVertexAttribPointerv(location, GL20.GL_VERTEX_ATTRIB_ARRAY_POINTER, pointer);
                attributes.add(new HistoryRotationPlan.Attribute(name, location, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING), GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_SIZE), GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_TYPE), GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_NORMALIZED) != 0, GL20.glGetVertexAttribi(location, GL30.GL_VERTEX_ATTRIB_ARRAY_INTEGER) != 0, GL20.glGetVertexAttribi(location, GL20.GL_VERTEX_ATTRIB_ARRAY_STRIDE), pointer.get(0), GL.getCapabilities().OpenGL33 ? GL20.glGetVertexAttribi(location, GL33.GL_VERTEX_ATTRIB_ARRAY_DIVISOR) : -1));
            }
            // Index type/payload/call remain unproved; do not infer them from the EBO handle.
            quad = new HistoryRotationPlan.QuadBinding(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING), GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING), GL11.GL_TRIANGLES, 6, 0, 0, false, attributes);
            status = "CANONICAL_QUAD_OBSERVED_CALL_AND_PAYLOAD_UNPROVEN";
        } catch (RuntimeException | LinkageError refused) { status = "REFUSED_QUAD_OBSERVATION:" + refused.getClass().getSimpleName(); }
    }
    public static void quadScopeReturned() { if (STARTUP) observingDraw = false; }
    /** Actual GL11.glDrawElements arguments inside the canonical c2 wrapper, not an inferred EBO type. */
    public static void nativeDraw(int primitive, int count, int type, long offset) {
        if (!STARTUP || !observingDraw || quad == null) return;
        if (HistoryRotationRuntime.prepared()) return;
        quad = new HistoryRotationPlan.QuadBinding(quad.vertexArray(), quad.elementBuffer(), primitive, count, type, offset, false, quad.attributes());
        status = "ACTUAL_NATIVE_DRAW_ARGUMENTS_OBSERVED_PAYLOAD_IMMUTABILITY_UNPROVEN";
    }
    public static void beforeResize(Object target, int version, int texture, int width, int height, Object format) {
        if (!STARTUP || target != targets || cached == null) return;
        if (version != depthVersion || texture != depthTexture || format != depthFormat
                || width != cached.width() || height != cached.height() || targets.isFullClearRequired()) close("BEFORE_NATIVE_ALLOCATION_CHANGE");
    }
    public static void beforeRecalculate(Object owner) {
        if (!STARTUP) return;
        HistoryRotationRuntime.beforeRendererRecalculate(owner);
        if (owner == composite || owner == finalOwner) invalidate("BEFORE_RENDERER_RECALCULATE");
    }
    public static void targetsDestroyed(Object target) {
        if (!STARTUP) return;
        HistoryRotationRuntime.beforeTargetsDestroy(target);
        FULL_CLEAR.remove(target); PENDING_CLEAR.remove(target);
        if (target == targets) { invalidate("TARGET_DESTROY"); targets = null; }
    }
    private static boolean sampling(int texture, int width, int height) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT) == GL30.GL_R11F_G11F_B10F
                && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == width
                && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) == height
                && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD) == -1000f
                && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD) == 1000f
                && (!(GL.getCapabilities().GL_EXT_texture_filter_anisotropic || GL.getCapabilities().GL_ARB_texture_filter_anisotropic)
                    || GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, 0x84FE) == 1f)
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER) == GL11.GL_LINEAR
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER) == GL11.GL_LINEAR
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL) == 0
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S) == GL12.GL_CLAMP_TO_EDGE
                && GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T) == GL12.GL_CLAMP_TO_EDGE
                && GL11.glGetTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS) == 0;
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }
    }
    public static void close(String reason) {
        if (!STARTUP) return;
        HistoryRotationRuntime.closeBeforeMutation(reason); invalidate(reason);
    }
    public static void destroyed(Object owner) {
        if (!STARTUP) return;
        if (owner == pipeline || owner == composite || owner == finalOwner) close("BEFORE_NATIVE_RENDERER_OR_PIPELINE_DESTROY");
        if (owner == pipeline) { pipeline = null; targets = null; composite = null; finalOwner = null; programId = 0; c2Program = null; }
        LINKS.remove(owner); INPUTS.remove(owner);
    }
    public static void frameFailed(Throwable cause) { if (STARTUP) {
        // onFullClear clears Iris's request bit before the native clear loop runs.
        // A thrown loop must never be promoted to completed initialization next frame.
        PENDING_CLEAR.clear(); FULL_CLEAR.clear(); invalidate("OUTER_FRAME_FAILED");
        HistoryRotationRuntime.abortFrame(cause);
    } }
    public static void frameReturned() {
        if (!STARTUP) return;
        var s = HistoryRotationRuntime.snapshot();
        if (s.prepared() && s.state() != null && s.state().phase() != HistoryRotationState.Phase.IDLE)
            HistoryRotationRuntime.abortFrame(new IllegalStateException("OUTER_FRAME_RETURN_WITHOUT_FINAL_COMPLETION"));
    }
    private static void invalidate(String reason) { cached = null; closure = null; quad = null; observingDraw = false; status = "INVALIDATED:" + reason; }
    public static Report snapshot() {
        Link link = composite == null ? null : LINKS.get(composite);
        ConsumedSource source = link == null ? null : new ConsumedSource(link.input.rawVertex, link.input.rawFragment, link.input.vertex, link.input.fragment);
        return new Report(status, cached == null ? -1 : cached.generation(), allocation, programId, framebuffersSeen,
            cached != null && sourceJoin(), quad, source, missing, attachments, HistoryRotationRuntime.snapshot());
    }
    static Object field(Object owner, String name) throws ReflectiveOperationException {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
            catch (NoSuchFieldException absent) { /* pinned inherited resource */ }
        }
        throw new NoSuchFieldException(name);
    }
}
