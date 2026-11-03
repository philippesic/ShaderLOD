package com.pp.shaderlod.dev;

import com.mojang.blaze3d.systems.RenderSystem;
import com.pp.shaderlod.CoreUniformFrame;
import com.pp.shaderlod.ShaderLodConfig;
import com.pp.shaderlod.benchmark.BenchmarkWorld;
import com.pp.shaderlod.measurement.RuntimeMeasurement;
import com.pp.shaderlod.optimization.OptimizationConfig;
import com.pp.shaderlod.optimization.OptimizationEditTransaction;
import com.pp.shaderlod.optimization.OptimizationRuntimeContext;
import com.pp.shaderlod.optimization.OptimizationSnapshot;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.reimaginedearlyalpha.ReimaginedReceiverCompiledPrograms;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundEarlyAlphaRuntime;
import com.pp.shaderlod.optimization.unboundearlyalpha.UnboundReceiverCompiledPrograms;
import com.pp.shaderlod.tuning.AutoTuner;
import com.pp.shaderlod.tuning.SavedTunings;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One synchronous, untimed negative control on real GL uniforms and real runtime entry points.
 * Never registers an owner, changes configuration, submits geometry, or grants native permission.
 * Runtime refusal counters/sampler memos may change; this is not a performance measurement.
 */
public final class ReceiverClearDiagnostic {
    private static final String PERMISSION = "shaderlodComplementaryReceiverPermission";
    private static final String VERTEX = """
        #version 330 core
        void main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); }
        """;
    private static final String FRAGMENT = """
        #version 330 core
        uniform int shaderlodComplementaryReceiverPermission;
        out vec4 diagnosticColor;
        void main() {
            diagnosticColor = vec4(float(shaderlodComplementaryReceiverPermission), 0.0, 0.0, 1.0);
        }
        """;

    private ReceiverClearDiagnostic() {}

    // Pure seam for no-GL checks. Both the frame snapshot and persisted requests must refuse GPU work.
    static void requireOff(CoreUniformFrame.Core core, OptimizationSnapshot requests) {
        if (core == null || core.settings().enabled() || requests == null
                || !OptimizationEditTransaction.cpuOnlyRequests(core.optimizations())
                || !OptimizationEditTransaction.cpuOnlyRequests(requests))
            throw new IllegalStateException("Core and ALL GPU requests must already be Off (frame and direct config)");
    }

    public static Map<String, Object> capture() {
        if (!RenderSystem.isOnRenderThread()) throw new IllegalStateException("Render thread required");
        var client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.getWindow() == null
                || client.getMainRenderTarget() == null || client.getMainRenderTarget().width <= 0
                || client.getMainRenderTarget().height <= 0)
            throw new IllegalStateException("Valid in-world client and render target required");
        long context = GLFW.glfwGetCurrentContext();
        if (context == 0 || context != client.getWindow().getWindow() || !GL.getCapabilities().OpenGL33)
            throw new IllegalStateException("Current client GL33 context required");
        Object capabilities = GL.getCapabilities();
        if (AutoTuner.active() || AutoTuner.restorationPending()
                || BenchmarkProfileComparison.active() || BenchmarkWorld.active()
                || RuntimeMeasurement.needed() || SavedTunings.pending()
                || Boolean.TRUE.equals(BenchmarkMeasurementGuard.status().get("armed"))
                || Boolean.getBoolean("shaderlod.dev.gpuTiming") || Boolean.getBoolean("shaderlod.dev.cpuTiming"))
            throw new IllegalStateException("Exclusive untimed diagnostic lane required");
        // This launch property selects production-mod presence; it is not a measurement lease.
        if (!"on".equals(System.getProperty("shaderlod.dev.compare")))
            throw new IllegalStateException("Normal production-mod-present dev launch required");
        if (!OptimizationConfig.loaded() || ShaderLodConfig.snapshot().enabled())
            throw new IllegalStateException("Loaded config and direct core Off required");
        var core = OptimizationRuntimeContext.core();
        var requests = OptimizationConfig.snapshot();
        long generation = OptimizationConfig.generation();
        requireOff(core, requests);
        if (Iris.isFallback() || !(Iris.getPipelineManager().getPipelineNullable() instanceof IrisRenderingPipeline)
                || Iris.getCurrentPack().isEmpty() || !Iris.getShaderPackOptionQueue().isEmpty())
            throw new IllegalStateException("Settled valid Iris pipeline required");
        requireNoError("entry (pre-existing errors)");
        int previous = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (previous != 0 && (!GL20.glIsProgram(previous)
                || GL20.glGetProgrami(previous, GL20.GL_DELETE_STATUS) != GL11.GL_FALSE
                || GL20.glGetProgrami(previous, GL20.GL_LINK_STATUS) != GL11.GL_TRUE))
            throw new IllegalStateException("Previous program cannot safely be restored");
        requireNoError("save current program");

