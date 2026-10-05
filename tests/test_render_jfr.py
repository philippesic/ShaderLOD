"""Tiny synthetic JDK JSON fixtures; no JVM, recording or GPU process is launched."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("render_jfr", ROOT / "scripts/analyze_render_jfr.py")
JFR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(JFR)


def event(kind, at, thread="Render thread", stack=(), **extra):
    values = {"startTime": at, "sampledThread" if kind in JFR.SAMPLE_TYPES else "eventThread": {"javaName": thread},
              "stackTrace": {"truncated": False, "frames": [
                  {"method": {"type": {"name": klass}, "name": method}} for klass, method in stack]}}
    values.update(extra)
    return {"type": kind, "values": values}


class RenderJfrTests(unittest.TestCase):
    def test_iso_units_and_invalid_duration(self):
        self.assertAlmostEqual(62.0004, JFR.duration_seconds("PT1M2.0004S"))
        self.assertAlmostEqual(.000000123, JFR.duration_seconds("PT0.000000123S"))
        self.assertEqual(JFR.timestamp("2026-10-04T15:00:00-07:00"), JFR.timestamp("2026-10-04T22:00:00Z"))
        for value in ("PT", "-PT1S", -1):
            with self.subTest(value=value), self.assertRaises(ValueError):
                JFR.duration_seconds(value)

    def test_thread_filter_streams_and_recursive_inclusive_counts(self):
        trace = [("org/lwjgl/opengl/GL32C", "nglMultiDrawElementsBaseVertex"),
                 ("net/minecraft/client/Minecraft", "runTick"), ("net/minecraft/client/Minecraft", "runTick")]
        events = [event("jdk.NativeMethodSample", 1, stack=trace, state="STATE_RUNNABLE"),
                  event("jdk.NativeMethodSample", 2, thread="Server thread", stack=trace),
                  event("jdk.ExecutionSample", 3, stack=[("net/minecraft/client/Minecraft", "tick")])]
        result = JFR.analyze(events, start=0, end=5)
        native = result["samples"]["jdk.NativeMethodSample"]
        self.assertEqual(native["render_samples"], 1)
        self.assertEqual(native["all_thread_counts"], {"Render thread": 1, "Server thread": 1})
        self.assertEqual(next(row["samples"] for row in native["inclusive_methods"] if row["name"].endswith("runTick")), 1)
        self.assertEqual(native["categories"][0]["name"], "native_opengl_draw_api")
        self.assertEqual(result["samples"]["jdk.ExecutionSample"]["render_samples"], 1)

    def test_wait_clips_and_splits_across_bins(self):
        events = [event("jdk.ThreadPark", 3, duration="PT5S"), event("jdk.ThreadSleep", 4, duration="PT2S"),
                  event("jdk.ThreadPark", 1, thread="Server thread", duration="PT10S")]
        result = JFR.analyze(events, bin_seconds=2, start=4, end=7)
        self.assertAlmostEqual(result["render_wait_union_ms"], 3000)
        self.assertAlmostEqual(result["render_wait_events"]["jdk.ThreadPark"]["sum_clipped_ms"], 3000)
        self.assertAlmostEqual(result["time_bins"][0]["render_wait_ms"], 4000)  # overlapping park and sleep: do not call bin sum a union.
        self.assertAlmostEqual(result["time_bins"][1]["render_wait_ms"], 1000)

    def test_gc_collection_elapsed_not_pause_and_compiler_not_render(self):
        events = [event("jdk.GCPhasePause", 1, thread="GC Thread", duration="PT0.02S", name="Pause"),
                  event("jdk.GarbageCollection", 0, thread="GC Thread", duration="PT3S", sumOfPauses="PT0.02S", longestPause="PT0.02S"),
                  event("jdk.Compilation", 1, thread="C2 CompilerThread0", duration="PT1S")]
        result = JFR.analyze(events)
        self.assertAlmostEqual(result["gc_pause_union_ms"], 20)
        self.assertAlmostEqual(result["garbage_collections"][0]["elapsed_ms"], 3000)
        self.assertEqual(result["slow_compilations_all_threads"][0]["thread"], "C2 CompilerThread0")

    def test_cpu_load_missing_stack_and_partial_capture(self):
        events = [event("jdk.CPUInformation", 0, hwThreads=16),
                  event("jdk.ThreadCPULoad", 2, user=.05, system=.01),
                  event("jdk.ExecutionSample", 3, stackTrace=None)]
        result = JFR.analyze({"recording": {"events": events}}, start=1, end=5)
        self.assertAlmostEqual(result["requested_window_coverage_fraction"], .5)
        self.assertAlmostEqual(result["render_thread_cpu_load"]["mean_machine_fraction"], .06)
        self.assertAlmostEqual(result["render_thread_cpu_load"]["core_fraction_if_hardware_count_equals_active_count"], .96)
        self.assertEqual(result["samples"]["jdk.ExecutionSample"]["missing_stacks"], 1)

    def test_no_overlap_and_invalid_json_rejected(self):
        with self.assertRaises(ValueError):
            JFR.analyze({})
        with self.assertRaisesRegex(ValueError, "does not overlap"):
            JFR.analyze([event("jdk.ExecutionSample", 1)], start=10, end=20)

    def test_macos_main_thread_detected_by_actual_render_stack(self):
        events = [event("jdk.NativeMethodSample", 1, thread="main", stack=[
                      ("org/lwjgl/opengl/GL32C", "nglMultiDrawElementsBaseVertex"),
                      ("net/minecraft/client/renderer/GameRenderer", "render")]),
                  event("jdk.ExecutionSample", 2, thread="main", stack=[("net/minecraft/client/Minecraft", "runTick")]),
                  event("jdk.ExecutionSample", 3, thread="Server thread", stack=[("net/minecraft/server/MinecraftServer", "tick")])]
        result = JFR.analyze(events)
        self.assertEqual(result["render_thread_pattern"], "^main$")
        self.assertEqual(result["samples"]["jdk.NativeMethodSample"]["render_samples"], 1)
        self.assertEqual(result["render_thread_selection"]["candidates"], {"main": 2})

    def test_generic_jni_swap_context_and_nonadditive_contexts(self):
        trace = [("org/lwjgl/system/JNI", "invokePV"), ("org/lwjgl/glfw/GLFW", "glfwSwapBuffers"),
                 ("net/minecraft/client/Minecraft", "runTick")]
        result = JFR.analyze([event("jdk.NativeMethodSample", 1, stack=trace),
                              event("jdk.ExecutionSample", 2, stack=[("net/minecraft/client/Minecraft", "runTick")])])
        native = result["samples"]["jdk.NativeMethodSample"]
        self.assertEqual(native["categories"][0]["name"], "native_window_swap_or_events")
        self.assertEqual(native["overlapping_stack_contexts"][0]["name"], "window_swap")


if __name__ == "__main__":
    unittest.main()
