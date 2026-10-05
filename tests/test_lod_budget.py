import importlib.util
import math
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location(
    "lod_budget", Path(__file__).resolve().parents[1] / "scripts/analyze_lod_budget.py")
budget = importlib.util.module_from_spec(spec)
spec.loader.exec_module(budget)


class LodBudgetTest(unittest.TestCase):
    def test_fps_and_frame_time_are_equivalent(self):
        fps = budget.target_gap("fps", 100, 110)
        frame = budget.target_gap("total-frame-ms", 10, 1000 / 110)
        self.assertAlmostEqual(fps["target_ms"], 20 / 3)
        self.assertAlmostEqual(fps["remaining_time_reduction_percent"], 100 * (1 - 1.1 / 1.5))
        self.assertAlmostEqual(fps["current_throughput_uplift_percent"], 10)
        self.assertAlmostEqual(frame["remaining_time_ms"], fps["remaining_time_ms"])

    def test_gpu_does_not_export_fps(self):
        result = budget.target_gap("gpu-ms", 12.522953172222222, 13.369340746312684)
        self.assertIsNone(result["target_fps"])
        self.assertLess(result["current_time_reduction_percent"], 0)
        self.assertAlmostEqual(result["remaining_time_ms"], 5.020705298164536)

    def test_target_already_exceeded(self):
        result = budget.target_gap("total-frame-ms", 12, 7)
        self.assertTrue(result["target_met_arithmetically"])
        self.assertEqual(result["remaining_time_ms"], 0)

    def test_amdahl_insufficient_budget(self):
        bound = budget.amdahl_bound(0.2, math.inf)
        self.assertAlmostEqual(bound["predicted_speedup"], 1.25)
        self.assertFalse(bound["target_possible_under_assumptions"])
        self.assertAlmostEqual(bound["required_affected_share"], 1 / 3)

    def test_finite_effect_speedup_needs_larger_share(self):
        bound = budget.amdahl_bound(2 / 3, 2)
        self.assertAlmostEqual(bound["predicted_speedup"], 1.5)
        self.assertTrue(bound["target_possible_under_assumptions"])
        self.assertIsNone(budget.amdahl_bound(1, 1)["required_affected_share"])

    def test_complete_removal_has_explicit_unbounded_result(self):
        bound = budget.amdahl_bound(1, math.inf)
        self.assertTrue(bound["unbounded_speedup"])
        self.assertIsNone(bound["predicted_speedup"])

    def test_invalid_inputs_reject(self):
        for bad in (0, -1, math.nan, math.inf):
            with self.assertRaises(ValueError):
                budget.target_gap("fps", bad)
        for bad in (-1, 1.1, math.nan, math.inf):
            with self.assertRaises(ValueError):
                budget.amdahl_bound(bad, 2)
        with self.assertRaises(ValueError):
            budget.amdahl_bound(0.5, math.nan)
        with self.assertRaises(ValueError):
            budget.target_gap("cpu-thread-ms", 8)


if __name__ == "__main__":
    unittest.main()
