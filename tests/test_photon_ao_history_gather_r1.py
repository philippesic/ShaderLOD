"""Pinned source and eligibility checks; no Java/client/driver or GPU claim."""
import copy
import math
from pathlib import Path
import random
import re
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import build_photon_ao_history_gather_r1 as candidate


def select_option(source, enabled):
    """Resolve only this private switch; leave native preprocessing untouched."""
    text = source.decode()
    text = text.replace('//#define ' + candidate.SWITCH + '\n', '')
    text = text.replace('#define ' + candidate.SWITCH + '\n', '')
    stack, output = [], []
    for line in text.splitlines(keepends=True):
        if line == '#ifdef ' + candidate.SWITCH + '\n':
            stack.append(enabled)
        elif line == '#else // ' + candidate.SWITCH + '\n':
            stack[-1] = not stack[-1]
        elif line == '#endif // ' + candidate.SWITCH + '\n':
            stack.pop()
        elif all(stack):
            output.append(line)
    assert not stack
    return ''.join(output).encode()


def extract_function(source, name):
    start = source.index('vec4 ' + name + '(')
    body = source.index('{', start)
    depth = 1
    for end in range(body + 1, len(source)):
        depth += (source[end] == '{') - (source[end] == '}')
        if not depth:
            return source[start:end + 1]
    raise AssertionError('Unclosed function')


class SourceTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        base = ROOT / candidate.BASE
        # Required local source; no silent skips on this owned source assignment.
        assert candidate.sha256(base.read_bytes()) == candidate.BASE_SHA256
        cls.files = candidate.archive_members(base)
        cls.manifest = candidate.load_manifest()
        cls.patched = candidate.patch_members(cls.files, cls.manifest, 'enabled')

    def test_disabled_option_restores_every_original_byte_and_license(self):
        for arm in candidate.ARMS:
            files = candidate.patch_members(self.files, self.manifest, arm)
            for name, original in self.files.items():
                effective = select_option(files[name], False) if name in candidate.MEMBERS else files[name]
                self.assertEqual(original, effective, (arm, name))
            self.assertEqual(self.files['LICENSE'], files['LICENSE'])
            self.assertEqual(set(files) - set(self.files), set() if arm == 'original' else {candidate.HELPER})

    def test_only_ao_and_settings_modified_all_consumers_preserved(self):
        changed = sorted(n for n in self.files if self.patched[n] != self.files[n])
        self.assertEqual(sorted(candidate.MEMBERS), changed)
        for n in self.manifest['source_dependencies']:
            self.assertEqual(self.files[n], self.patched[n])

    def test_fresh_ao_and_native_blend_write_contract_unchanged(self):
        original = self.files[candidate.AO].decode()
        effective = select_option(self.patched[candidate.AO], True).decode()
        for start, stop in [('    // Calculate AO\n', '    // Temporal accumulation\n'),
                            ('    if (clamp01(previous_screen_pos.xy) == previous_screen_pos.xy) {\n', None)]:
            # Native final accumulation occurs after candidate gathers.
            segment = original[original.index(start):original.index(stop) if stop else None]
            self.assertTrue(effective.endswith(segment) if stop is None else segment in effective)
        self.assertNotIn('shaderlodHistoryReset', effective)

    def test_helper_only_changes_function_name_and_explicit_lod(self):
        native = extract_function(self.files['shaders/include/utility/bicubic.glsl'].decode(), 'catmull_rom_filter_fast')
        helper = extract_function(self.patched[candidate.HELPER].decode(), 'shaderlod_ao_history_filter_lod0')
        self.assertEqual(5, helper.count('textureLod('))
        self.assertNotIn('texture(', helper)
        restored = helper.replace('shaderlod_ao_history_filter_lod0(', 'catmull_rom_filter_fast(', 1)
        restored = re.sub(r'textureLod\(sampler, (vec2\([^\n]+?\)), 0\.0\)', r'texture(sampler, \1)', restored)
        self.assertEqual(native, restored)
        self.assertIn('colortex6, previous_screen_pos.xy, 0.65)', self.patched[candidate.AO].decode())

    def test_base_only_linear_storage_no_c6_c14_mips_in_any_original_source(self):
        for name, data in self.files.items():
            if name.endswith(('.glsl', '.fsh', '.vsh', '.properties')):
                self.assertNotRegex(data.decode(), r'colortex(?:6|14)MipmapEnabled\s*=\s*true', name)
        buffers = self.files['shaders/include/buffers.glsl'].decode()
        self.assertRegex(buffers, r'colortex6Format\s*= RGBA16;')
        self.assertRegex(buffers, r'colortex14Format\s*= RG16F;')
        properties = self.files['shaders/shaders.properties'].decode()
        self.assertIn('size.buffer.colortex6 = 0.5 0.5', properties)
        self.assertIn('size.buffer.colortex14 = 0.5 0.5', properties)
        self.assertIn('flip.composite1.colortex6 = true', properties)

    def test_six_gathers_inside_eligibility_native_fallback_and_bridge_quality(self):
        source = select_option(self.patched[candidate.AO], True).decode()
        active = source[source.index('    if (shaderlod_ao_history_gather_active()) {'):source.index('    } else {\n        // Frame-uniform fallback')]
        eligibility = active.index('if (clamp01(previous_screen_pos.xy) == previous_screen_pos.xy)')
        self.assertLess(eligibility, active.index('shaderlod_ao_history_filter_lod0('))
        self.assertLess(eligibility, active.index('textureLod('))
        self.assertNotIn('texture(', active)
        helper = self.patched[candidate.HELPER].decode()
        for gate in ['shaderlodBridgeVersion == 2', 'shaderlodEnabled == 1', 'shaderlodPreset >= 0 && shaderlodPreset <= 2']:
            self.assertIn(gate, helper)
        self.assertNotIn('dFdx', helper)
        self.assertNotIn('dFdy', helper)
        self.assertNotIn('shaderlodNear', helper)

    def test_every_pin_manifest_helper_and_unknown_arm_fail_closed(self):
        for name in (*candidate.MEMBERS, *self.manifest['source_dependencies'], 'LICENSE'):
            with self.subTest(member=name), self.assertRaises(ValueError):
                candidate.patch_members(self.files | {name:self.files[name] + b'\n'}, self.manifest, 'enabled')
        altered = copy.deepcopy(self.manifest)
        altered['native_contract']['sharpness'] = 0.5
        with self.assertRaises(ValueError):
            candidate.patch_members(self.files, altered, 'enabled')
        with self.assertRaises(ValueError):
            candidate.patch_members(self.files, self.manifest, 'unknown')
        with self.assertRaises(ValueError):
            candidate.patch_members(self.files | {candidate.HELPER:b'conflict'}, self.manifest, 'enabled')
        with self.assertRaises(ValueError):
            candidate.build(output=ROOT / 'not-authorized.zip')


