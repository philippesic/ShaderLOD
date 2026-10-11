import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('qualification', ROOT / 'scripts/qualify_renderer_bracket.py')
q = importlib.util.module_from_spec(spec)
spec.loader.exec_module(q)


class QualificationTests(unittest.TestCase):
    def setUp(self):
        self.report = json.loads((ROOT / 'tests/fixtures/renderer-rate-12111-r2.json').read_text())

    def newer(self):
        report = copy.deepcopy(self.report)
        for arm in ('original', 'on', 'off'):
            s = report[arm]
            s.update(nativeWindowFocused=s.pop('focused'), windowIconified=s.pop('iconified'),
                     rejectedMeasuredFrames=s.pop('invalidMeasuredFrames'),
                     minimumConfiguredFramerateLimit=260, maximumConfiguredFramerateLimit=260,
                     rateObservedFrames=s['renderFrames'])
        return report

    def test_actual_r2_keeps_opposite_controls_and_stage_denominator(self):
        result = q.qualify(self.report, '1.21.11')
        self.assertTrue(result['measurementValid'])
        self.assertFalse(result['causalSpeedClaimAccepted'])
        self.assertAlmostEqual(result['comparisons']['returnFrameTimeDriftPercent'], 117.3264, places=3)
        self.assertLess(result['comparisons']['throughputVsInitialPercent'], 0)
        self.assertGreater(result['comparisons']['throughputVsReturnPercent'], 0)
        self.assertIn('Opposite', result['attribution'])
        self.assertEqual(result['workObservation']['removedCommands'], 111)
        self.assertEqual(result['workObservation']['inputCommands'], 90057)
        self.assertIn('submitted work unknown', result['workObservation']['scope'])
        self.assertFalse(result['workObservation']['totalFrameSpeedupBoundProven'])

    def test_virtual_time_afk_transition_retains_every_completed_frame(self):
        # Actual pinned 1.21.11 SHORT_AFK boundary from r1 reviewed bytecode:
        # strictly greater than60,000ms since input, min(configured,30).
        # These are test-only virtual observed facts, never runtime admission.
        times = [59999, 60000, 60001, 60002]
        caps = [260 if t <= 60000 else min(260, 30) for t in times]
        policies = ['AFK'] * len(times)  # actual original profile policy; repaired admission rejects it even before the threshold
        s = self.report['on']
        s.update(renderFrames=len(times), elapsedSeconds=.1,
                 minimumEffectiveFramerateLimit=min(caps), maximumEffectiveFramerateLimit=max(caps),
                 invalidMeasuredFrames=sum(policy != 'MINIMIZED' or cap != 260 for policy, cap in zip(policies, caps)))
        # A recovered MINIMIZED/260 final snapshot must not hide prior actual AFK-policy frames.
        self.assertEqual(s['effectiveFramerateLimit'], 260)
        result = q.qualify(self.report, '1.21.11')
        self.assertFalse(result['measurementValid'])
        self.assertEqual(result['arms']['on']['renderFrames'], 4)
        self.assertEqual(result['arms']['on']['meanElapsedFrameMs'], 25)
        self.assertFalse(result['causalSpeedClaimAccepted'])

    def test_each_missing_required_observation_fails(self):
        for key in ('effectiveFramerateLimit', 'framerateThrottleReason', 'inactivityFpsLimit',
                    'minimumEffectiveFramerateLimit', 'maximumEffectiveFramerateLimit',
                    'focused', 'iconified', 'invalidMeasuredFrames'):
            with self.subTest(key=key):
                report = copy.deepcopy(self.report)
                del report['on'][key]
                self.assertFalse(q.qualify(report, '1.21.11')['measurementValid'])

    def test_262_transient_equal_caps_or_unavailable_samples_fail_whole_bracket(self):
        report = self.newer()
        self.assertTrue(q.qualify(report, '26.2')['measurementValid'])
        report['on']['minimumConfiguredFramerateLimit'] = 120
        report['on']['minimumEffectiveFramerateLimit'] = 120
        self.assertFalse(q.qualify(report, '26.2')['measurementValid'])
        report = self.newer()
        report['on']['rateObservedFrames'] -= 1
        self.assertFalse(q.qualify(report, '26.2')['measurementValid'])

    def test_focus_iconification_and_throttling_fail(self):
        for key, value in [('focused', False), ('iconified', True), ('framerateThrottleReason', 'SHORT_AFK'),
                           ('invalidMeasuredFrames', 1), ('vsync', True)]:
            report = copy.deepcopy(self.report)
            report['on'][key] = value
            self.assertFalse(q.qualify(report, '1.21.11')['measurementValid'])

    def test_1211_never_inherits_newer_tracker_semantics(self):
        result = q.qualify(self.report, '1.21.1')
        self.assertFalse(result['measurementValid'])
        self.assertIn('legacy Minecraft limiter', result['errors'][0])

    def test_zero_negative_boolean_and_nonfinite_denominators_fail(self):
        for key, value in [('renderFrames', 0), ('renderFrames', True), ('elapsedSeconds', -1),
                           ('elapsedSeconds', float('nan')), ('elapsedSeconds', float('inf'))]:
            report = copy.deepcopy(self.report)
            report['on'][key] = value
            self.assertFalse(q.qualify(report, '1.21.11')['measurementValid'])

    def test_no_numeric_drift_gate_and_no_unmeasured_headroom_claim(self):
        report = copy.deepcopy(self.report)
        report['off']['elapsedSeconds'] = report['original']['elapsedSeconds'] * 20
        del report['on']['stageInputCommands']
        result = q.qualify(report, '1.21.11')
        self.assertTrue(result['measurementValid'])
        self.assertFalse(result['causalSpeedClaimAccepted'])
        self.assertFalse(result['workObservation']['totalFrameSpeedupBoundProven'])


if __name__ == '__main__':
    unittest.main()
