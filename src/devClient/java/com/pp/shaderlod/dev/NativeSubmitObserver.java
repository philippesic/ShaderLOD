package com.pp.shaderlod.dev;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedReceiverCompiledPrograms;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundReceiverCompiledPrograms;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.tuning.AutoTuner;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/** Finite, read-only, render-thread DEV witness of the sole native terrain submit and its outer unwind. */
public final class NativeSubmitObserver {
    private NativeSubmitObserver() {}
    private static final String[] UNIFORMS = {
        "shaderlodComplementaryReceiverPermission",
        "shaderlodReimaginedTemporalShadowSamplingQualified",
        "shaderlodReimaginedEarlyAlphaPermission",
        "shaderlodUnboundTemporalShadowSamplingQualified",
        "shaderlodUnboundEarlyAlphaPermission"
    };
    private static final int LIMIT = 256, MAX_DEPTH = 8;
    private static final Object OVERFLOW_TOKEN = new Object();
    private static final ArrayDeque<Scope> SCOPES = new ArrayDeque<>();
    private static final List<Map<String,Object>> ROWS = new ArrayList<>();
    private static final List<String> FAILURE_REASONS = new ArrayList<>();
    private static final IdentityHashMap<Object,Integer> LABELS = new IdentityHashMap<>();
    private static boolean armed;
    private static boolean truncated;
    private static int dropped, failures, skipped, skippedShadows, unscoped, nativeCalls, scopes;
    private static int overflowDepth;
    private static String family;
    private static SodiumPrograms.Pass requestedPass;
    private static Thread owner;
    private static Object armedPack, armedPipeline;

    private static final class Scope {
        final int depth;
        final List<Sample> samples = new ArrayList<>();
        Scope(int depth) { this.depth = depth; }
    }
    private record Identity(String family, Thread thread, long context, Object capabilities,
                            Object pipeline, Object pack, long epoch, Object receipt,
                            GlProgram<?> program, int handle, SodiumPrograms.Pass pass,
                            Object source, Object certificate) {}
    private record Sample(Identity identity, Map<String,Object> entry) {}

