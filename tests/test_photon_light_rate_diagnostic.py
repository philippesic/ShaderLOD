"""No-launch preparation and raw light-rate validity fixtures."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import prepare_photon_light_rate_diagnostic as prep


class LightRateRuntimePreparationTests(unittest.TestCase):
    def test_fresh_owned_plan_and_preservation(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp); output = root / 'run-workloads/light-rate-diagnostic-r1'; build = root / 'build/light-rate-runtime-r1'
            p = prep.plan(output, build, root)
            self.assertFalse(p['main_mod_presence_verified']); self.assertFalse(p['launch_authorized'])
            self.assertEqual(p['trace_extent'], [744, 418]); self.assertFalse(output.exists())
            with self.assertRaises(ValueError): prep.plan(root / 'tracked', build, root)
            with self.assertRaises(ValueError): prep.plan(output, root / 'build/other', root)
            output.mkdir(parents=True)
            with self.assertRaises(ValueError): prep.plan(output, build, root)

    def test_control_qualified_before_noon_derivation(self):
        control = dict(debug=False, pitch=3.735, preset='PERFORMANCE', time=12500, weather='clear',
            x=500.5, y=68.0, yaw=87.38, z=660.5)
        before = copy.deepcopy(control); after = prep.derive_control(control)
        self.assertEqual(control, before); self.assertEqual(after, control | {'time': 6000})
        for mutation in ({'time': 6000}, {'yaw': 90}, {'enabled': True}):
            with self.assertRaises(ValueError): prep.derive_control(control | mutation)

    def test_explicit_single_untimed_launch_command(self):
        cmd = prep.command(Path('/profile'), Path('/registration.json'), Path('/raw.json'))
        self.assertEqual(cmd[1], 'runClientLodOn')
        for key in prep.OFF: self.assertIn('-P' + key + '=false', cmd)
        for key in ('workTrace', 'workTraceOnly', 'measureWorkload', 'measureOnly'):
            self.assertIn('-P' + key + '=true', cmd)
        self.assertIn('-PworkTraceMetadata=/registration.json', cmd)
        self.assertEqual(cmd.count('runClientLodOn'), 1)
        self.assertNotIn('--run', Path(prep.__file__).read_text())

    def test_raw_positive_and_zero_reuse_distinct(self):
        d = prep.decode_raw([[10, 6, 2, 32], [6, 2, 3, 2], [1, 713001, 1, 0]], 6, 2)
        self.assertEqual(d, dict(N=10, Q=6, R=2, S=32, L=6, A=2, mode=3, path=2))
        self.assertEqual(prep.decode_raw([[10, 6, 0, 48], [6, 2, 3, 2], [1, 713001, 1, 0]], 6, 2)['R'], 0)
        self.assertEqual(prep.decode_raw([[0, 0, 0, 0], [6, 2, 0, 0], [1, 713001, 1, 0]], 6, 2)['path'], 0)
        self.assertEqual(prep.decode_raw([[10, 6, 0, 48], [6, 2, 1, 1], [1, 713001, 1, 0]], 6, 2)['mode'], 1)

    def test_raw_invalid_values_accounting_markers_and_modes(self):
        good = [[10, 6, 2, 32], [6, 2, 3, 2], [1, 713001, 1, 0]]
        changes = [(0, 0, -1), (0, 0, float('nan')), (0, 0, float('inf')), (0, 0, True),
            (0, 0, 1.5), (0, 0, 16777216), (0, 0, 5), (0, 2, 3), (0, 3, 33),
            (1, 0, 4), (1, 1, 2.5), (1, 2, 2), (1, 2, 4), (1, 3, 1), (1, 3, 3),
            (2, 0, 2), (2, 1, 0), (2, 2, 0), (2, 3, 1)]
        for page, channel, value in changes:
            with self.subTest(page=page, channel=channel, value=value):
                pages = copy.deepcopy(good); pages[page][channel] = value
                with self.assertRaises(ValueError): prep.decode_raw(pages, 6, 2)
        with self.assertRaises(ValueError): prep.decode_raw(good[:2], 6, 2)

    def trace_fixture(self, reused=2):
        qualified = dict(registration='/registration.json', registration_metadata={'exact': 'binding'})
        sample = [[10, 6, reused, (6-reused)*8], [6, 2, 3, 2], [1, 713001, 1, 0]]
        capture = dict(status='RAW_SAME_DRAW_CAPTURE', performanceEvidence=False, attachments=[0, 1, 2], schema=1,
            viewport=[0, 0, 744, 418], program=7, drawFramebuffer=11,
            schemaUniform=dict(type=5124, location=5, value=1), drawOrdinal=1, renderFrameOrdinal=100,
            attachmentImages=[dict(internalFormat=34836, width=744, height=418, object=i+1, level=0) for i in range(3)],
            samples=[dict(x=int((2*x+1)*744/16), y=int((2*y+1)*418/16), pages=copy.deepcopy(sample),
                decoded={'status': 'UNKNOWN', 'reason': 'LEGACY_LABELS_MUST_NOT_DEFINE_NEW_SEMANTICS'}) for y in range(8) for x in range(8)])
        trace = dict(kind='synchronous_raw_work_trace_diagnostic', evidenceVersion=1, performanceEvidence=False,
            captureWindowFinished=True, terminalStatus='CAPTURED', metadata=qualified['registration_metadata'],
            metadataPath=qualified['registration'], hook='FullScreenQuadRenderer.renderQuad()V TAIL', captures=[capture])
        return trace, qualified, dict(CLOUDS_CUMULUS_LIGHTING_STEPS='6', CLOUDS_CUMULUS_AMBIENT_STEPS='2')

    def test_raw_capture_ignores_legacy_decoder_and_preserves_limits(self):
        summary = prep.validate_trace(*self.trace_fixture())
        self.assertEqual(summary['counts']['R'], 128); self.assertEqual(summary['sampled_rays'], 64)
        self.assertTrue(summary['positive_reuse_observed']); self.assertFalse(summary['performanceEvidence'])
        zero = prep.validate_trace(*self.trace_fixture(0))
        self.assertFalse(zero['positive_reuse_observed']); self.assertIn('HOLD', zero['next_action'])
        self.assertNotIn('percentage', summary)

    def test_capture_extent_identity_restoration_grid_and_metadata_fail_closed(self):
        changes = [lambda t: t.update(captureWindowFinished=False),
            lambda t: t.update(metadata={'wrong': True}), lambda t: t.update(hook='unknown'),
            lambda t: t['captures'][0].update(restoreErrors=['error']),
            lambda t: t['captures'][0].update(status='UNKNOWN'),
            lambda t: t['captures'][0]['viewport'].__setitem__(2, 2976),
            lambda t: t['captures'][0]['attachmentImages'][0].update(internalFormat=34842),
            lambda t: t['captures'][0]['attachmentImages'][0].update(width=2976),
            lambda t: t['captures'][0]['attachmentImages'][0].update(object=2),
            lambda t: t['captures'][0]['schemaUniform'].update(value=0),
            lambda t: t['captures'][0]['samples'].pop(),
            lambda t: t['captures'][0]['samples'][0].update(x=0),
            lambda t: t['captures'][0]['samples'][0]['pages'][2].__setitem__(1, 0)]
        for mutate in changes:
            trace, qualified, effective = self.trace_fixture(); mutate(trace)
            with self.assertRaises(ValueError): prep.validate_trace(trace, qualified, effective)

    def test_both_actual_boundary_times_required_before_other_report_guards(self):
        inputs = dict(control_sha256='control', control=prep.derive_control(dict(debug=False, pitch=3.735,
            preset='PERFORMANCE', time=12500, weather='clear', x=500.5, y=68.0, yaw=87.38, z=660.5)), shader_pack='pack.zip')
        request = {'run_id': 'fresh'}
        observed = dict(day_time=12500, dimension='minecraft:overworld', rain=0, thunder=0)
        state = dict(request=request, arm=dict(profile_inputs=inputs, archive_sha256='archive',
            expected_effective_options={'options': True}, expected_effective_options_sha256='options'))
        report = dict(status='complete', request=request, diagnostic_only=True, performanceEvidence=False,
            modPresent=True, dhPresent=False, actual_shader_sha256='archive', effective_shader_options={'options': True},
            effective_shader_options_sha256='options', control_sha256='control', input_state_start=observed, input_state_end=observed)
        # Equal start/end sunset would pass generic unchanged-time checking, but fails this scene contract.
        with self.assertRaisesRegex(ValueError, 'Actual boundary time'): prep.validate_report(report, state)

    def test_missing_receipt_never_frozen_as_present(self):
        with tempfile.TemporaryDirectory() as tmp:
            marker = Path(tmp) / 'marker.json'
            with self.assertRaises(ValueError): prep.finalize(marker, Path(tmp) / 'absent.json', '0'*64)
        self.assertNotEqual(prep.artifacts.expected_mods(True), prep.artifacts.expected_mods(False))


if __name__ == '__main__': unittest.main()
