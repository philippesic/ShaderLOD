"""Tiny parser/provenance/explicit-anchor fixtures; these do not launch Java or a game."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('frustum_replay', ROOT / 'scripts/replay_frustum_coherence.py')
replay = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(replay)


def bits(value):
    return struct.unpack('>i', struct.pack('>f', value))[0]


def trace_fixture(source_sha='a' * 64):
    return {'kind': 'BASE', 'frustumClass': 'net.irisshaders.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum',
            'frustumIdentity': 7, 'renderFrame': 100, 'prepareOrPlaneGeneration': 9,
            'planeCount': 1, 'contextAvailable': True, 'contextAvailability': 'fixture',
            'sequenceEnded': True, 'endReason': 'next frame', 'totalObservedQueries': 2,
            'sequenceBoundaryObserved': True, 'proofCacheStateCaptured': False,
            'recordedQueries': 2, 'truncated': False, 'complete': True,
            'originalUnavailable': 0, 'revisitedAfterClose': False,
            'context': {'available': True, 'ownerAssociationVerified': True, 'frustumIdentity': 7,
                        'helperSourceSha256': source_sha, 'planeCount': 1, 'fma': True,
                        'rawDistanceLimitBits': 0x4060000000000000, 'distanceLimitKind': 'finite',
                        'distanceLimitBitsHex': '4060000000000000',
                        'rawPlaneBits': [[bits(1), bits(0), bits(0), bits(1000)]]},
            'queries': [{'index': 0, 'rawBoundsBits': [bits(1), bits(2), bits(3), bits(19), bits(20), bits(21)], 'actual': True, 'original': True},
                        {'index': 1, 'rawBoundsBits': [bits(-10), bits(-2), bits(-3), bits(8), bits(16), bits(15)], 'actual': True, 'original': True}]}


class FrustumReplayTest(unittest.TestCase):
    def test_signed_raw_bits_preserve_negative_zero_nan_and_negative_coordinates(self):
        self.assertEqual(replay.raw_bits(-2147483648, 32), 0x80000000)
        self.assertEqual(replay.raw_bits(-1, 32), 0xffffffff)
        self.assertEqual(replay.float_kind(0x7f800001, 32), 'nan')
        for bad in (True, 1.5, 1 << 32, -(1 << 31) - 1):
            with self.assertRaises(ValueError):
                replay.raw_bits(bad, 32)

    def test_complete_trace_and_report_wrapper_are_valid(self):
        trace = trace_fixture()
        parsed = replay.validate_trace({'shadow_coherence_oracle': {'replayTrace': trace}}, 'a' * 64)
        self.assertEqual(parsed['provenance']['scope'], 'complete first same-frame/frustum sequence')
        self.assertEqual(parsed['count'], 1)
        self.assertEqual(len(parsed['queries']), 2)
        trace['originalUnavailable'] = False
        self.assertEqual(replay.validate_trace(trace, 'a' * 64)['count'], 1)
        trace['originalUnavailable'] = True
        with self.assertRaises(ValueError):
            replay.validate_trace(trace, 'a' * 64)

    def test_unknown_owner_source_identity_fma_and_reference_errors_are_rejected(self):
        for mutate in (lambda t: t.update(contextAvailable=False),
                       lambda t: t['context'].update(ownerAssociationVerified=False),
                       lambda t: t['context'].update(frustumIdentity=8),
                       lambda t: t['context'].update(helperSourceSha256='b' * 64),
                       lambda t: t['context'].update(fma=None),
                       lambda t: t.update(originalUnavailable=1),
                       lambda t: t.update(revisitedAfterClose=True),
                       lambda t: t.update(kind='REVERSED')):
            trace = trace_fixture()
            mutate(trace)
            with self.assertRaises(ValueError):
                replay.validate_trace(trace, 'a' * 64)

    def test_partial_prefix_is_explicit_and_unmarked_truncation_fails(self):
        trace = trace_fixture()
        trace.update(totalObservedQueries=10, truncated=True, complete=False)
        parsed = replay.validate_trace(trace, 'a' * 64)
        self.assertTrue(parsed['provenance']['truncated'])
        self.assertEqual(parsed['provenance']['scope'], 'partial recorded prefix only')
        trace['truncated'] = False
        with self.assertRaises(ValueError):
            replay.validate_trace(trace, 'a' * 64)
        trace = trace_fixture()
        trace.update(sequenceBoundaryObserved=False, complete=False)
        self.assertFalse(replay.validate_trace(trace, 'a' * 64)['provenance']['complete'])
        trace['proofCacheStateCaptured'] = True
        with self.assertRaises(ValueError):
            replay.validate_trace(trace, 'a' * 64)

    def test_nonfinite_values_are_lossless_and_reported(self):
        trace = trace_fixture()
        trace['queries'][0]['rawBoundsBits'][0] = 0x7f800001
        trace['context'].update(rawDistanceLimitBits=0x7ff0000000000000,
                                distanceLimitBitsHex='7ff0000000000000', distanceLimitKind='positive_infinity')
        parsed = replay.validate_trace(trace, 'a' * 64)
        self.assertEqual(parsed['queries'][0][0][0], 0x7f800001)
        self.assertEqual(parsed['provenance']['nonfinite_query_endpoints'], 1)
        self.assertEqual(parsed['provenance']['distance_kind'], 'positive_infinity')

    def test_order_count_and_distance_metadata_inconsistency_fail(self):
        for mutate in (lambda t: t['queries'][1].update(index=3),
                       lambda t: t.update(recordedQueries=3),
                       lambda t: t['context'].update(distanceLimitKind='nan'),
                       lambda t: t['context'].update(rawPlaneBits=[])):
            trace = trace_fixture()
            mutate(trace)
            with self.assertRaises(ValueError):
                replay.validate_trace(trace, 'a' * 64)

    def test_variant_matrix_changes_only_four_explicit_anchors(self):
        source = '\n'.join(replay.ANCHORS) + '\n// original math body\n'
        variant, changes = replay.variant_source(source, 16, 48, 512)
        self.assertEqual(len(changes), 4)
        self.assertIn('// original math body', variant)
        self.assertIn('SLOTS = 512', variant)
        baseline, changes = replay.variant_source(source, 64, 96, 64)
        self.assertEqual(source, baseline)
        self.assertFalse(changes)
        with self.assertRaises(ValueError):
            replay.variant_source(source.replace(replay.ANCHORS[0], ''), 16, 48, 512)
        with self.assertRaises(ValueError):
            replay.variant_source(source, 8, 40, 512)

    def test_prepare_generates_original_helper_variants_and_binary_without_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            helper = root / 'helper.java'
            helper.write_text('\n'.join(replay.ANCHORS))
            diagnostics = root / 'diagnostics.java'
            diagnostics.write_text('// actual diagnostics fixture')
            trace = trace_fixture(replay.digest(helper.read_bytes()))
            path = root / 'trace.json'
            import json
            path.write_text(json.dumps(trace))
            destination = root / 'generated'
            manifest = replay.prepare(path, destination, helper, diagnostics)
            self.assertEqual(len(manifest['variants']), 9)
            self.assertIn('prepared only', manifest['execution'])
            self.assertTrue((destination / 'trace.bin').read_bytes().startswith(b'FRCO1'))
            self.assertFalse((destination / manifest['variants'][0]['name'] / 'classes').exists())
            with self.assertRaises(ValueError):
                replay.prepare(path, destination, helper, diagnostics)


if __name__ == '__main__':
    unittest.main()