        // Slots acquire ownership immediately, before any operation that may throw.
        int[] objects = new int[3]; // vertex, fragment, program
        Throwable primary = null;
        var result = new LinkedHashMap<String, Object>();
        try {
            objects[0] = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            compile(objects[0], VERTEX, "vertex");
            objects[1] = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            compile(objects[1], FRAGMENT, "fragment");
            objects[2] = GL20.glCreateProgram();
            int program = objects[2];
            if (program == 0) throw new IllegalStateException("glCreateProgram returned zero");
            GL20.glAttachShader(program, objects[0]);
            GL20.glAttachShader(program, objects[1]);
            GL20.glLinkProgram(program);
            requireNoError("link");
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE)
                throw new IllegalStateException("Link failed: " + GL20.glGetProgramInfoLog(program));
            int location = GL20.glGetUniformLocation(program, PERMISSION);
            requireNoError("permission location");
            if (location < 0) throw new IllegalStateException("Permission optimized out of linked output");
            if (ReimaginedReceiverCompiledPrograms.nativeRegistration(program)
                    || UnboundReceiverCompiledPrograms.nativeRegistration(program)
                    || ReimaginedReceiverCompiledPrograms.ownsBoundary(program)
                    || UnboundReceiverCompiledPrograms.ownsBoundary(program))
                throw new IllegalStateException("Temporary handle collides with retained native registration");
            GL20.glUseProgram(program);
            requireNoError("bind temporary program");
            result.put("reimagined", check("reimagined", program, location, ReimaginedEarlyAlphaRuntime::draw,
                    ReimaginedEarlyAlphaRuntime::revoke));
            result.put("unbound", check("unbound", program, location, UnboundEarlyAlphaRuntime::draw,
                    UnboundEarlyAlphaRuntime::revoke));
            requireOff(OptimizationRuntimeContext.core(), OptimizationConfig.snapshot());
            if (ShaderLodConfig.snapshot().enabled() || generation != OptimizationConfig.generation()
                    || !core.equals(OptimizationRuntimeContext.core())
                    || !requests.equals(OptimizationConfig.snapshot()))
                throw new IllegalStateException("Configuration changed during diagnostic");
            result.put("uniform_location_present", true);
            result.put("core_and_all_gpu_requests_off", true);
            result.put("selected_family", OptimizationConfig.activeFamily());
            result.put("preset", core.preset().name());
            result.put("dimension", client.level.dimension().location().toString());
            result.put("geometry_submissions", 0);
            result.put("positive_final_native_permission_proven", false);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            // A runtime must not switch contexts. Never delete same-numbered objects in another context.
            if (!sameContext(context, capabilities)) {
                cleanup = new IllegalStateException("Context changed; cannot safely restore/delete original-context objects");
            } else {
                cleanup = attempt(cleanup, () -> {
                    GL20.glUseProgram(previous);
                    requireNoError("restore previous program");
                    if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != previous)
                        throw new IllegalStateException("Previous program restoration mismatch");
                });
                cleanup = attempt(cleanup, () -> {
                    if (objects[2] != 0) GL20.glDeleteProgram(objects[2]);
                    requireNoError("delete temporary program");
                });
                cleanup = attempt(cleanup, () -> {
                    if (objects[0] != 0) GL20.glDeleteShader(objects[0]);
                    requireNoError("delete vertex shader");
                });
                cleanup = attempt(cleanup, () -> {
                    if (objects[1] != 0) GL20.glDeleteShader(objects[1]);
                    requireNoError("delete fragment shader");
                });
                cleanup = attempt(cleanup, () -> {
                    if (objects[2] != 0 && GL20.glIsProgram(objects[2])
                            || objects[0] != 0 && GL20.glIsShader(objects[0])
                            || objects[1] != 0 && GL20.glIsShader(objects[1]))
                        throw new IllegalStateException("Temporary GL object survived deletion");
                    if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != previous)
                        throw new IllegalStateException("Final current program mismatch");
                    requireNoError("cleanup verification");
                });
            }
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else rethrow(cleanup);
            }
        }
        result.put("previous_program_restored", true);
        result.put("temporary_objects_deleted", true);
        result.put("gl_errors", 0);
        return Map.copyOf(result);
    }

    private static Map<String, Integer> check(String family, int program, int location, Runnable draw, Runnable revoke) {
        long context = GLFW.glfwGetCurrentContext();
        Object capabilities = GL.getCapabilities();
        Throwable primary = null;
        try {
            GL20.glUniform1i(location, 1);
            expect(program, location, 1, family + " seed");
            draw.run(); // Actual runtime, including all native permission guards; no replacement implementation.
            requireContext(context, capabilities);
            expect(program, location, 0, family + " after draw");
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = attempt(null, () -> {
                // Never revoke against another context or accidentally touch the saved native program.
                requireContext(context, capabilities);
                GL20.glUseProgram(program);
                requireNoError("bind for revoke");
            });
            cleanup = attempt(cleanup, () -> {
                requireContext(context, capabilities);
                if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program)
                    throw new IllegalStateException("Cannot safely revoke: temporary program is not current");
                revoke.run();
            });
            cleanup = attempt(cleanup, () -> {
                requireContext(context, capabilities);
                expect(program, location, 0, family + " after revoke");
            });
            if (cleanup != null) {
                if (primary != null) primary.addSuppressed(cleanup);
                else rethrow(cleanup);
            }
        }
        return Map.of("seed", 1, "after_draw", 0, "after_finally_revoke", 0);
    }

    private static void requireContext(long context, Object capabilities) {
        if (!sameContext(context, capabilities))
            throw new IllegalStateException("Diagnostic GL context changed");
    }

    private static boolean sameContext(long context, Object capabilities) {
        try { return GLFW.glfwGetCurrentContext() == context && GL.getCapabilities() == capabilities; }
        catch (RuntimeException unavailable) { return false; }
    }

    private static void compile(int shader, String source, String stage) {
        if (shader == 0) throw new IllegalStateException(stage + " glCreateShader returned zero");
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        requireNoError(stage + " compile");
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE)
            throw new IllegalStateException(stage + " compile failed: " + GL20.glGetShaderInfoLog(shader));
        requireNoError(stage + " compile status");
    }

    private static void expect(int program, int location, int expected, String stage) {
        requireNoError(stage);
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != program)
            throw new IllegalStateException(stage + ": current program changed");
        int actual = GL20.glGetUniformi(program, location);
        requireNoError(stage + " readback");
        if (actual != expected)
            throw new IllegalStateException(stage + ": expected permission " + expected + ", got " + actual);
    }

    private static void requireNoError(String stage) {
        var errors = new StringBuilder();
        // Finite even for a broken/lost context; consume and report every observed error.
        for (int i = 0; i < 16; i++) {
            int error = GL11.glGetError();
            if (error == GL11.GL_NO_ERROR) {
                if (errors.isEmpty()) return;
                throw new IllegalStateException(stage + ": GL errors " + errors);
            }
            if (!errors.isEmpty()) errors.append(',');
            errors.append("0x").append(Integer.toHexString(error));
        }
        throw new IllegalStateException(stage + ": GL error drain limit: " + errors);
    }

    private static Throwable attempt(Throwable first, Runnable action) {
        try { action.run(); }
        catch (RuntimeException | Error failure) {
            if (first == null) return failure;
            first.addSuppressed(failure);
        }
        return first;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        throw (RuntimeException) failure;
    }
}
