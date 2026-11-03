package com.pp.shaderlod.dev.timing;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/** Development-only submission counters and one terminal snapshot; no GL getters. */
public final class DhRenderEvidence {
    private static final boolean ENABLED = Boolean.getBoolean("shaderlod.dev.dhDrawEvidence");
    private static final String PINNED_SHA256 = "864e70def5b0d54d619b940b66bb2784e91d3f3fc253064fdc1b3fc5786cbfab";
    private static final String PREFIX = "com.seibel.distanthorizons.";
    // world opaque, world translucent, shadow opaque, shadow translucent
    private static final long[] RENDER_INVOCATIONS = new long[4];
    private static final long[] DRAW_CALLS = new long[4];
    private static final long[] INDEX_COUNTS = new long[4];
    private static final Set<String> OBSERVED_RENDERERS = new LinkedHashSet<>();
    private static long nonpositiveIndexCalls;
    private static JsonObject terminalSnapshot;

    private DhRenderEvidence() {}

    public static boolean enabled() { return ENABLED; }

    /** Live cumulative counters only; no terminal freeze, file reads or GL getters. */
    public static JsonObject liveCounters() {
        var result = new JsonObject();
        result.addProperty("enabled", ENABLED);
        result.addProperty("terminal", terminalSnapshot != null);
        var draws = new JsonArray(); var indices = new JsonArray();
        for (int i = 0; i < DRAW_CALLS.length; i++) { draws.add(DRAW_CALLS[i]); indices.add(INDEX_COUNTS[i]); }
        result.add("draws", draws); result.add("indices", indices);
        result.addProperty("scope", "cumulative CPU submissions; divide deltas by completed render frames for steady geometry observations");
        return result;
    }

    /** Cheap render-thread marker; establishes the hook was exercised even if every buffer is skipped. */
    public static void rendererEntered(String renderer, boolean opaque, boolean shadow) {
        if (!ENABLED || terminalSnapshot != null) return;
        RENDER_INVOCATIONS[scope(opaque, shadow)]++;
        if (OBSERVED_RENDERERS.size() < 4) OBSERVED_RENDERERS.add(renderer);
    }

    /** Call only after the original terrain glDrawElements delegate returned normally. */
    public static void drawSubmitted(int indices, boolean opaque, boolean shadow) {
        if (!ENABLED || terminalSnapshot != null) return;
        if (indices <= 0) { nonpositiveIndexCalls++; return; }
        int scope = scope(opaque, shadow);
        DRAW_CALLS[scope]++;
        INDEX_COUNTS[scope] += indices;
    }

    /** First call freezes this JVM's counters. Caller must finish measurement first, on the render thread. */
    public static JsonObject captureTerminalSnapshot() {
        if (terminalSnapshot != null) return terminalSnapshot.deepCopy();
        JsonObject result = new JsonObject();
        // Freeze before reflection/file reads; no further hooked draws enter the terminal counters.
        terminalSnapshot = result;
        result.addProperty("schema", 1);
        result.addProperty("captured_utc", Instant.now().toString());
        result.addProperty("opt_in", ENABLED);
        result.addProperty("counter_scope", "Cumulative since this JVM's first opted-in hooked terrain draw; warmup included; frozen at first terminal snapshot");
        result.addProperty("submission_semantics", "Positive-index glDrawElements delegate returned normally after DH skip checks; CPU submission evidence, not GPU completion or visible distant pixels");
        result.addProperty("last_frame_counts", "unknown: no frame observer or retained DH draw counters used");
        result.addProperty("pinned_dh_version", "3.3.3");
        result.addProperty("pinned_dh_sha256", PINNED_SHA256);
        result.add("actual_distant_terrain_visible", unknown("requires rendered image/scene evidence; draw calls alone do not prove visible distant pixels"));
        JsonArray renderers = new JsonArray();
        OBSERVED_RENDERERS.forEach(renderers::add);
        result.add("observed_terrain_renderers", renderers);
        JsonObject draws = new JsonObject();
        String[] names = {"world_opaque", "world_translucent", "shadow_opaque", "shadow_translucent"};
        long invocations = 0;
        for (int i = 0; i < names.length; i++) {
            invocations += RENDER_INVOCATIONS[i];
            JsonObject phase = new JsonObject();
            if (!ENABLED) {
                phase = unknown("shaderlod.dev.dhDrawEvidence was not enabled");
            } else if (RENDER_INVOCATIONS[i] == 0) {
                phase = unknown("no hooked terrain renderer invocation observed for this scope; not interpreted as zero draws");
            } else {
                phase.addProperty("status", "observed_cpu_submissions");
                phase.addProperty("terrain_renderer_invocations", RENDER_INVOCATIONS[i]);
                phase.addProperty("positive_index_draw_calls", DRAW_CALLS[i]);
                phase.addProperty("submitted_indices", INDEX_COUNTS[i]);
            }
            draws.add(names[i], phase);
        }
        result.add("cumulative_draws", draws);
        result.addProperty("nonpositive_index_delegate_returns_excluded", nonpositiveIndexCalls);
        result.addProperty("hook_exercised", ENABLED && invocations > 0);
        result.addProperty("scope_classifier", "render(..., boolean opaque, ...) true=opaque/false=translucent; Iris ShadowRenderer.ACTIVE at the submission call true=shadow/false=world");
        try {
            boolean installed = ModList.get().isLoaded("distanthorizons");
            result.addProperty("installed", installed);
            if (!installed) {
                result.add("dh_state", unknown("DH is not installed"));
                return result.deepCopy();
            }
            for (var mod : ModList.get().getMods()) {
                if (mod.getModId().equals("distanthorizons")) result.addProperty("installed_version", mod.getVersion().toString());
            }
            captureArtifact(result);
            captureState(result);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            result.add("dh_state_error", unknown(error(exception)));
            if (!result.has("dh_state")) result.add("dh_state", unknown("DH state could not be resolved"));
        }
        return result.deepCopy();
    }

