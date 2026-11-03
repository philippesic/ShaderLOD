package com.pp.shaderlod.optimization.framecopy.rotation;

import com.google.common.collect.ImmutableList;
import com.pp.shaderlod.optimization.framecopy.FrameCopyRuntime;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.targets.RenderTargets;

/**
 * One render-thread owner, no automatic adoption. Registration and the root live/source proof are
 * intentionally external joins. With the startup property absent, no method performs GL work.
 */
public final class HistoryRotationRuntime {
    private HistoryRotationRuntime() {}
    public static final String STARTUP_PROPERTY = "shaderlod.experimentalMakeUpHistoryRotation";
    private static final boolean STARTUP = Boolean.getBoolean(STARTUP_PROPERTY);
    private static BooleanSupplier admission; // root's exact native100/options/source/peer-graph check
    private static boolean admissionConfigured;
    private static Session session;
    private static Snapshot last;
    private static String status = "DEFAULT_OFF";
    public record Snapshot(String status, boolean startupGate, boolean prepared, boolean enabled,
                           HistoryRotationState.Snapshot state, long shadowFrames, long guardQueries,
                           long nativeCalls, long rt3Calls, long repairs, long coverageVetoes,
                           long aborts, int supplierReturn, int selectedFbo, int copyCursor,
                           String coverage, String evidence, LiveWitness live,
                           List<CopyEvent> effectivePlan, List<CopyEvent> lastFrameCopies) {}
    public record CopyEvent(int target, int fbo, int source, int destination, int width, int height, int parity,
                            boolean returned, String kind) {}
    public record LiveWitness(long generation, long allocation, int physicalA, int physicalB,
                              int expectedRead, int expectedWrite, int program, int fbo, int sampler,
                              int output1, int output3, int x, int y, int width, int height,
                              boolean ownedBindings, boolean completeCoverage, String reason) {}
    /** Explicit abort is propagated out of Iris. Root must catch at its frame/reload boundary. */
    public static final class FrameAbort extends IllegalStateException {
        FrameAbort(String reason, Throwable cause) { super(reason, cause); }
    }
    private static final class Session {
        final HistoryRotationPlan p;
        final Thread owner = Thread.currentThread();
        final Supplier<HistoryRotationPlan.Epoch> current;
        final HistoryRotationGl gl = new HistoryRotationGl();
        final HistoryRotationState state = new HistoryRotationState();
        final java.util.ArrayList<CopyEvent> calls = new java.util.ArrayList<>(8);
        boolean enabled, pendingClose, quadTarget, samplerWitness, copyPending;
        int supplierReturn, selectedFbo, cursor, frameParity;
        long shadowFrames, queries, nativeCalls, rt3Calls, repairs, vetoes, aborts;
        List<HistoryRotationPlan.Copy> effective;
        HistoryRotationGl.Coverage coverage;
        Session(HistoryRotationPlan p, Supplier<HistoryRotationPlan.Epoch> current) {
            this.p = p; this.current = current; effective = p.nativeCopies;
        }
    }
    /** Install once, before prepare/activation. Invoked once per explicit enable attempt, never per frame. */
    public static void configureAdmission(BooleanSupplier rootAdmission) {
        if (admissionConfigured || session != null) throw new IllegalStateException("ADMISSION_ALREADY_CONFIGURED");
        admission = Objects.requireNonNull(rootAdmission); admissionConfigured = true;
    }
    public static boolean prepare(HistoryRotationPlan.Request request, Supplier<HistoryRotationPlan.Epoch> current) {
        if (!STARTUP) { status = "DEFAULT_OFF"; return false; }
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        if (session != null) throw new IllegalStateException("CLOSE_OLD_PLAN_BEFORE_PREPARE");
        last = null;
        if (!HistoryRotationPins.exactApi() || !HistoryRotationPins.hooksVerified()) {
            status = "REFUSED_PIN_OR_TRANSFORMED_HOOKS"; return false;
        }
        HistoryRotationPlan plan = null;
        try {
            if (request == null || current == null || request.epoch() == null || !request.epoch().matches(current.get())) {
                status = "REFUSED_CURRENT_EPOCH"; return false;
            }
            plan = HistoryRotationPlan.prepare(request);
            session = new Session(plan, current); status = "SHADOW_NATIVE_ONLY"; return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError refused) {
            if (plan != null) plan.retireAfterPrepareFailure(refused);
            status = "REFUSED_" + refused.getClass().getSimpleName() + ":" + refused.getMessage(); return false;
        }
    }
    /** Shadow first: requires a complete, owned live draw and actual returned canonical RT3 copy. */
    public static boolean enable() {
        Session s = ownedSession();
        if (s != null && s.enabled) return true;
        if (!STARTUP || s == null || s.state.phase() != HistoryRotationState.Phase.IDLE
                || !s.state.warm() || s.shadowFrames == 0 || !admissionConfigured) {
            status = "REFUSED_ARM_REQUIRES_ROOT_ADMISSION_AND_SHADOW"; return false;
        }
        boolean accepted;
        try { accepted = admission.getAsBoolean(); } catch (RuntimeException e) { accepted = false; }
        if (!accepted || FrameCopyRuntime.observing() || !valid(s)) {
            status = "REFUSED_ACTIVATION_ADMISSION"; return false;
        }
        s.enabled = true; status = "ARMED"; return true;
    }
    /** Queued in-frame; no sampler/FBO selector consults this mutable control independently. */
    public static void disable() {
        Session s = ownedSession(); if (s == null) return;
        s.enabled = false;
        if (s.state.inFrame()) { s.pendingClose = true; return; }
        closeBeforeMutation("OFF");
    }
    /** ROOT JOIN: after native clear/resize and before any admitted graph work. Exact frame latch. */
    public static void beginFrame(Object pipeline) {
        Session s = ownedSession(); if (s == null || pipeline != s.p.epoch.pipeline()) return;
        if (s.state.inFrame()) throw abort(s, "PREVIOUS_FRAME_INCOMPLETE", null);
        if (s.state.phase() == HistoryRotationState.Phase.SUSPENDED)
            throw new FrameAbort("SUSPENDED_REQUIRES_ROOT_RELOAD", null);
        if (s.pendingClose) { closeBeforeMutation("PRIOR_ABORT"); return; }
        if (FrameCopyRuntime.observing() || !valid(s)) {
            closeBeforeMutation(FrameCopyRuntime.observing() ? "CANONICAL_OBSERVER" : "FLAGS_OR_EPOCH_CHANGED"); return;
        }
        s.calls.clear(); s.cursor = 0; s.copyPending = s.samplerWitness = s.quadTarget = false;
        s.coverage = null; s.supplierReturn = s.selectedFbo = 0;
        s.state.begin(s.enabled); s.frameParity = s.state.parity();
        status = s.enabled ? "ROTATING_FRAME" : "SHADOW_NATIVE_ONLY";
    }
    public static int selectSampler(RenderTargets owner, int target, int original) {
        Session s = ownedSession(); if (s == null || owner != s.p.targets || target != 3) return original;
        if (s.state.phase() == HistoryRotationState.Phase.SUSPENDED)
            throw new FrameAbort("SUSPENDED_REQUIRES_ROOT_RELOAD", null);
        if (!s.state.inFrame()) {
            if (s.state.parity() != 0) throw abort(s, "SAMPLER_WITHOUT_FRAME_LATCH", null);
            return original;
        }
        requireLive(s);
        if (original != s.p.a && original != s.p.b) throw abort(s, "UNOWNED_SUPPLIER_RETURN", null);
        int selected = s.state.selecting() && s.state.parity() == 1
            ? (original == s.p.a ? s.p.b : s.p.a) : original;
        if (s.quadTarget && s.state.phase() == HistoryRotationState.Phase.BOUND) {
            if (original != s.p.a) throw abort(s, "C2_STAGE_SUPPLIER_NOT_MAIN", null);
            s.samplerWitness = true; s.supplierReturn = selected;
        }
        return selected;
    }
    /** Target is unique original FBO identity AND exact CompositeRenderer owner, not texture name. */
    public static GlFramebuffer selectFramebuffer(Object owner, GlFramebuffer original) {
        Session s = ownedSession(); if (s == null || owner != s.p.compositeOwner) return original;
        s.quadTarget = original == s.p.canonicalC2;
        if (!s.quadTarget) return original;
        if (!s.state.inFrame()) throw abort(s, "C2_WITHOUT_FRAME_LATCH", null);
        requireLive(s);
        try { s.state.bound(); } catch (RuntimeException e) { throw abort(s, e.getMessage(), e); }
        GlFramebuffer selected = s.state.selecting() && s.state.parity() == 1 ? s.p.twin() : original;
        s.selectedFbo = selected.getId(); return selected;
    }
    public static void beforeQuad(Object owner) {
        Session s = ownedSession(); if (s == null || owner != s.p.compositeOwner || !s.quadTarget) return;
        requireLive(s);
        try {
            if (!s.samplerWitness) throw new IllegalStateException("MISSING_C2_DYNAMIC_SUPPLIER_WITNESS");
            s.queries++;
            int read = s.state.readTexture(s.p.a, s.p.b), write = s.state.writeTexture(s.p.a, s.p.b);
            s.coverage = s.gl.beforeQuad(s.p, s.selectedFbo, read, write);
            if (!s.coverage.ownedBindings()) throw new IllegalStateException("UNOWNED_LIVE_BINDINGS:" + s.coverage.reason());
            boolean full = s.coverage.complete();
            if (!full) s.vetoes++;
            if (s.state.beforeDraw(full)) {
                // Program.use/FBO bind have ALREADY occurred. Seed W, preserve p and all bindings.
                repairCopy(s, s.state.seedDirection(), "PRE_QUAD_SEED"); s.state.seeded();
            }
            s.state.drawStarted();
        } catch (RuntimeException | LinkageError e) { throw abort(s, "PRE_QUAD:" + e.getMessage(), e); }
    }
    public static void quadReturned(Object owner) {
        Session s = ownedSession(); if (s == null || owner != s.p.compositeOwner || !s.quadTarget) return;
        try { s.state.drawReturned(); } catch (RuntimeException e) { throw abort(s, e.getMessage(), e); }
    }
    public static void quadFailed(Object owner, Throwable cause) {
        Session s = ownedSession(); if (s != null && owner == s.p.compositeOwner) throw abort(s, "QUAD_FAILED", cause);
    }
    public static void beginFinal(Object owner) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return;
        if (FrameCopyRuntime.observing()) throw abort(s, "OBSERVER_ENABLED_DURING_EXPERIMENTAL_FRAME", null);
        boolean current = valid(s); if (!current) { s.enabled = false; s.pendingClose = true; }
        try {
            boolean omit = s.state.beginFinal(current && s.enabled && !s.pendingClose);
            s.effective = omit ? s.p.omittedCopies : s.state.parity() == 1 ? s.p.reversedCopies : s.p.nativeCopies;
        } catch (RuntimeException e) { throw abort(s, e.getMessage(), e); }
    }
    public static ImmutableList<?> selectSwapList(Object owner, ImmutableList<?> original) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return original;
        if (original != s.p.canonical || s.state.phase() != HistoryRotationState.Phase.FINAL)
            throw abort(s, "FINAL_LIST_IDENTITY_OR_PHASE", null);
        return s.state.omit() ? s.p.remove3 : s.state.parity() == 1 ? s.p.reverse3 : original;
    }
    /** Called immediately around the actual native primitive, independent of the strict observer. */
    public static void beforeNativeCopy(Object owner, int target, int level, int xo, int yo, int x, int y, int w, int h) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return;
        try {
            var phase = s.state.phase();
            if (phase != HistoryRotationState.Phase.FINAL && phase != HistoryRotationState.Phase.COPIED)
                throw new IllegalStateException("NATIVE_COPY_WITHOUT_FINAL_PHASE");
            if (s.copyPending || s.cursor >= s.effective.size()) throw new IllegalStateException("EXTRA_COPY");
            var c = s.effective.get(s.cursor);
            if (target != org.lwjgl.opengl.GL11.GL_TEXTURE_2D || level != 0 || xo != 0 || yo != 0 || x != 0 || y != 0
                    || w != c.width() || h != c.height() || !s.gl.nativeEndpoints(c))
                throw new IllegalStateException("NATIVE_ENDPOINT_OR_ARGUMENT_MISMATCH");
            s.copyPending = true;
        } catch (RuntimeException | LinkageError e) { throw abort(s, e.getMessage(), e); }
    }
    public static void nativeCopyReturned(Object owner) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return;
        if (org.lwjgl.opengl.GL11.glGetError() != org.lwjgl.opengl.GL11.GL_NO_ERROR)
            nativeCopyFailed(owner, new IllegalStateException("NATIVE_COPY_GL_ERROR"));
        try {
            if (!s.copyPending) throw new IllegalStateException("COPY_RETURN_WITHOUT_CALL");
            var c = s.effective.get(s.cursor++); s.copyPending = false; s.nativeCalls++;
            s.calls.add(event(s, c, "NATIVE_SWAP"));
            if (c.target() == 3) { s.state.nativeCopyReturned(); s.rt3Calls++; }
        } catch (RuntimeException e) { throw abort(s, e.getMessage(), e); }
    }
    public static void nativeCopyFailed(Object owner, Throwable cause) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return;
        // A throwing W->R call may already have touched committed R. Never invent rollback safety.
        s.aborts++; s.enabled = false; s.pendingClose = true; s.state.suspend();
        status = "SUSPENDED_NATIVE_COPY_RESULT_UNKNOWN";
        throw new FrameAbort(status, cause);
    }
    public static void finalReturned(Object owner) {
        Session s = ownedSession(); if (s == null || owner != s.p.finalOwner) return;
        try {
            if (s.copyPending || s.cursor != s.effective.size()) throw new IllegalStateException("INCOMPLETE_COPY_STREAM");
            boolean shadow = !s.state.rotating();
            s.state.finalReturned();
            if (shadow && s.coverage != null && s.coverage.complete() && s.samplerWitness) s.shadowFrames++;
            status = s.enabled ? "ARMED" : "SHADOW_NATIVE_ONLY";
            if (s.pendingClose) closeBeforeMutation("DRAINED_CONTROL_OR_EPOCH_CHANGE");
        } catch (RuntimeException e) { throw abort(s, e.getMessage(), e); }
    }
    /** ROOT JOIN: catch render/final exceptions, BEFORE old native graph may be reused. */
    public static void abortFrame(Throwable cause) {
        Session s = ownedSession();
        if (s != null && s.state.phase() == HistoryRotationState.Phase.SUSPENDED)
            throw new FrameAbort("SUSPENDED_REQUIRES_ROOT_RELOAD", cause);
        if (s != null) throw abort(s, "ROOT_FRAME_ABORT", cause);
    }
    /** ROOT JOIN: before Off/Quality/Cancel/observer/reload/resize/full clear/destruction. */
    public static void closeBeforeMutation(String reason) {
        Session s = ownedSession(); if (s == null) return;
        if (s.state.phase() == HistoryRotationState.Phase.SUSPENDED)
            throw new FrameAbort("SUSPENDED_REQUIRES_PERMANENT_DISCARD:" + reason, null);
        if (s.state.inFrame()) throw abort(s, "MUTATION_DURING_FRAME:" + reason, null);
        try {
            if (s.state.needsRepair()) repairCopy(s, s.state.seedDirection(), "IDLE_COMMITTED_HISTORY");
            s.state.repaired(); s.enabled = false; s.state.close(); s.p.close(); status = "CLOSED:" + reason;
            last = snapshot(s, false); session = null;
        } catch (RuntimeException | LinkageError e) {
            s.state.suspend(); status = "SUSPENDED_REPAIR_REQUIRED:" + reason; throw new FrameAbort(status, e);
        }
    }
    public static void beforeResize(Object targets, int width, int height) {
        Session s = ownedSession(); if (s == null || targets != s.p.targets) return;
        if (width != s.p.epoch.width() || height != s.p.epoch.height() || s.p.targets.isFullClearRequired())
            closeBeforeMutation("BEFORE_RESIZE_OR_FULL_CLEAR");
    }
    public static void beforeRendererRecalculate(Object owner) {
        Session s = ownedSession();
        if (s != null && (owner == s.p.compositeOwner || owner == s.p.finalOwner)) closeBeforeMutation("BEFORE_RENDERER_RECALCULATE");
    }
    public static void beforeTargetsDestroy(Object targets) {
        Session s = ownedSession(); if (s != null && targets == s.p.targets) closeBeforeMutation("BEFORE_NATIVE_TARGETS_DESTROY");
    }
    /** Only after root irrevocably discards the entire graph; it MUST NOT be resumed after this call. */
    public static void discardForNativeRecreation(Object pipeline) {
        Session s = ownedSession(); if (s == null || pipeline != s.p.epoch.pipeline()) return;
        s.enabled = false; s.p.close(); status = "DISCARDED_NATIVE_RECREATION_REQUIRED";
        last = snapshot(s, false); session = null;
    }
    /** Confirmed loss of the OLD context means those GL objects no longer exist. No delete/copy. */
    public static void forgetAfterConfirmedContextLoss(Object pipeline) {
        if (!STARTUP) return;
        Session s = ownedSession();
        if (s == null) { HistoryRotationPlan.forgetCleanupAfterContextLoss(); return; }
        if (pipeline != s.p.epoch.pipeline()) return;
        s.enabled = false; s.state.suspend(); status = "CONTEXT_LOST_NATIVE_INITIALIZATION_REQUIRED";
        last = snapshot(s, false); session = null; HistoryRotationPlan.forgetCleanupAfterContextLoss();
    }
    public static void retryRefusedPreparationCleanup() {
        if (!STARTUP) return;
        com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
        HistoryRotationPlan.retryPrepareCleanup();
    }
    private static FrameAbort abort(Session s, String reason, Throwable cause) {
        s.aborts++; s.enabled = false; s.pendingClose = true;
        if (s.state.phase() == HistoryRotationState.Phase.SUSPENDED)
            return new FrameAbort(status, cause);
        try {
            // A partial W is never committed. A returned native copy already established equality.
            if (s.state.needsRepair()) repairCopy(s, s.state.seedDirection(), "ABORT_ROLLBACK_COMMITTED_HISTORY");
            s.state.repaired(); status = "ABORTED_REPAIRED:" + reason;
        } catch (RuntimeException | LinkageError repairFailed) {
            s.state.suspend(); status = "SUSPENDED_RELOAD_REQUIRED:" + reason;
            if (cause != null) repairFailed.addSuppressed(cause); cause = repairFailed;
        }
        return new FrameAbort(status, cause);
    }
    private static void repairCopy(Session s, HistoryRotationState.Direction direction, String kind) {
        try {
            if (!s.p.storageValid()) throw new IllegalStateException("OLD_STORAGE_NO_LONGER_VALID");
            var c = s.p.copy(direction); s.gl.copy(c); s.repairs++; s.calls.add(event(s, c, kind));
        } catch (RuntimeException | LinkageError failure) {
            s.enabled = false; s.state.suspend(); status = "SUSPENDED_EQUALIZATION_FAILED";
            throw new FrameAbort(status, failure);
        }
    }
    private static CopyEvent event(Session s, HistoryRotationPlan.Copy c, String kind) {
        return new CopyEvent(c.target(), c.framebuffer(), c.source(), c.destination(), c.width(), c.height(), s.state.parity(), true, kind);
    }
    private static boolean valid(Session s) {
        try { return s.p.valid(s.current.get()); } catch (RuntimeException | LinkageError e) { return false; }
    }
    private static Session ownedSession() {
        Session s = session;
        if (s != null && Thread.currentThread() != s.owner) throw new IllegalStateException("ROTATION_RENDER_THREAD_ONLY");
        return s;
    }
    private static void requireLive(Session s) {
        if (FrameCopyRuntime.observing()) throw abort(s, "CANONICAL_OBSERVER_CONFLICT", null);
        if (!valid(s)) throw abort(s, "MID_FRAME_FLAG_OR_EPOCH_CHANGE", null);
    }
    public static boolean prepared() { return ownedSession() != null; }
    public static Snapshot snapshot() {
        Session s = ownedSession();
        return s == null ? last != null ? last : new Snapshot(status, STARTUP, false, false, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "UNOBSERVED", "", null, List.of(), List.of())
            : snapshot(s, true);
    }
    private static Snapshot snapshot(Session s, boolean prepared) {
        var c = s.coverage;
        int p = s.frameParity;
        // The last draw parity is preserved in the actual-copy events; expected inputs below are
        // derived from the witnessed supplier, since finalReturned may already have committed p^1.
        int read = s.supplierReturn;
        var live = c == null ? null : new LiveWitness(s.p.epoch.generation(), s.p.epoch.allocation(), s.p.a, s.p.b,
            read, read == s.p.a ? s.p.b : s.p.a, c.program(), c.fbo(), c.sampler(), c.output1(), c.output3(),
            c.x(), c.y(), c.width(), c.height(), c.ownedBindings(), c.complete(), c.reason());
        var effective = s.effective.stream().map(copy -> new CopyEvent(copy.target(), copy.framebuffer(), copy.source(), copy.destination(),
            copy.width(), copy.height(), p, false, "PLANNED_NATIVE_SWAP")).toList();
        return new Snapshot(status, STARTUP, prepared, s.enabled, s.state.snapshot(), s.shadowFrames, s.queries,
                s.nativeCalls, s.rt3Calls, s.repairs, s.vetoes, s.aborts, s.supplierReturn, s.selectedFbo, s.cursor,
                s.coverage == null ? "UNOBSERVED" : s.coverage.reason(), s.p.proof.evidence(), live, effective, List.copyOf(s.calls));
    }
}
