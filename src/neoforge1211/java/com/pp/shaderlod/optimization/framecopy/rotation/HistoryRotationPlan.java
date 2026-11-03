package com.pp.shaderlod.optimization.framecopy.rotation;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.GlStateManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Prepare/reload-only reflection and metadata census. No canonical metadata is ever changed. */
public final class HistoryRotationPlan implements AutoCloseable {
    /** Maintained G01 inserts scalar exposure and shifts native TAA to composite3. */
    public static final String HISTORY_PASS = "composite3";
    // Failed prepare has no active selectors, but must retain ownership of any failed deletion.
    private static final List<GlFramebuffer> FAILED_PREPARE_CLEANUP = new ArrayList<>(2);
    private static final List<HistoryRotationPlan> FAILED_PLAN_CLEANUP = new ArrayList<>(1);
    /** These are root-owned live/source proofs, not deductions from an enabled G1 macro. */
    public record Proof(boolean exactNative100Aa2Overworld, boolean currentGeneratedSource,
                        boolean liveAliasesClosed, boolean sourceToProgramClosed,
                        boolean transformedShaderOwned, boolean actualDrawHookOwned,
                        boolean lifecycleHooksOwned, boolean nativeSamplingAndFormat,
                        boolean initializedByNativeFullClear, String evidence) {
        public boolean closed() {
            return exactNative100Aa2Overworld && currentGeneratedSource && liveAliasesClosed
                && sourceToProgramClosed && transformedShaderOwned && actualDrawHookOwned
                && lifecycleHooksOwned && nativeSamplingAndFormat && initializedByNativeFullClear
                && evidence != null && !evidence.isBlank();
        }
    }
    /** Supplier must return cached, allocation-free state on the render thread; no hashing here. */
    public record Epoch(Object pipeline, Object pack, Object world, long generation, long allocation,
                        int width, int height, boolean g1, boolean sixPeersOff, boolean noDh, boolean noVoxy) {
        public boolean eligible() {
            return pipeline != null && pack != null && world != null && width > 0 && height > 0
                && g1 && sixPeersOff && noDh && noVoxy;
        }
        public boolean matches(Epoch other) {
            return other != null && other.eligible() && pipeline == other.pipeline && pack == other.pack
                && world == other.world && generation == other.generation && allocation == other.allocation
                && width == other.width && height == other.height && eligible();
        }
    }
    public record Attribute(String name, int location, int buffer, int size, int type, boolean normalized,
                            boolean integer, int stride, long pointer, int divisor) {}
    /** Root witnesses native quad payload/indices and their immutable ownership, not just handles. */
    public record QuadBinding(int vertexArray, int elementBuffer, int primitive, int count, int indexType,
                              long indexOffset, boolean payloadAndNativeCallOwned, List<Attribute> attributes) {
        public QuadBinding { attributes = List.copyOf(attributes); }
        boolean closed() {
            return vertexArray > 0 && elementBuffer > 0 && primitive == GL11.GL_TRIANGLES && count == 6
                && (indexType == GL11.GL_UNSIGNED_BYTE || indexType == GL11.GL_UNSIGNED_SHORT || indexType == GL11.GL_UNSIGNED_INT)
                && indexOffset == 0 && payloadAndNativeCallOwned && !attributes.isEmpty()
                && attributes.stream().allMatch(a -> a.name != null && !a.name.isBlank() && a.location >= 0
                    && a.buffer > 0 && a.size >= 1 && a.size <= 4 && a.stride >= 0 && a.pointer >= 0 && a.divisor == 0);
        }
    }
    public record Request(RenderTargets targets, Object compositeOwner, Object finalOwner,
                          Epoch epoch, Proof proof, QuadBinding quad) {}
    public record Copy(int target, GlFramebuffer from, int framebuffer, int source, int destination, int width, int height) {}
    final RenderTargets targets;
    final RenderTarget rt3;
    final Object compositeOwner, finalOwner;
    final Epoch epoch;
    final Proof proof;
    final QuadBinding quad;
    final GlFramebuffer canonicalC2;
    final int a, b, rt1Write, program, historyLocation, maxTextureUnits;
    final HistoryRotationSamplerLatch historySampler = new HistoryRotationSamplerLatch();
    final ImmutableList<?> canonical, remove3, reverse3;
    final List<Copy> nativeCopies, omittedCopies, reversedCopies;
    final Copy fromB, fromA;
    private final HistoryRotationResources<GlFramebuffer> resources;

