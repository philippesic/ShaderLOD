"""Exact source/support/census predicates. No GLSL compilation or GPU evidence."""
import importlib.util
from array import array
import json
import math
from pathlib import Path
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('bsl_tiles', ROOT / 'scripts/build_bsl_shadow_tiles_r1.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class BslShadowTilesTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        with zipfile.ZipFile(m.BASE) as archive:
            cls.files = {n: archive.read(n) for n in archive.namelist() if not n.endswith('/')}

    def test_pinned_source_and_minimal_cue_scope(self):
        result = m.apply_cue(self.files)
        self.assertEqual({n for n in self.files if self.files[n] != result[n]}, {m.TERRAIN, m.SHADOWS, m.FINAL})
        self.assertEqual(set(result) - set(self.files), {m.HELPER})
        self.assertEqual(result['shaders/shaders.properties'], self.files['shaders/shaders.properties'])
        self.assertIn(b'BSL Shaders v10 Series by Capt Tatsu', result[m.TERRAIN])
        self.assertEqual(self.files[m.TERRAIN].count(b'GetLighting('), 1)
        for n in ('shaders/program/shadow.glsl', 'shaders/program/shadowcomp.glsl',
                  'shaders/program/dh_terrain.glsl', 'shaders/program/gbuffers_water.glsl'):
            self.assertEqual(self.files[n], result[n])

    def test_shadow_sampling_and_implicit_derivative_body_retained(self):
        result = m.apply_cue(self.files)
        addition = (b'    #ifdef SHADERLOD_BSL_TILES_SCOPE\r\n'
                    b'    shaderlodBslTilesObserve(shadowPos, offset, subsurface);\r\n    #endif\r\n')
        self.assertEqual(result[m.SHADOWS].replace(addition, b'', 1), self.files[m.SHADOWS])
        helper = result[m.HELPER].decode()
        self.assertNotIn('texture2D', helper)
        self.assertNotIn('dFdx', helper)
        self.assertIn('shaderlodBridgeVersion == 2', helper)
        self.assertIn('shaderlodPreset == 0 || shaderlodPreset == 1 || shaderlodPreset == 2', helper)
        self.assertNotIn('shaderlodPreset == 3', helper)

    def test_changed_upstream_fails_closed(self):
        files = dict(self.files)
        files[m.SHADOWS] += b'\n'
        with self.assertRaisesRegex(ValueError, 'Changed pinned'):
            m.apply_cue(files)

    def test_all_nine_bilinear_supports_inside_queried_full_tiles(self):
        for ix in range(33):
            for iy in range(33):
                x, y = 8 + ix / 8, 8 + iy / 8
                tiles = m.support_tiles(x/32, y/32, 1/32, 32, 32)
                self.assertLessEqual(len(tiles), 4)
                for ox, oy in m.OFFSETS:
                    bx, by = math.floor(x + ox - .5), math.floor(y + oy - .5)
                    for dx in (0, 1):
                        for dy in (0, 1):
                            self.assertIn(((bx+dx)//4, (by+dy)//4), tiles)

    def test_interior_blocker_not_four_corner_probe(self):
        depth = [1.] * 32**2
        tiles = m.support_tiles(10/32, 10/32, 1/32, 32, 32)
        self.assertTrue(m.all_lit(depth, 32, tiles, .5, 0, .00001))
        tx, ty = next(iter(tiles))
        depth[(ty*4+1)*32 + tx*4+1] = .4
        self.assertFalse(m.all_lit(depth, 32, tiles, .5, 0, .00001))

    def test_ratio_two_border_and_error_are_not_fixed_four_proofs(self):
        self.assertEqual(len(m.support_tiles(5.5/32, 5.5/32, 2/32, 32, 32)), 9)
        self.assertIsNone(m.support_tiles(0, .5, 1/32, 32, 32))
        base = m.support_tiles(10/32, 10/32, 1/32, 32, 32)
        expanded = m.support_tiles(10/32, 10/32, 1/32, 32, 32, 2/32)
        self.assertTrue(base <= expanded)
        self.assertFalse(m.all_lit([.5]*32**2, 32, base, .5, 0, 0))
        self.assertFalse(m.all_lit([.5001]*32**2, 32, base, .5, .0002, 0))

    def test_full_producer_cost_and_invocation_denominator(self):
        screen = m.expression_screen(2048, 2048, 1920*1200, 1920*1200, 1920*1200, 4*1920*1200)
        self.assertTrue(screen['optimistic_expression_screen_pass'])
        self.assertEqual(screen['build_raw_fetch_expressions'], 4194304)
        self.assertEqual(screen['useful_r32f_bytes'], 1048576)
        self.assertEqual(screen['iris_full_r32f_bytes_per_image'], 16777216)
        low = m.expression_screen(2048, 2048, 1920*1200, 100000, 100000, 400000)
        self.assertFalse(low['optimistic_expression_screen_pass'])
        overdraw = m.expression_screen(2048, 2048, 100, 200, 200, 800)
        self.assertEqual(overdraw['p_invocations_per_output_pixel'], 2)
        self.assertFalse(screen['performance_claim'])

    def test_no_output_escape_or_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, 'fresh ZIP'):
                m.build(Path(directory) / 'bad.zip')

    def test_rgba_coordinates_retain_native_sentinels_and_no_pixel_multiplicity(self):
        raw = array('f', [.25, .5, .75, 1/2048, -1, -1, -1, -1]).tobytes()
        records = list(m.coordinate_records(raw, (2, 1)))
        self.assertEqual(records[0]['shadow_position'], (.25, .5, .75))
        self.assertEqual(records[0]['multiplicity'], 1)
        self.assertTrue(records[0]['filtered_reached'])
        self.assertFalse(records[1]['filtered_reached'])
        with self.assertRaises(ValueError):
            list(m.coordinate_records(raw, (3, 1)))

    def test_snapshot_join_population_epoch_and_complete_cost(self):
        # Synthetic parser fixture, never recorded as a runtime observation.
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            raw = array('f', [.9] * 32**2).tobytes()
            (directory / 'depth.bin').write_bytes(raw)
            receipt = {'archive_sha256': '0'*64, 'original_archive_sha256': m.ARCHIVE_SHA, 'never_time': True}
            receipt_raw = m.canonical(receipt)
            (directory / 'cue.json').write_bytes(receipt_raw)
            epoch = {key: {'synthetic_fixture': True} for key in m.EPOCH_DOMAINS}
            snapshot = {'original_archive_sha256': m.ARCHIVE_SHA, 'iris_sha256': m.IRIS_SHA,
                'options_map_sha256': m.OPTIONS_MAP_SHA, 'capture_archive_sha256': '0'*64,
                'cue_receipt_file': 'cue.json', 'cue_receipt_sha256': m.digest(receipt_raw),
                'native_shadow_source_and_state_unchanged_verified': True,
                'population': 'visible-selected-terrain', 'producer_epoch': epoch,
                'depth_epoch': m.digest(m.canonical(epoch)), 'receivers_epoch': m.digest(m.canonical(epoch)),
                'epoch_complete': True, 'compare_func': 'LEQUAL', 'compare_mode': 'COMPARE_REF_TO_TEXTURE',
                'filter': 'LINEAR', 'depth_level': 0, 'sampler_bounds_verified': True,
                'raw_readback_verified': True, 'physical_depth_extent': [32, 32],
                'depth_f32le_file': 'depth.bin', 'depth_sha256': m.digest(raw),
                'declared_resolution': 2048, 'uv_error_bound': 0, 'reference_error_bound': 0,
                'compare_margin': .00001, 'output_extent': [100, 100],
                'records': [{'multiplicity': 10000, 'subsurface': 0, 'filtered_reached': True,
                    'offset': 1/2048, 'shadow_position': [.5, .5, .5]}]}
            path = directory / 'snapshot.json'
            path.write_bytes(m.canonical(snapshot))
            result = m.analyze(path)
            self.assertTrue(result['optimistic_expression_screen_pass'])
            self.assertFalse(result['whole_forward_expression_screen_pass'])
            self.assertEqual(result['eligible_invocations'], 10000)
            self.assertEqual(result['all_lit_invocations'], 10000)
            snapshot['receivers_epoch'] = 'later'
            path.write_bytes(m.canonical(snapshot))
            with self.assertRaisesRegex(ValueError, 'current shadow epoch'):
                m.analyze(path)


if __name__ == '__main__':
    unittest.main()
