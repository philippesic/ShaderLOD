package com.pp.shaderlod.dev;

import com.google.gson.JsonObject;
import com.pp.shaderlod.dev.timing.CpuTimers;
import com.pp.shaderlod.dev.timing.GpuTimers;
import java.util.ArrayList;
import java.util.List;
import static com.pp.shaderlod.dev.PhysicalResolutionSession.*;

/** Modeled lifecycle/negative contracts only; no client, driver or actual resize claim. */
public final class PhysicalResolutionContractTest {
    private static int checks;
    public static void main(String[] args) {
        dimensions(); lifecycle(); rejection(); inputs(); properties(); timing();
        System.out.println("PhysicalResolutionContractTest: " + checks + " modeled checks passed; actual runtime unverified");
    }
    private static long t(double seconds) { return (long) (seconds * 1e9); }
    private static ResourceStamp stamp(long allocation, long completion, Extent size) {
        return new ResourceStamp(allocation, allocation, allocation, allocation, allocation, 1,
                completion, allocation, size.physicalWidth(), size.physicalHeight(), "pipeline1");
    }
    private static ObservedSize size(Extent e) {
        return new ObservedSize(e.logicalWidth(), e.logicalHeight(), e.physicalWidth(), e.physicalHeight(),
                e.physicalWidth(), e.physicalHeight(), e.physicalWidth(), e.physicalHeight(), e.physicalWidth(), e.physicalHeight());
    }
    private static void dimensions() {
        check(matches(NATIVE, size(NATIVE))); check(matches(REDUCED, size(REDUCED)));
        check(!matches(NATIVE, size(REDUCED))); check(!matches(REDUCED, size(NATIVE)));
        var n = size(NATIVE);
        check(!matches(NATIVE, new ObservedSize(1488, 836, 1488, 836, 2976, 1672, 2976, 1672, 2976, 1672)));
        check(!matches(NATIVE, new ObservedSize(n.glfwWidth(), n.glfwHeight(), 2976, 1672, 2976, 1672, 2232, 1254, 2232, 1254)));
        check(!matchesResources(NATIVE, n, stamp(1, 2, REDUCED)));
        check((long) REDUCED.physicalWidth() * REDUCED.physicalHeight() * 16 == (long) NATIVE.physicalWidth() * NATIVE.physicalHeight() * 9);
    }
    private static void lifecycle() {
        var clock = new Timeline(0); check(!clock.warmed(t(29.999))); check(clock.warmed(t(30)));
        double now = 30;
        for (int p = 0; p < 3; p++) {
            check(clock.phase == p);
            var previous = stamp(Math.max(1, p), p + 1, p == 1 ? NATIVE : p == 2 ? REDUCED : NATIVE);
            var settled = stamp(p + 1, p + 2, ORDER.get(p));
            clock.request(t(now), previous);
            check(!clock.observe(t(now + 1), true, settled));
            check(!clock.observe(t(now + 8.999), true, settled));
            check(clock.observe(t(now + 9), true, settled));
            clock.capture(t(now + 9));
            check(!clock.captured(t(now + 28.999), true, settled));
            check(clock.captured(t(now + 29), true, settled));
            clock.next(); now += 30;
        }
        check(clock.state == Timeline.State.RESTORING);
    }
    private static void rejection() {
        var c = new Timeline(0); var before = stamp(1, 1, NATIVE); var completed = stamp(1, 2, NATIVE);
        c.request(t(30), before);
        check(!c.observe(t(31), true, before)); // selected/armed is not completed
        check(!c.observe(t(32), false, completed));
        check(!c.observe(t(33), true, completed));
        check(!c.observe(t(40), false, completed)); // mismatch resets settling
        check(!c.observe(t(41), true, completed));
        check(c.observe(t(49), true, completed)); c.capture(t(49));
        rejects(() -> c.captured(t(50), false, completed));
        rejects(() -> c.captured(t(50), true, stamp(2, 2, NATIVE)));
        var clock = new Timeline(0); clock.phase = 1; clock.request(0, before);
        check(!clock.observe(t(1), true, new ResourceStamp(2, 1, 2, 2, 2, 1, 2, 2, 2232, 1254, "pipeline1")));
        check(!clock.observe(t(2), true, new ResourceStamp(2, 2, 2, 2, 1, 1, 2, 2, 2232, 1254, "pipeline1")));
        check(!clock.observe(t(3), true, new ResourceStamp(2, 2, 2, 2, 2, 1, 2, 1, 2232, 1254, "pipeline1")));
        rejects(() -> clock.observe(t(30), true, stamp(2, 2, REDUCED)));
        rejects(() -> Timeline.seconds(1, 2));
    }
    private static JsonObject baseline() {
        var s = new JsonObject(); s.addProperty("actual_shader_sha256", ORIGINAL);
        s.addProperty("mod_present", true); s.addProperty("shaders_in_use", true); s.addProperty("fallback", false); s.addProperty("hide_gui", true);
        var o = new JsonObject(); o.addProperty("TAAU", "false"); o.addProperty("TAA", "true");
        // Deliberately modeled placeholders, never claimed to be Iris-loaded options.
        for (int i = 0; i < 364; i++) o.addProperty("modeled_option_" + i, "fixed");
        s.add("effective_shader_options", o);
        for (String f : List.of("terrain_configured", "terrain_confirmation", "terrain_active", "shadow_culling_configured", "shadow_culling_active", "shadow_lists_configured", "shadow_lists_active", "shadow_coherence_configured", "shadow_coherence_active")) s.addProperty(f, false);
        var lod = new JsonObject(); lod.addProperty("enabled", false); lod.addProperty("debug", false); s.add("lod_settings", lod);
        var semantic = new JsonObject(); semantic.add("lod", lod.deepCopy()); s.add("loaded_semantic_config", semantic);
        var cfg = new JsonObject(); cfg.addProperty("complete", true); s.add("configuration_files", cfg);
        s.addProperty("framebuffer_width", 2976); s.addProperty("framebuffer_height", 1672); s.addProperty("x", 1);
        return s;
    }
    private static void inputs() {
        var s = baseline(); validateOriginal(s); check(true);
        var resized = s.deepCopy(); resized.addProperty("framebuffer_width", 2232); resized.addProperty("framebuffer_height", 1254);
        check(fixedInputs(s).equals(fixedInputs(resized)));
        for (String field : List.of("x", "actual_shader_sha256", "effective_shader_options", "configuration_files", "loaded_semantic_config")) {
            var changed = resized.deepCopy(); changed.addProperty(field, "changed"); check(!fixedInputs(s).equals(fixedInputs(changed)));
        }
        var bad = s.deepCopy(); bad.getAsJsonObject("effective_shader_options").addProperty("TAAU", "true"); var taau = bad; rejects(() -> validateOriginal(taau));
        bad = s.deepCopy(); bad.getAsJsonObject("effective_shader_options").addProperty("TAA", "false"); var taaOff = bad; rejects(() -> validateOriginal(taaOff));
        bad = s.deepCopy(); bad.getAsJsonObject("effective_shader_options").remove("modeled_option_0"); var missing = bad; rejects(() -> validateOriginal(missing));
        bad = s.deepCopy(); bad.getAsJsonObject("lod_settings").addProperty("enabled", true); var enabled = bad; rejects(() -> validateOriginal(enabled));
        bad = s.deepCopy(); bad.addProperty("actual_shader_sha256", "candidate"); var candidate = bad; rejects(() -> validateOriginal(candidate));
    }
    private static void properties() {
        for (String p : List.of("gpuPassTiming", "gpuAttribution", "cpuLayerTiming")) System.setProperty("shaderlod.dev." + p, "false");
        System.setProperty("shaderlod.dev.gpuSampleInterval", "5"); requireProperties(); check(true);
        for (String o : OBSERVERS) {
            System.setProperty("shaderlod.dev." + o, "true"); rejects(PhysicalResolutionSession::requireProperties); System.clearProperty("shaderlod.dev." + o);
        }
        System.setProperty("shaderlod.dev.gpuSampleInterval", "1"); rejects(PhysicalResolutionSession::requireProperties); System.setProperty("shaderlod.dev.gpuSampleInterval", "5");
        System.setProperty("shaderlod.dev.initialWarmup", "29"); rejects(PhysicalResolutionSession::requireProperties); System.clearProperty("shaderlod.dev.initialWarmup");
    }
    private static void timing() {
        var gpu = new ArrayList<GpuTimers.Sample>();
        for (int i = 0; i < 60; i++) gpu.add(new GpuTimers.Sample("game-render", 12, i * 5, 2));
        var cpu = List.of(new CpuTimers.Sample("run-tick", 15, 8.0, 1, 2));
        validateTiming(gpu, cpu, 300); check(true);
        rejects(() -> validateTiming(gpu.subList(0, 59), cpu, 300));
        rejects(() -> validateTiming(gpu, cpu, 99));
        rejects(() -> validateTiming(gpu, List.of(new CpuTimers.Sample("terrain-detailed", 1, null, 1, 2)), 300));
        gpu.set(0, new GpuTimers.Sample("game-render", 12, 0, 3)); rejects(() -> validateTiming(gpu, cpu, 300));
        System.setProperty("shaderlod.dev.gpuTiming", "false"); validateTiming(List.of(), cpu, 300); check(true);
        rejects(() -> validateTiming(gpu, cpu, 300)); System.clearProperty("shaderlod.dev.gpuTiming");
    }
    private static void check(boolean pass) { checks++; if (!pass) throw new AssertionError("check " + checks); }
    private static void rejects(Runnable action) { checks++; try { action.run(); } catch (IllegalStateException expected) { return; } throw new AssertionError("Expected rejection " + checks); }
}