    private HistoryRotationPlan(Request r, RenderTarget target, GlFramebuffer c2, Program program,
                                ImmutableList<?> swaps, List<Copy> copies, int index,
                                HistoryRotationResources<GlFramebuffer> resources) throws ReflectiveOperationException {
        targets = r.targets; rt3 = target; compositeOwner = r.compositeOwner; finalOwner = r.finalOwner;
        epoch = r.epoch; proof = r.proof; quad = r.quad; canonicalC2 = c2;
        a = target.getMainTexture(); b = target.getAltTexture(); rt1Write = c2.getColorAttachment(0);
        this.program = program.getProgramId();
        if (GL20.glGetProgrami(this.program, GL20.GL_ACTIVE_ATTRIBUTES) != quad.attributes.size())
            throw new IllegalArgumentException("NATIVE_ACTIVE_ATTRIBUTE_CENSUS");
        var locations = new java.util.HashSet<Integer>();
        for (Attribute attribute : quad.attributes)
            if (!locations.add(attribute.location) || GL20.glGetAttribLocation(this.program, attribute.name) != attribute.location)
                throw new IllegalArgumentException("NATIVE_ATTRIBUTE_PROGRAM_JOIN");
        historyLocation = GL20.glGetUniformLocation(this.program, "colortex3");
        if (historyLocation < 0) throw new IllegalArgumentException("MISSING_LIVE_HISTORY_SAMPLER");
        // Iris defers sampler uniform initialization until Program.use. Never read it here.
        maxTextureUnits = GL11.glGetInteger(GL20.GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        this.resources = resources; canonical = swaps; nativeCopies = List.copyOf(copies);
        fromB = copies.get(index);
        fromA = new Copy(3, resources.fromA(), resources.fromA().getId(), a, b, epoch.width, epoch.height);
        var without = new ArrayList<Object>(swaps); without.remove(index); remove3 = ImmutableList.copyOf(without);
        var reverse = new ArrayList<Object>(swaps);
        // SwapPass is package-private. Construct exactly once; no reflection on the render path.
        Class<?> type = swaps.get(index).getClass();
        var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); Object replacement = ctor.newInstance();
        set(replacement, "target", 3); set(replacement, "width", epoch.width); set(replacement, "height", epoch.height);
        set(replacement, "from", resources.fromA()); set(replacement, "targetTexture", b);
        reverse.set(index, replacement); reverse3 = ImmutableList.copyOf(reverse);
        var omitted = new ArrayList<>(copies); omitted.remove(index); omittedCopies = List.copyOf(omitted);
        var reversed = new ArrayList<>(copies); reversed.set(index, fromA); reversedCopies = List.copyOf(reversed);
    }
    static HistoryRotationPlan prepare(Request r) throws ReflectiveOperationException {
        if (!FAILED_PREPARE_CLEANUP.isEmpty() || !FAILED_PLAN_CLEANUP.isEmpty())
            throw new IllegalStateException("REFUSED_PREPARE_FBO_CLEANUP_REQUIRED");
        if (r == null || r.targets == null || r.compositeOwner == null || r.finalOwner == null
                || r.epoch == null || !r.epoch.eligible() || r.proof == null || !r.proof.closed()
                || r.quad == null || !r.quad.closed())
            throw new IllegalArgumentException("ROOT_LIVE_SOURCE_LIFECYCLE_PROOF_NOT_CLOSED");
        if (r.targets.isFullClearRequired()) throw new IllegalArgumentException("WAIT_FOR_NATIVE_FULL_CLEAR");
        if (field(r.compositeOwner, "renderTargets") != r.targets || field(r.finalOwner, "renderTargets") != r.targets)
            throw new IllegalArgumentException("GRAPH_OWNER_MISMATCH");
        RenderTarget target = r.targets.get(3);
        if (target == null || target.getWidth() != r.epoch.width || target.getHeight() != r.epoch.height
                || !"R11F_G11F_B10F".equals(target.getInternalFormat().name())
                || target.getMainTexture() == target.getAltTexture() || target.getMainTexture() <= 0 || target.getAltTexture() <= 0)
            throw new IllegalArgumentException("RT3_STORAGE_MISMATCH");
        int a = target.getMainTexture(), b = target.getAltTexture();
        Object selected = null; GlFramebuffer c2 = null;
        for (Object pass : (List<?>) field(r.compositeOwner, "passes")) {
            if (!HistoryRotationPlan.HISTORY_PASS.equals(field(pass, "name"))) continue;
            if (selected != null) throw new IllegalArgumentException("NONUNIQUE_C2");
            selected = pass; c2 = (GlFramebuffer) field(pass, "framebuffer");
            if (!java.util.Arrays.equals((int[]) field(pass, "drawBuffers"), new int[]{1, 3})
                    || ((Set<?>) field(pass, "stageReadsFromAlt")).contains(3)
                    || !((Set<?>) field(pass, "mipmappedBuffers")).isEmpty()
                    || (int) field(pass, "viewWidth") != r.epoch.width || (int) field(pass, "viewHeight") != r.epoch.height
                    || field(pass, "blendModeOverride") != null || c2.hasDepthAttachment()
                    || c2.getColorAttachment(1) != b || c2.getColorAttachment(0) == a || c2.getColorAttachment(0) == b)
                throw new IllegalArgumentException("C2_GRAPH_MISMATCH");
            for (Object compute : (Object[]) field(pass, "computes"))
                if (compute != null) throw new IllegalArgumentException("C2_COMPUTE");
        }
        if (selected == null) throw new IllegalArgumentException("NO_C2");
        // Identity, not attachment integers alone: only this unique cached FBO may be substituted.
        int uses = 0;
        for (Object pass : (List<?>) field(r.compositeOwner, "passes"))
            if (field(pass, "framebuffer") == c2) uses++;
        if (uses != 1) throw new IllegalArgumentException("NONUNIQUE_ORIGINAL_C2_FBO");
        ImmutableList<?> swaps = (ImmutableList<?>) field(r.finalOwner, "swapPasses");
        var copies = new ArrayList<Copy>(); var order = new ArrayList<Integer>(); int index = -1;
        for (Object swap : swaps) {
            int n = (int) field(swap, "target"); RenderTarget t = r.targets.get(n);
            var fbo = (GlFramebuffer) field(swap, "from");
            int dest = (int) field(swap, "targetTexture"), w = (int) field(swap, "width"), h = (int) field(swap, "height");
            if (t == null || fbo.getColorAttachment(0) != t.getAltTexture() || dest != t.getMainTexture()
                    || w != t.getWidth() || h != t.getHeight()) throw new IllegalArgumentException("NATIVE_COPY_METADATA");
            if (n == 3) index = copies.size();
            order.add(n); copies.add(new Copy(n, fbo, fbo.getId(), t.getAltTexture(), dest, w, h));
        }
        if (!order.equals(List.of(6, 4, 1, 3))) throw new IllegalArgumentException("REQUIRES_G1_FOUR_COPY_GRAPH");
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlFramebuffer twin = null, fromA = null;
        try {
            twin = new GlFramebuffer(); twin.addColorAttachment(0, c2.getColorAttachment(0)); twin.addColorAttachment(1, a);
            twin.drawBuffers(new int[]{0, 1}); twin.readBuffer(0);
            fromA = new GlFramebuffer(); fromA.addColorAttachment(0, a); fromA.drawBuffers(new int[]{0}); fromA.readBuffer(0);
            if (twin.getStatus() != GL30.GL_FRAMEBUFFER_COMPLETE || fromA.getStatus() != GL30.GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalArgumentException("INCOMPLETE_EXTRA_FBO");
            var lease = new HistoryRotationResources<>(twin, fromA, GlFramebuffer::destroy);
            return new HistoryRotationPlan(r, target, c2, (Program) field(selected, "program"), swaps, copies, index, lease);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            retireFailedPrepare(twin, e); retireFailedPrepare(fromA, e);
            throw e;
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        }
    }
    public boolean valid(Epoch now) {
        return epoch.matches(now) && storageValid();
    }
    boolean storageValid() {
        return !resources.closed() && targets.get(3) == rt3
            && rt3.getMainTexture() == a && rt3.getAltTexture() == b
            && rt3.getWidth() == epoch.width && rt3.getHeight() == epoch.height;
    }
    GlFramebuffer twin() { return resources.twin(); }
    Copy copy(HistoryRotationState.Direction direction) { return direction == HistoryRotationState.Direction.A_TO_B ? fromA : fromB; }
    @Override public void close() { resources.close(); }
    void retireAfterPrepareFailure(Throwable cause) {
        try { close(); }
        catch (RuntimeException | LinkageError cleanup) { FAILED_PLAN_CLEANUP.add(this); cause.addSuppressed(cleanup); }
    }
    private static void retireFailedPrepare(GlFramebuffer fbo, Throwable failure) {
        if (fbo == null) return;
        try { fbo.destroy(); }
        catch (RuntimeException | LinkageError cleanup) { FAILED_PREPARE_CLEANUP.add(fbo); failure.addSuppressed(cleanup); }
    }
    static void retryPrepareCleanup() {
        var iterator = FAILED_PREPARE_CLEANUP.iterator();
        while (iterator.hasNext()) { iterator.next().destroy(); iterator.remove(); }
        var plans = FAILED_PLAN_CLEANUP.iterator();
        while (plans.hasNext()) { plans.next().close(); plans.remove(); }
    }
    static void forgetCleanupAfterContextLoss() { FAILED_PREPARE_CLEANUP.clear(); FAILED_PLAN_CLEANUP.clear(); }
    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner.getClass();
        while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
    private static void set(Object owner, String name, Object value) throws ReflectiveOperationException {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
}
