"""Volume boundary qualification: genuine Java/Gson records and synthetic correlated phases."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

from test_inprocess_control import fixture
from test_spatial_inprocess_control import spatial_fixture, DIRECTORY_RECEIPT
from analyze_inprocess_control import (analyze_report, canonical, ordered_digest, semantic_state,
    VOLUME_METHOD, VOLUME_ARCHIVES, VOLUME_REQUIRED_OPTIONS, volume_source_binding, compare_sessions,
    validate_volume_artifacts)

FIXTURES = Path(__file__).parent / 'fixtures'
OBJECTS = json.loads((FIXTURES / 'volume-toggle-gson.json').read_text())
WINDOW = json.loads((FIXTURES / 'volume-toggle-window-gson.json').read_text())
FIELDS = ('volume_effect_controls', 'volume_adapter_binding', 'volume_source_qualification')


def volume_fixture(pin=next(iter(VOLUME_ARCHIVES))):
    report = fixture()
    report.update(benchmark_feature='volume', method=VOLUME_METHOD, selected_flag='lod.enabled', atmosphere_guard_absent=True)
    report['instrumentation_properties']['benchmarkFeature'] = 'volume'
    report['loaded_mods'] = dict(minecraft='1.21.1', neoforge='21.1.253', iris='1.8.12-snapshot+mc1.21.1-local',
        sodium='0.6.13+mc1.21.1', shaderlod='0.1.0-dev')
    report['loaded_artifacts_sha256'] = {'': '8' * 64, '/fixtures/neoforge.jar': '9' * 64,
        '/fixtures/iris.jar': 'a' * 64, '/fixtures/sodium.jar': 'b' * 64}
    directory = next(iter(DIRECTORY_RECEIPT))
    report['loaded_artifacts_sha256'][directory] = copy.deepcopy(DIRECTORY_RECEIPT[directory])
    report['loaded_artifact_inventory'] = []
    paths = {'minecraft': '', 'neoforge': '/fixtures/neoforge.jar', 'iris': '/fixtures/iris.jar',
        'sodium': '/fixtures/sodium.jar', 'shaderlod': directory}
    for mod, path in paths.items():
        report['loaded_artifact_inventory'].append(dict(mods={mod: report['loaded_mods'][mod]}, path=path,
            uri='jij:/fixtures/iris.jar%237!/META-INF/jarjar/minecraft-client.jar' if not path else Path(path).as_uri() + ('/' if path == directory else ''),
            filesystem_provider='jij' if not path else 'file', identity=copy.deepcopy(report['loaded_artifacts_sha256'][path])))
    report['workload-manifest.json'].update(adaptive_pack_sha256=pin, source_world_sha256='7' * 64)
    for phase in report['phases']:
        phase['windowState'] = copy.deepcopy(WINDOW)
        for boundary in ('inputStart', 'inputEnd'):
            state = phase[boundary]
            for field in FIELDS:
                state[field] = copy.deepcopy(OBJECTS[field])
                state[field + '_sha256'] = OBJECTS[field + '_sha256']
            state.update(actual_shader_sha256=pin, framebuffer_width=2560, framebuffer_height=1440,
                volume_dh_present=False, volume_camera_type='FIRST_PERSON', volume_fov=70, control_sha256='6' * 64,
                **{'config/shaderlod-effects-client.toml_sha256': '4' * 64})
            state['volume_window_state'] = copy.deepcopy(WINDOW['capture']['start']['state'])
            state['loaded_semantic_config']['effects'] = copy.deepcopy(state['volume_effect_controls'])
            state['loaded_semantic_config']['rendering']['shadowFrustumCoherence'] = False
            state['shadow_coherence_configured'] = state['shadow_coherence_active'] = False
            state['effective_shader_options'] = {k: v[0] for k, v in VOLUME_REQUIRED_OPTIONS.items()} | {'QUALITY': 'high'}
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            if pin != next(iter(VOLUME_ARCHIVES)):
                q = state['volume_source_qualification']
                q['source_binding'] = volume_source_binding(pin)
                q['source_fingerprint'] = {'sourceSha256': pin, 'memberSha256': copy.deepcopy(q['source_binding']['memberSha256'])}
                state['volume_source_qualification_sha256'] = ordered_digest(q)
        phase['gpu'] = [dict(phase['gpu'][0], frameId=phase['startFrame'] + index) for index in range(1, 61)]
    report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'volume')
    return report


def analyze(report, **kwargs):
    return analyze_report(report, expected_directory_artifacts=DIRECTORY_RECEIPT,
        expected_artifact_inventory=expected_inventory(), **kwargs)


def expected_inventory():
    result = copy.deepcopy(volume_fixture()['loaded_artifact_inventory'])
    for item in result:
        if item['filesystem_provider'] == 'jij': item['uri'] = item['uri'].replace('%237', '')
    return result


class VolumeInprocessTests(unittest.TestCase):
    def test_colliding_virtual_paths_keep_distinct_direct_identities(self):
        report = volume_fixture()
        second = dict(mods={'fabric_api_base': '0.4.42+fixture'}, path='',
            uri='jij:/fixtures/sodium.jar%2312!/META-INF/jarjar/fabric-api-base.jar',
            filesystem_provider='jij', identity='c' * 64)
        report['loaded_artifact_inventory'].append(second)
        report['loaded_mods'].update(second['mods'])
        report['loaded_artifacts_sha256'][''] = second['identity']
        expected = expected_inventory() + [dict(second, uri=second['uri'].replace('%2312', ''))]
        validate_volume_artifacts(report, DIRECTORY_RECEIPT, expected)
        for mutate in (lambda r: r['loaded_artifact_inventory'][-1].update(identity='d' * 64),
                       lambda r: r['loaded_artifact_inventory'][-1].update(uri=second['uri'].replace('fabric-api-base.jar', 'other.jar')),
                       lambda r: r['loaded_artifact_inventory'][-1].update(uri=report['loaded_artifact_inventory'][0]['uri']),
                       lambda r: r['loaded_artifacts_sha256'].update({'': 'e' * 64})):
            altered = copy.deepcopy(report); mutate(altered)
            with self.assertRaises(ValueError): validate_volume_artifacts(altered, DIRECTORY_RECEIPT, expected)

    def test_pinned_nested_alternatives_and_mount_counter(self):
        report = volume_fixture(); expected = expected_inventory()
        first = expected[0]
        alternative = dict(first, uri=first['uri'].replace('/iris.jar!/', '/sodium.jar!/'), identity='c' * 64)
        expected[0] = {'alternatives': [first, alternative]}
        actual = report['loaded_artifact_inventory'][0]
        actual.update(alternative, uri=alternative['uri'].replace('.jar!/', '.jar%23999!/', 1))
        report['loaded_artifacts_sha256'][''] = alternative['identity']
        validate_volume_artifacts(report, DIRECTORY_RECEIPT, expected)
        actual['uri'] = actual['uri'].replace('/sodium.jar', '/unapproved.jar')
        with self.assertRaises(ValueError): validate_volume_artifacts(report, DIRECTORY_RECEIPT, expected)

    def test_actual_gson_order_hash_descriptor_and_both_pins(self):
        for field in FIELDS: self.assertEqual(ordered_digest(OBJECTS[field]), OBJECTS[field + '_sha256'])
        for pin in VOLUME_ARCHIVES:
            result = analyze(volume_fixture(pin))
            self.assertEqual(result['shader_sha256'], pin)
            self.assertEqual(result['selected_flag'], 'lod.enabled')
            self.assertEqual(len(result['adjacent_pairs']), 2)
            self.assertIn('existing-pass budget only', result['volume_diagnostic_limits'])
            self.assertIn('no original Photon 1.5x', result['volume_diagnostic_limits'])
            self.assertIsNone(result['phases'][0]['uniform_observation'])
        # Java Set serialization order can reverse independently of actual requirements.
        report = volume_fixture()
        for phase in report['phases']:
            for boundary in ('inputStart', 'inputEnd'):
                state = phase[boundary]
                state['volume_adapter_binding']['descriptor']['protocols'][1]['acceptedVersions'].reverse()
                state['volume_adapter_binding_sha256'] = ordered_digest(state['volume_adapter_binding'])
        report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'volume')
        analyze(report)

    def test_artifact_empty_slot_requires_complete_named_independent_inventory(self):
        report = volume_fixture()
        with self.assertRaisesRegex(ValueError, 'independent complete frozen'): analyze_report(report, expected_directory_artifacts=DIRECTORY_RECEIPT)
        for mutate in (lambda r: r.pop('loaded_artifact_inventory'),
                       lambda r: r['loaded_artifact_inventory'].pop(),
                       lambda r: r['loaded_artifact_inventory'][0].update(uri=None),
                       lambda r: r['loaded_artifact_inventory'][0].update(locator_error='UnsupportedOperationException'),
                       lambda r: r['loaded_artifact_inventory'][0].update(mods={}),
                       lambda r: r['loaded_artifact_inventory'][0].update(identity='0' * 64),
                       lambda r: r['loaded_artifact_inventory'].append(copy.deepcopy(r['loaded_artifact_inventory'][0])),
                       lambda r: r['loaded_mods'].update(unknown='1'),
                       lambda r: r['loaded_artifacts_sha256'].update(unknown='c' * 64)):
            report = volume_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze(report)
        changed = copy.deepcopy(volume_fixture()['loaded_artifact_inventory']); changed[0]['identity'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'frozen receipt'):
            analyze_report(volume_fixture(), expected_directory_artifacts=DIRECTORY_RECEIPT, expected_artifact_inventory=changed)

    def test_old_spatial_empty_path_failure_retained(self):
        report = spatial_fixture(); report['loaded_artifacts_sha256'][''] = '8' * 64
        with self.assertRaisesRegex(ValueError, 'Spatial artifact path missing'):
            analyze_report(report, expected_directory_artifacts=DIRECTORY_RECEIPT,
                expected_artifact_inventory=volume_fixture()['loaded_artifact_inventory'])

    def test_timing_floors_positive_unique_scope_epochs_and_duration(self):
        for mutate in (lambda p: p['gpu'].pop(), lambda p: p['gpu'][0].update(milliseconds=0),
                       lambda p: p['gpu'][0].update(epoch=99), lambda p: p['gpu'][0].update(frameId=p['startFrame']),
                       lambda p: p['gpu'][0].update(scope='deferred'),
                       lambda p: p['gpu'][0].update(frameId=p['gpu'][1]['frameId']),
                       lambda p: p.update(elapsedSeconds=19.99)):
            report = volume_fixture(); mutate(report['phases'][0])
            with self.assertRaises(ValueError): analyze(report)
        report = volume_fixture(); report['measurement_seconds'] = 19.99
        with self.assertRaisesRegex(ValueError, '>=20s'): analyze(report)
        report = volume_fixture(); report['phases'][1]['gpuEpoch'] = 1
        for sample in report['phases'][1]['gpu']: sample['epoch'] = 1
        with self.assertRaisesRegex(ValueError, 'reused'): analyze(report)

    def test_source_options_policy_protocol_boundaries_reject_rehashed_mutations(self):
        mutations = [lambda s: s['volume_effect_controls']['VOLUMETRICS'].update(mode='FULL'),
            lambda s: s['volume_effect_controls']['VOLUMETRICS']['policy'].update(farWorkRatio=.5),
            lambda s: s['volume_effect_controls']['VOLUMETRICS']['policy']['bands'].update(nearBlocks=256),
            lambda s: s['volume_effect_controls']['OPAQUE_LIGHTING'].update(mode='ADAPTIVE'),
            lambda s: s['volume_adapter_binding'].update(reason='UNVALIDATED_PACK'),
            lambda s: s['volume_adapter_binding'].update(operations=[]),
            lambda s: s['volume_adapter_binding']['descriptor']['capabilities'][0]['domain'].update(minimumRetainedWork=7),
            lambda s: s['volume_adapter_binding']['descriptor']['protocols'][1].update(acceptedVersions=[1]),
            lambda s: s['volume_source_qualification']['source_fingerprint']['memberSha256'].clear(),
            lambda s: s['volume_source_qualification']['source_binding'].update(sourceSha256='0' * 64)]
        for mutate in mutations:
            report = volume_fixture(); state = report['phases'][0]['inputEnd']; mutate(state)
            state['loaded_semantic_config']['effects'] = copy.deepcopy(state['volume_effect_controls'])
            for field in FIELDS: state[field + '_sha256'] = ordered_digest(state[field])
            with self.assertRaises(ValueError): analyze(report)
        for name in VOLUME_REQUIRED_OPTIONS:
            report = volume_fixture(); state = report['phases'][0]['inputEnd']
            state['effective_shader_options'][name] = 'true' if name == 'BLOCKY_CLOUDS' else 'false'
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            with self.assertRaises(ValueError): analyze(report)

    def test_global_config_pose_window_and_feature_contract(self):
        for mutate in (lambda s: s.update(volume_dh_present=True), lambda s: s.update(volume_fov=71),
                       lambda s: s.update(yaw=90), lambda s: s.update(day_time=7001),
                       lambda s: s['lod_settings'].update(enabled=True),
                       lambda s: s.update(**{'config/shaderlod-effects-client.toml_sha256': '5' * 64}),
                       lambda s: s['volume_window_state'].update(focused='unknown'),
                       lambda s: s['volume_window_state']['pacing'].update(vsync=True)):
            report = volume_fixture(); mutate(report['phases'][0]['inputEnd'])
            with self.assertRaises(ValueError): analyze(report)
        for mutate in (lambda r: r.update(restored_initial_enabled=False), lambda r: r.update(atmosphere_guard_absent=False),
                       lambda r: r['instrumentation_properties'].update(workTrace='true'),
                       lambda r: r['loaded_mods'].update(iris='unknown'), lambda r: r.update(starting_order='ON/OFF/ON/OFF'),
                       lambda r: r['phases'][0].pop('windowState'),
                       lambda r: r['phases'][0]['windowState']['capture']['focused'].update(unknown_observations=1)):
            report = volume_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze(report)

    def test_raw_order_and_same_archive_sessions(self):
        report = volume_fixture(); state = report['phases'][0]['inputEnd']; field = 'volume_adapter_binding'
        state[field] = dict(reversed(list(state[field].items())))
        with self.assertRaisesRegex(ValueError, 'fingerprint'): analyze(report)
        first = analyze(volume_fixture()); self.assertFalse(compare_sessions([first, analyze(volume_fixture())])['archive_source_delta'])
        with self.assertRaisesRegex(ValueError, 'no cross-archive'):
            compare_sessions([first, analyze(volume_fixture(list(VOLUME_ARCHIVES)[1]))])


if __name__ == '__main__': unittest.main()
