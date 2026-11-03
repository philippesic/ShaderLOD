"""Spatial schema qualification, using actual Java/Gson record exports plus synthetic phases."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

from test_inprocess_control import fixture, terrain_fixture
from analyze_inprocess_control import (analyze_report as analyze_with_receipt, compare_sessions, canonical, ordered_digest,
    semantic_state, SPATIAL_METHOD, SPATIAL_ARCHIVES, SPATIAL_REQUIRED_OPTIONS, spatial_source_binding)

GSON_FIXTURE = Path(__file__).parent / 'fixtures/spatial-toggle-gson.json'
DIRECTORY_FIXTURE = Path(__file__).parent / 'fixtures/spatial-toggle-directories.json'
DIRECTORY_RECEIPT = json.loads(DIRECTORY_FIXTURE.read_text())['loaded_directory_identities']


def analyze_report(report, **kwargs):
    return analyze_with_receipt(report, expected_directory_artifacts=DIRECTORY_RECEIPT, **kwargs)


def spatial_fixture(pin=next(iter(SPATIAL_ARCHIVES))):
    report = fixture()
    report.update(benchmark_feature='spatial', method=SPATIAL_METHOD, selected_flag='lod.enabled', atmosphere_guard_absent=True)
    report['instrumentation_properties']['benchmarkFeature'] = 'spatial'
    report['loaded_mods'].update(minecraft='1.21.1', neoforge='21.1.253', iris='1.8.12-snapshot+mc1.21.1-local', sodium='0.6.13+mc1.21.1')
    report['loaded_artifacts_sha256'].update(copy.deepcopy(DIRECTORY_RECEIPT))
    report['workload-manifest.json'].update(adaptive_pack_sha256=pin, source_world_sha256='7' * 64)
    # Keep actual Gson object ordering and original Java-produced hashes for default pin.
    objects = json.loads(GSON_FIXTURE.read_text())
    window_template = terrain_fixture()['phases'][0]['windowState']
    for phase in report['phases']:
        phase['windowState'] = copy.deepcopy(window_template)
        for boundary in ('inputStart', 'inputEnd'):
            state = phase[boundary]
            state.update(copy.deepcopy(objects))
            state.update(actual_shader_sha256=pin, framebuffer_width=2976, framebuffer_height=1672,
                spatial_dh_present=False, spatial_camera_type='FIRST_PERSON', spatial_fov=70, control_sha256='6' * 64,
                **{'config/shaderlod-effects-client.toml_sha256': '4' * 64})
            state['spatial_window_state'] = copy.deepcopy(window_template['capture']['start']['state'])
            state['loaded_semantic_config']['effects'] = copy.deepcopy(state['spatial_effect_controls'])
            state['loaded_semantic_config']['rendering']['shadowFrustumCoherence'] = False
            state['shadow_coherence_configured'] = state['shadow_coherence_active'] = False
            state['effective_shader_options'] = {k: v[0] for k, v in SPATIAL_REQUIRED_OPTIONS.items()} | {'QUALITY': 'high'}
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            if pin != next(iter(SPATIAL_ARCHIVES)):
                q = state['spatial_source_qualification']
                q['source_binding'] = spatial_source_binding(pin)
                q['source_fingerprint'] = {'sourceSha256': pin, 'memberSha256': copy.deepcopy(q['source_binding']['memberSha256'])}
                state['spatial_source_qualification_sha256'] = ordered_digest(q)
        phase['gpu'] = [dict(phase['gpu'][0], frameId=phase['startFrame'] + index) for index in range(1, 61)]
    report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'spatial')
    return report


class SpatialInprocessTests(unittest.TestCase):
    def test_genuine_directory_receipt_matches_independently_derived_java_trees(self):
        fixture = json.loads(DIRECTORY_FIXTURE.read_text())
        self.assertEqual(sorted(record['files'] for record in DIRECTORY_RECEIPT.values()), [2, 2, 102, 147])
        for name, expected in DIRECTORY_RECEIPT.items():
            entries = fixture['tree_inputs'][name]
            self.assertEqual([entry['path'] for entry in entries], sorted(entry['path'] for entry in entries))
            encoded = ''.join(entry['path'] + '\0' + entry['sha256'] + '\n' for entry in entries).encode()
            self.assertEqual(hashlib.sha256(encoded).hexdigest(), expected['tree_sha256'])
            self.assertEqual(len(entries), expected['files'])
            self.assertEqual(sum(entry['bytes'] for entry in entries), expected['bytes'])
        self.assertEqual(analyze_report(spatial_fixture())['loaded_artifacts_sha256'], spatial_fixture()['loaded_artifacts_sha256'])

    def test_directory_schema_completeness_and_frozen_receipt_mutations_reject(self):
        name = next(iter(DIRECTORY_RECEIPT))
        for mutate in (lambda value: value.update(kind='unknown'), lambda value: value.update(complete=False),
                       lambda value: value.update(files=146), lambda value: value.update(files=True),
                       lambda value: value.update(bytes=value['bytes'] + 1),
                       lambda value: value.update(tree_sha256='0' * 64), lambda value: value.pop('tree_sha256'),
                       lambda value: value.update(limit='partial')):
            report = spatial_fixture(); mutate(report['loaded_artifacts_sha256'][name])
            with self.assertRaises(ValueError): analyze_report(report)
        with self.assertRaisesRegex(ValueError, 'external artifact receipt'): analyze_with_receipt(spatial_fixture())
        report = spatial_fixture(); report['loaded_artifacts_sha256'].pop(name)
        # ModList exports observed roots, not necessarily every separately frozen build directory.
        self.assertEqual(len(analyze_report(report)['loaded_artifacts_sha256']), 4)
        report = spatial_fixture(); report['loaded_artifacts_sha256']['unknown-directory'] = report['loaded_artifacts_sha256'].pop(name)
        with self.assertRaisesRegex(ValueError, 'frozen receipt'): analyze_report(report)
        changed_receipt = copy.deepcopy(DIRECTORY_RECEIPT); changed_receipt[name]['tree_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'frozen receipt'): analyze_with_receipt(spatial_fixture(), expected_directory_artifacts=changed_receipt)

    def test_actual_gson_object_hashes_and_both_exact_source_pins(self):
        objects = json.loads(GSON_FIXTURE.read_text())
        for field in ('spatial_effect_controls', 'spatial_adapter_binding', 'spatial_source_qualification'):
            self.assertEqual(ordered_digest(objects[field]), objects[field + '_sha256'])
        for pin in SPATIAL_ARCHIVES:
            result = analyze_report(spatial_fixture(pin))
            self.assertEqual(result['shader_sha256'], pin)
            self.assertEqual(len(result['adjacent_pairs']), 2)
            self.assertIn('OFF retains extra pass', result['spatial_diagnostic_limits'])
            self.assertIn('no original Photon 1.5x', result['spatial_diagnostic_limits'])
            self.assertIsNone(result['phases'][0]['uniform_observation'])

    def test_raw_stored_order_hash_not_sorted_mirror(self):
        report = spatial_fixture()
        state = report['phases'][0]['inputEnd']
        field = 'spatial_adapter_binding'
        state[field] = dict(reversed(list(state[field].items())))
        with self.assertRaisesRegex(ValueError, 'fingerprint'): analyze_report(report)
        state[field + '_sha256'] = hashlib.sha256(canonical(state[field]).encode()).hexdigest()
        with self.assertRaisesRegex(ValueError, 'fingerprint'): analyze_report(report)

    def test_no_legacy_boolean_effect_options_fabricated_or_old_guard_relaxed(self):
        report = spatial_fixture()
        self.assertNotIn('SHADERLOD_CLOUDS', report['phases'][0]['inputStart']['effective_shader_options'])
        analyze_report(report)
        report.update(benchmark_feature='effects')
        with self.assertRaises(ValueError): analyze_report(report)

    def test_source_declarations_observations_and_options_fail_even_rehashed(self):
        for target in ('source_binding', 'source_fingerprint'):
            for field, value in (('sourceSha256', '0' * 64), ('memberSha256', {})):
                report = spatial_fixture(); state = report['phases'][0]['inputEnd']
                state['spatial_source_qualification'][target][field] = value
                state['spatial_source_qualification_sha256'] = ordered_digest(state['spatial_source_qualification'])
                with self.assertRaises(ValueError): analyze_report(report)
        for option in SPATIAL_REQUIRED_OPTIONS:
            report = spatial_fixture(); state = report['phases'][0]['inputEnd']
            state['effective_shader_options'][option] = 'false' if option == 'SHADERLOD_SHARED_SPATIAL' else 'true'
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            with self.assertRaises(ValueError): analyze_report(report)

    def test_policy_or_availability_changes_fail_even_rehashed(self):
        for mutate in (lambda s: s['spatial_effect_controls']['OPAQUE_LIGHTING'].update(mode='FULL'),
                       lambda s: s['spatial_effect_controls']['OPAQUE_LIGHTING']['policy'].update(farWorkRatio=1),
                       lambda s: s['spatial_effect_controls']['OPAQUE_LIGHTING']['policy']['bands'].update(nearBlocks=64),
                       lambda s: s['spatial_effect_controls']['VOLUMETRICS'].update(mode='ADAPTIVE'),
                       lambda s: s['spatial_adapter_binding'].update(reason='UNVALIDATED_PACK'),
                       lambda s: s['spatial_adapter_binding'].update(operations=[])):
            report = spatial_fixture(); state = report['phases'][0]['inputEnd']; mutate(state)
            state['loaded_semantic_config']['effects'] = copy.deepcopy(state['spatial_effect_controls'])
            for field in ('spatial_effect_controls', 'spatial_adapter_binding'): state[field + '_sha256'] = ordered_digest(state[field])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_fixed_config_pose_window_and_observation_guards(self):
        for mutate in (lambda s: s.update(spatial_dh_present=True), lambda s: s.update(spatial_fov=71),
                       lambda s: s.update(yaw=90), lambda s: s.update(day_time=7001),
                       lambda s: s.update(**{'config/shaderlod-effects-client.toml_sha256': '5' * 64}),
                       lambda s: s['spatial_window_state'].update(focused='unknown'),
                       lambda s: s['spatial_window_state']['pacing'].update(vsync=True)):
            report = spatial_fixture(); mutate(report['phases'][0]['inputEnd'])
            with self.assertRaises(ValueError): analyze_report(report)
        for mutate in (lambda p: p.pop('windowState'),
                       lambda p: p['windowState']['capture']['focused'].update(unknown_observations=1),
                       lambda p: p['windowState']['capture'].update(transition_counts={'focused': 2})):
            report = spatial_fixture(); mutate(report['phases'][0])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_insufficient_mixed_duplicate_out_of_phase_epoch_streams_fail(self):
        for mutate in (lambda p: p['gpu'].pop(), lambda p: p['gpu'][0].update(milliseconds=0),
                       lambda p: p['gpu'][0].update(epoch=99), lambda p: p['gpu'][0].update(frameId=p['startFrame']),
                       lambda p: p['gpu'][0].update(scope='deferred'),
                       lambda p: p['gpu'][0].update(frameId=p['gpu'][1]['frameId'])):
            report = spatial_fixture(); mutate(report['phases'][0])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_explicit_mode_restore_observers_and_runtime_pins(self):
        for mutate in (lambda r: r.update(restored_initial_enabled=False), lambda r: r.update(atmosphere_guard_absent=False),
                       lambda r: r['instrumentation_properties'].update(workTrace='true'),
                       lambda r: r['loaded_mods'].update(iris='unknown'),
                       lambda r: r['loaded_mods'].update(distanthorizons='3.3.3')):
            report = spatial_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze_report(report)

    def test_same_archive_comparisons_only(self):
        a = analyze_report(spatial_fixture()); b = analyze_report(spatial_fixture())
        self.assertFalse(compare_sessions([a, b])['archive_source_delta'])
        other = analyze_report(spatial_fixture(list(SPATIAL_ARCHIVES)[1]))
        with self.assertRaisesRegex(ValueError, 'no cross-archive'): compare_sessions([a, other])


if __name__ == '__main__': unittest.main()