def eligible(uv):
    # Native equality with clamp01: boundaries inclusive, NaN is unequal.
    return all(math.isfinite(v) and 0.0 <= v <= 1.0 for v in uv)


def output_contract(uv, active, gather):
    history = gather() if (not active or eligible(uv)) else None
    # The native out-of-bounds arm never reads either gathered value.
    return ('native-accumulation', history) if eligible(uv) else ('fresh-ao', (1.0, 0.0, 0.5, 0.5), (0.0, 0.0))


class EligibilityFixtures(unittest.TestCase):
    def test_inclusive_boundaries_poisoned_dead_gathers_and_unknown_fallback(self):
        for uv in [(0.,0.), (1.,1.), (.5,.5), (-.01,.5), (1.01,.5), (float('nan'),.5), (float('inf'),.5)]:
            calls = []
            def gather():
                calls.append(1)
                return (float('nan'),) * 6
            native = output_contract(uv, False, gather)
            calls.clear()
            optimized = output_contract(uv, True, gather)
            self.assertEqual(1 if eligible(uv) else 0, len(calls))
            if not eligible(uv):
                self.assertEqual(native, optimized)
            else:
                self.assertEqual(native[0], optimized[0])

    def test_random_eligibility_fraction_is_not_a_ray_reuse_certificate(self):
        rng = random.Random(50)
        positions = [(rng.uniform(-.2,1.2), rng.uniform(-.2,1.2)) for _ in range(1000)]
        for uv in positions:
            self.assertEqual(output_contract(uv, False, lambda:(.3,.2,.4,.8)),
                             output_contract(uv, True, lambda:(.3,.2,.4,.8)))
        # Receiver depth/normal, camera and age can agree while a neighboring
        # sample moves into the AO radius: horizon changes without a receiver-key change.
        receiver_key = (2.0, (0.,0.,1.), 10.0)
        previous_key = receiver_key
        self.assertEqual(receiver_key, previous_key)
        previous_horizon = -1.0  # native empty sample sentinel
        moved_offset = (0.2, 0., 0.2)
        current_cos = moved_offset[2] / math.sqrt(sum(v*v for v in moved_offset))
        self.assertNotEqual(previous_horizon, current_cos)


if __name__ == '__main__':
    unittest.main()