    private static void untimed() {
        if (!RenderSystem.isOnRenderThread() || AutoTuner.active() || AutoTuner.restorationPending()
                || RuntimeMeasurement.needed() || BenchmarkProfileComparison.active())
            throw new IllegalStateException("untimed render thread required");
    }
    public static void arm() { arm("SOLID"); }
    public static void arm(String pass) {
        requestedPass = switch (pass) {
            case "SOLID" -> SodiumPrograms.Pass.TERRAIN;
            case "CUTOUT" -> SodiumPrograms.Pass.TERRAIN_CUTOUT;
            default -> throw new IllegalArgumentException("SOLID or CUTOUT required");
        };
        if (!Boolean.getBoolean("shaderlod.dev.nativeSubmitObserver")) throw new IllegalStateException("observer mixins not enabled");
        untimed();
        if (armed) throw new IllegalStateException("observer already armed; export first");
        if (!SCOPES.isEmpty() || overflowDepth != 0) throw new IllegalStateException("draw still open");
        var client = Minecraft.getInstance();
        if (client.level == null || client.level.dimension() != Level.OVERWORLD || Iris.isFallback()
                || !Iris.getShaderPackOptionQueue().isEmpty() || client.getWindow() == null)
            throw new IllegalStateException("settled native Overworld required");
        String selected = Iris.getIrisConfig().getShaderPackName().orElse("");
        family = switch (OptimizationConfig.activeFamily()) {
            case "complementary-reimagined" -> "complementary-reimagined";
            case "complementary-unbound" -> "complementary-unbound";
            default -> throw new IllegalStateException("pinned Complementary family required");
        };
        String original = family.equals("complementary-reimagined")
                ? "ComplementaryReimagined_r5.9.3.zip" : "ComplementaryUnbound_r5.9.3.zip";
        // Receiver controls live in the generated graph. Every sampled program must
        // still join its family's current compilation receipt in identity().
        if (!selected.equals(original) && !(selected.startsWith("ShaderLOD-" + family + "-") && selected.endsWith(".zip")))
            throw new IllegalStateException("pinned original/generated selection required");
        if (!family.equals(OptimizationConfig.activeFamily()) || !selected.equals(Iris.getCurrentPackName()))
            throw new IllegalStateException("selected family mismatch");
        if (GLFW.glfwGetCurrentContext() != client.getWindow().getWindow() || !GL.getCapabilities().OpenGL33)
            throw new IllegalStateException("current client GL33 context required");
        if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("pre-existing GL error");
        armedPack = Iris.getCurrentPack().orElse(null);
        armedPipeline = Iris.getPipelineManager().getPipelineNullable();
        if (armedPack == null || !(armedPipeline instanceof IrisRenderingPipeline))
            throw new IllegalStateException("native graph unavailable");
        ROWS.clear(); FAILURE_REASONS.clear(); LABELS.clear();
        dropped = failures = skipped = skippedShadows = unscoped = nativeCalls = scopes = overflowDepth = 0;
        truncated = false; owner = Thread.currentThread(); armed = true;
    }
    public static Map<String,Object> export() {
        untimed();
        if (!SCOPES.isEmpty() || overflowDepth != 0) throw new IllegalStateException("draw still open");
        armed = false;
        var result = new LinkedHashMap<String,Object>();
        result.put("family", family == null ? "none" : family);
        result.put("requested_pass", requestedPass == null ? "none" : requestedPass.name());
        result.put("rows", List.copyOf(ROWS)); result.put("native_calls", nativeCalls);
        result.put("scopes", scopes); result.put("dropped", dropped);
        result.put("skipped_other_program", skipped); result.put("unscoped_calls", unscoped);
        result.put("skipped_shadow_draw", skippedShadows);
        result.put("failures", failures); result.put("failure_reasons", List.copyOf(FAILURE_REASONS));
        result.put("limit", LIMIT); result.put("truncated", truncated);
        result.put("scope", "first joined terrain multidraw per arm; not all submissions");
        return result;
    }
    public static Object enter() {
        if (!armed) return null;
        if (Thread.currentThread() != owner) { failure(new IllegalStateException("observer thread changed")); return null; }
        if (overflowDepth > 0 || SCOPES.size() >= MAX_DEPTH) {
            overflowDepth++; dropped++; return OVERFLOW_TOKEN;
        }
        Scope scope = new Scope(SCOPES.size() + 1); SCOPES.push(scope); scopes++; return scope;
    }
    public static Object nativeEntry(int mode, long counts, int type, long indices, int drawCount, long bases) {
        if (!armed) return null;
        // getProgram(SOLID/CUTOUT) selects shadow variants while Iris renders shadows.
        // These receipts certify the main terrain programs only.
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) { skippedShadows++; return null; }
        if (overflowDepth > 0) { dropped++; return null; }
        if (SCOPES.isEmpty()) { unscoped++; return null; }
        nativeCalls++;
        Scope scope = SCOPES.peek();
        if (ROWS.size() + scope.samples.size() >= LIMIT) { dropped++; return null; }
        try {
            Identity id = identity(0);
            if (id == null || id.pass != requestedPass) { skipped++; return null; }
            var row = new LinkedHashMap<String,Object>();
            row.put("family", id.family); row.put("pass", id.pass.name()); row.put("depth", scope.depth);
            row.put("context", id.context); row.put("program", id.handle);
            row.put("link_context_capabilities", label(id.capabilities));
            row.put("program_object", label(id.program));
            row.put("receipt", label(id.receipt));
            // COMPILED.put creates a fresh retained Entry for each successful native link.
            row.put("link_generation", label(id.receipt));
            row.put("certificate", label(id.certificate));
            row.put("source", label(id.source));
            row.put("pipeline", label(id.pipeline));
            row.put("pack", label(id.pack)); row.put("epoch", id.epoch);
            var core = com.pp.shaderlod.optimization.OptimizationRuntimeContext.core();
            row.put("preset", core.preset().name());
            row.put("dimension", "minecraft:overworld");
            row.put("selected_pack", Iris.getCurrentPackName());
            row.put("mode", mode); row.put("type", type); row.put("draw_count", drawCount);
            row.put("native_returned", false);
            row.put("counts_pointer", counts); row.put("indices_pointer", indices); row.put("bases_pointer", bases);
            row.put("native_entry", readUniforms(id.handle));
            var sample = new Sample(id, row);
            scope.samples.add(sample);
            return sample;
        } catch (RuntimeException | LinkageError error) { failure(error); return null; }
    }
    public static void nativeReturned(Object token) {
        if (!(token instanceof Sample sample)) return;
        try { checkGl("native return"); sample.entry.put("native_returned", true); }
        catch (RuntimeException | LinkageError error) {
            failure(error); sample.entry.put("native_return_error", error.toString());
        }
    }
    public static void leave(Object token) {
        if (token == OVERFLOW_TOKEN) { overflowDepth--; return; }
        if (!(token instanceof Scope scope)) return;
        try {
            if (SCOPES.peek() != scope) { failure(new IllegalStateException("scope nesting changed")); return; }
            for (Sample sample : scope.samples) {
                var row = sample.entry;
                try {
                    int current = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                    checkGl("post current binding");
                    row.put("current_binding_after_unwind", current);
                    Identity now = identity(sample.identity.handle);
                    if (now == null || !same(sample.identity, now)) {
                        failure(new IllegalStateException("native-entry receipt/program identity changed"));
                        row.put("post_unwind", Map.of("status", "unavailable: identity changed"));
                    } else {
                        row.put("post_unwind", readUniforms(sample.identity.handle));
                    }
                } catch (RuntimeException | LinkageError error) {
                    failure(error); row.put("post_unwind", Map.of("status", "unavailable: " + error.getClass().getSimpleName()));
                }
                if (ROWS.size() < LIMIT) ROWS.add(row); else { dropped++; truncated = true; }
            }
            if (!ROWS.isEmpty()) armed = false; // One complete joined entry/outer-unwind observation per arm.
        } finally { if (SCOPES.peek() == scope) SCOPES.pop(); else SCOPES.remove(scope); }
    }
    private static boolean same(Identity a, Identity b) {
        return NativeSubmitIdentityGuard.same(snapshot(a), snapshot(b));
    }
    public static void externalFailure(Throwable error) { failure(error); }
    private static void failure(Throwable error) {
        failures++;
        if (FAILURE_REASONS.size() < 16) FAILURE_REASONS.add(error.toString());
    }
    private static int label(Object object) {
        var value = LABELS.get(object);
        if (value != null) return value;
        if (LABELS.size() >= 2048) throw new IllegalStateException("observer identity labels exhausted");
        int next = LABELS.size() + 1; LABELS.put(object, next); return next;
    }
    private static NativeSubmitIdentityGuard.Snapshot snapshot(Identity id) {
        return new NativeSubmitIdentityGuard.Snapshot(id.thread, id.context, id.capabilities,
                id.pipeline, id.pack, id.epoch, id.receipt, id.program, id.handle,
                id.pass, id.source, id.certificate);
    }
    private static Identity identity(int targetHandle) {
        untimed();
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered())
            throw new IllegalStateException("main terrain observation required");
        if (Thread.currentThread() != owner) throw new IllegalStateException("observer thread changed");
        var client = Minecraft.getInstance();
        if (client.level == null || client.level.dimension() != Level.OVERWORLD || client.getWindow() == null)
            throw new IllegalStateException("world unavailable");
        if (com.pp.shaderlod.optimization.OptimizationRuntimeContext.core() == null)
            throw new IllegalStateException("core snapshot unavailable");
        long context = GLFW.glfwGetCurrentContext(); Object capabilities = GL.getCapabilities();
        if (context == 0 || context != client.getWindow().getWindow()) throw new IllegalStateException("context changed");
        if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("pre-existing GL error");
        int handle = targetHandle;
        if (handle == 0) { handle = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); checkGl("current program"); }
        if (handle <= 0 || !GL20.glIsProgram(handle) || GL20.glGetProgrami(handle, GL20.GL_LINK_STATUS) != GL11.GL_TRUE
                || GL20.glGetProgrami(handle, GL20.GL_DELETE_STATUS) != GL11.GL_FALSE)
            throw new IllegalStateException("current linked live program required");
        checkGl("program lifetime");
        var pipeline = Iris.getPipelineManager().getPipelineNullable(); var pack = Iris.getCurrentPack().orElse(null);
        if (!(pipeline instanceof IrisRenderingPipeline iris) || pack == null
                || pipeline != armedPipeline || pack != armedPack || Iris.isFallback()
                || !Iris.getShaderPackOptionQueue().isEmpty() || !family.equals(OptimizationConfig.activeFamily()))
            throw new IllegalStateException("selected graph changed");
        var programs = iris.getSodiumPrograms();
        if (programs == null || programs.getClass() != SodiumPrograms.class) throw new IllegalStateException("Sodium graph changed");
        SodiumPrograms.Pass pass = null; GlProgram<?> program = null;
        for (var candidate : List.of(SodiumPrograms.Pass.TERRAIN, SodiumPrograms.Pass.TERRAIN_CUTOUT)) {
            var renderPass = candidate == SodiumPrograms.Pass.TERRAIN ? DefaultTerrainRenderPasses.SOLID : DefaultTerrainRenderPasses.CUTOUT;
            var value = programs.getProgram(renderPass);
            if (value != null && value.handle() == handle) { pass = candidate; program = value; break; }
        }
        if (pass == null) return null; // unrelated draw: no receiver claim
        if (family.equals("complementary-reimagined")) {
            long epoch = ReimaginedEarlyAlphaRuntime.epoch();
            var entry = ReimaginedReceiverCompiledPrograms.current(handle, pipeline, pack, epoch);
            if (entry == null || entry.program() != program || entry.certificate().pass() != pass
                    || entry.certificate().context() != capabilities) throw new IllegalStateException("Reimagined receipt unjoined: "+receiptDiagnostic(ReimaginedReceiverCompiledPrograms.class,handle,pipeline,pack,epoch,program,pass,capabilities));
            return new Identity(family, owner, context, capabilities, pipeline, pack, epoch, entry,
                    program, handle, pass, entry.certificate().source().source(), entry.certificate());
        }
        long epoch = UnboundEarlyAlphaRuntime.epoch();
        var entry = UnboundReceiverCompiledPrograms.current(handle, pipeline, pack, epoch);
        if (entry == null || entry.program() != program || entry.certificate().pass() != pass
                || entry.certificate().context() != capabilities) throw new IllegalStateException("Unbound receipt unjoined: "+receiptDiagnostic(UnboundReceiverCompiledPrograms.class,handle,pipeline,pack,epoch,program,pass,capabilities));
        return new Identity(family, owner, context, capabilities, pipeline, pack, epoch, entry,
                program, handle, pass, entry.certificate().source().source(), entry.certificate());
    }
    private static String receiptDiagnostic(Class<?> registry,int handle,Object pipeline,Object pack,long epoch,Object program,Object pass,Object caps) {
        try {
            var f=registry.getDeclaredField("COMPILED"); f.setAccessible(true);
            var store=(com.pp.shaderlod.optimization.sourcecache.LinkedProgramLocations<?,?>)f.get(null);
            var raw=store.current(handle);
            if(raw==null)return "no lifetime certificate for current handle";
            Object n=raw.certificate();Class<?> t=n.getClass();
            var source=(com.pp.shaderlod.optimization.sourcecache.IrisCompiledSource.Certificate)t.getMethod("source").invoke(n);
            return "program="+(raw.program()==program)+",pass="+t.getMethod("pass").invoke(n)+"/"+pass
                +",pipeline="+(t.getMethod("pipeline").invoke(n)==pipeline)+",pack="+(t.getMethod("pack").invoke(n)==pack)
                +",epoch="+t.getMethod("epoch").invoke(n)+"/"+epoch+",caps="+(t.getMethod("context").invoke(n)==caps)
                +",selected="+source.selected()+"/"+Iris.getIrisConfig().getShaderPackName().orElse("")
                +",sourceEpoch="+source.epoch()+",setPack="+(source.set().getPack()==pack)
                +",options="+source.input().options().current(((net.irisshaders.iris.shaderpack.ShaderPack)pack).getShaderPackOptions());
        } catch(ReflectiveOperationException|RuntimeException e){return "diagnostic unavailable: "+e.getClass().getSimpleName();}
    }
    private static Map<String,Object> readUniforms(int handle) {
        var values = new LinkedHashMap<String,Object>();
        for (String name : UNIFORMS) {
            int location = GL20.glGetUniformLocation(handle, name); checkGl("uniform location " + name);
            values.put(name, location < 0 ? Map.of("status", "absent", "location", -1)
                    : Map.of("status", "present", "location", location, "value", uniform(handle, location, name)));
        }
        return values;
    }
    private static int uniform(int handle, int location, String name) {
        int value = GL20.glGetUniformi(handle, location); checkGl("uniform value " + name); return value;
    }
    private static void checkGl(String stage) {
        if (GL11.glGetError() != GL11.GL_NO_ERROR) throw new IllegalStateException("GL error at " + stage);
    }
}