    private static void captureArtifact(JsonObject result) {
        JsonObject artifact = new JsonObject();
        result.add("loaded_artifact", artifact);
        try {
            Path path = null;
            for (var file : ModList.get().getModFiles()) {
                if (file.getMods().stream().anyMatch(mod -> mod.getModId().equals("distanthorizons"))) {
                    path = file.getFile().getFilePath();
                    break;
                }
            }
            if (path == null || !Files.isRegularFile(path)) {
                result.add("loaded_artifact", unknown("DH artifact is not a queryable regular file"));
                return;
            }
            artifact.addProperty("path", path.toAbsolutePath().toString());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = new byte[8192];
                int count;
                while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            artifact.addProperty("sha256", actual);
            artifact.addProperty("matches_inspected_dh_artifact", actual.equals(PINNED_SHA256));
        } catch (Exception exception) {
            result.add("loaded_artifact", unknown(error(exception)));
        }
    }

    private static void captureState(JsonObject result) throws ReflectiveOperationException {
        Class<?> sharedApi = Class.forName(PREFIX + "core.api.internal.SharedApi");
        Object world = sharedApi.getMethod("tryGetDhClientWorld").invoke(null);
        Class<?> clientApi = Class.forName(PREFIX + "core.api.internal.ClientApi");
        Object state = clientApi.getField("RENDER_STATE").get(null);
        Object client = clientApi.getField("INSTANCE").get(null);
        JsonObject stateInfo = new JsonObject();
        result.add("dh_state", stateInfo);
        stateInfo.addProperty("renderer_disabled_because_of_exceptions", clientApi.getField("rendererDisabledBecauseOfExceptions").getBoolean(client));
        Object validation = clientApi.getField("lastRenderParamValidationMessage").get(client);
        if (validation == null) stateInfo.add("last_render_param_validation_message", unknown("no retained message"));
        else stateInfo.addProperty("last_render_param_validation_message", validation.toString());
        if (state == null || world == null) {
            stateInfo.add("render_list", unknown("DH render state or client world absent"));
            return;
        }
        Object wrapper = state.getClass().getField("clientLevelWrapper").get(state);
        if (wrapper == null) {
            stateInfo.add("render_list", unknown("DH render state's client-level wrapper absent"));
            return;
        }
        Object level = world.getClass().getMethod("getClientLevel", Class.forName(PREFIX + "core.wrapperInterfaces.world.ILevelWrapper")).invoke(world, wrapper);
        if (level == null) {
            stateInfo.add("render_list", unknown("DH client level absent"));
            return;
        }
        Object handler = level.getClass().getMethod("getRenderBufferHandler").invoke(level);
        if (handler == null) {
            stateInfo.add("render_list", unknown("DH render-buffer handler absent"));
            return;
        }
        JsonObject list = new JsonObject();
        stateInfo.add("render_list", list);
        list.addProperty("handler_class", handler.getClass().getName());
        Object debug = handler.getClass().getMethod("getVboRenderDebugMenuString").invoke(handler);
        if (debug instanceof String text) list.addProperty("raw_vbo_debug_string", text);
        else list.add("raw_vbo_debug_string", unknown("DH returned a null/non-string debug value"));
        list.addProperty("semantics", "Last built render-list container/culling counters, not VBO draw calls, allocated VBOs, or last-frame opaque/translucent submissions");
        boolean pinned = result.has("loaded_artifact")
                && result.getAsJsonObject("loaded_artifact").has("matches_inspected_dh_artifact")
                && result.getAsJsonObject("loaded_artifact").get("matches_inspected_dh_artifact").getAsBoolean();
        for (String fieldName : new String[]{"visibleBufferCount", "culledBufferCount", "shadowVisibleBufferCount", "shadowCulledBufferCount"}) {
            if (!pinned) {
                list.add(fieldName, unknown("loaded DH artifact identity is not the inspected pinned jar"));
                continue;
            }
            try {
                Field field = handler.getClass().getDeclaredField(fieldName);
                if (!field.trySetAccessible()) list.add(fieldName, unknown("private read access denied"));
                else list.addProperty(fieldName, field.getInt(handler));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                list.add(fieldName, unknown(error(exception)));
            }
        }
    }

    private static int scope(boolean opaque, boolean shadow) {
        return (shadow ? 2 : 0) + (opaque ? 0 : 1);
    }

    private static JsonObject unknown(String reason) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "unknown");
        result.addProperty("reason", reason);
        return result;
    }

    private static String error(Throwable exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : exception;
        return cause.getClass().getName() + ": " + String.valueOf(cause.getMessage());
    }
}
