"""Small four-phase fixtures, without Minecraft, builds or large report parsing."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from analyze_inprocess_control import analyze_report, compare_sessions, canonical, OFF_OPTIONS, FALSE_FLAGS, METHOD, EFFECTS_METHOD, EFFECT_OPTIONS, COHERENCE_METHOD, ORIGINAL_PHOTON_SHA256, TERRAIN_METHOD, TERRAIN_PHOTON_SHA256, semantic_state, SSR_COST_METHOD, SSR_COST_ARCHIVES, ssr_cost_members, validate_ssr_cost_qualification

PIN = '1' * 64


def fixture(gpu=True, cpu=True):
    options = {key: 'false' for key in OFF_OPTIONS}
    options.update(SHADERLOD_USE_MOD_CONFIG='true', SHADERLOD_PRESERVE_LIGHTING='true', QUALITY='high')
    base = {'mod_present': True, 'shaders_in_use': True, 'fallback': False, 'hide_gui': True, 'gui_scale': 2,
            'actual_shader_sha256': PIN, 'framebuffer_width': 1488, 'framebuffer_height': 836,
            'requested_chunks': 24, 'effective_chunks': 24, 'x': 240.5, 'y': 68, 'z': 680.5, 'yaw': -180, 'pitch': 3,
            'day_time': 7000, 'rain': 0, 'thunder': 0, 'dimension': 'minecraft:overworld',
            'lod_settings': {'enabled': False, 'debug': False, 'near': 32, 'far': 96, 'transition': 8},
            'loaded_semantic_config': {'lod': {'enabled': False, 'debug': False, 'near': 32, 'far': 96, 'transition': 8, 'preset': 'PERFORMANCE'},
                                       'terrain': {'enabled': False, 'vanillaChunkCap': 16, 'warmSourceConfirmed': False, 'warmupSeconds': 30},
                                       'rendering': {'optimizeShadowCulling': False, 'separateShadowVisibilityLists': False}},
            'effective_shader_options': options, 'effective_shader_options_sha256': hashlib.sha256(canonical(options).encode()).hexdigest()}
    base.update({key: False for key in FALSE_FLAGS})
    reference = copy.deepcopy(base)
    reference['lod_settings'].pop('enabled')
    reference['loaded_semantic_config']['lod'].pop('enabled')
    report = {'schema': 3, 'status': 'complete', 'session_id': str(uuid.uuid4()), 'method': METHOD,
              'starting_order': 'OFF/ON/OFF/ON', 'gpu_requested': gpu, 'cpu_requested': cpu, 'restored_initial_enabled': True,
              'measurement_seconds': 20, 'backend': 'ELAPSED_ALTERNATING' if gpu else 'DISABLED',
              'sampling_policy': 'frame only; interval16', 'instrumentation_properties': {'gpuSampleInterval': '16'},
              'stable_input_reference': reference, 'loaded_mods': {'shaderlod': '0.1', 'iris': '1.8.12'},
              'loaded_artifacts_sha256': {'iris.jar': '2' * 64}, 'workload-manifest.json': {'adaptive_pack_sha256': PIN}, 'phases': []}
    for index in range(1, 5):
        enabled = index % 2 == 0
        state = copy.deepcopy(base)
        state['lod_settings']['enabled'] = enabled
        state['loaded_semantic_config']['lod']['enabled'] = enabled
        state['config/shaderlod-client.toml_sha256'] = str(index) * 64
        phase = {'index': index, 'id': str(uuid.uuid4()), 'status': 'complete', 'enabled': enabled,
                 'startFrame': index * 200, 'endFrame': index * 200 + 100, 'renderFrames': 100,
                 'elapsedSeconds': 20, 'gpuEpoch': index if gpu else None, 'cpuEpoch': index if cpu else None,
                 'inputStart': state, 'inputEnd': copy.deepcopy(state), 'image': f'phase-{index}.png',
                 'imageSha256': '3' * 64, 'gpu': [], 'cpu': [],
                 'windowState': {'capture': {'focused': {'unknown_observations': 4}}},
                 'temporalStart': {'wetness': {'mode': 'fixed', 'fixedWetness': 0, 'suppliers': [{'supplierReads': 10, 'appliedWetness': 0}]}},
                 'temporalEnd': {'wetness': {'mode': 'fixed', 'fixedWetness': 0, 'measurementReads': 10, 'suppliers': [{'supplierReads': 20, 'measurementReads': 10, 'appliedWetness': 0, 'appliedMin': 0, 'appliedMax': 0}]}}}
        if gpu: phase['gpu'] = [{'scope': 'game-render', 'epoch': index, 'frameId': phase['startFrame'] + 1, 'milliseconds': 8 if not enabled else 6}]
        if cpu: phase['cpu'] = [{'scope': 'run-tick', 'epoch': index, 'frameId': phase['startFrame'] + 1, 'wallMilliseconds': 9, 'threadMilliseconds': None}]
        report['phases'].append(phase)
    return report

def coherence_fixture():
    report = fixture()
    report.update(benchmark_feature='coherence', method=COHERENCE_METHOD, selected_flag='rendering.shadowFrustumCoherence',
                  initial_enabled=True, restored_initial_feature=True,
                  diagnostic_policy={'shadow_coherence_oracle': False, 'visibility_phase_observer': False})
    report['instrumentation_properties'].update(shadowCoherenceOracle='false', visibilityLists='false', benchmarkFeature='coherence')
    options = {'QUALITY': 'high'}  # Original Photon has no ShaderLOD pack switches.
    for phase in report['phases']:
        for key in ('inputStart', 'inputEnd'):
            state = phase[key]
            state['actual_shader_sha256'] = ORIGINAL_PHOTON_SHA256
            state['effective_shader_options'] = options.copy()
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(options).encode()).hexdigest()
            state['lod_settings']['enabled'] = True
            state['loaded_semantic_config']['lod']['enabled'] = True
            state['loaded_semantic_config']['rendering']['shadowFrustumCoherence'] = phase['enabled']
            state['shadow_coherence_configured'] = phase['enabled']
            state['shadow_coherence_active'] = phase['enabled']
    report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'coherence')
    return report


def effects_fixture(enabled_options=EFFECT_OPTIONS, reverse=False, gpu=True, cpu=True):
    report = fixture(gpu=gpu, cpu=cpu)
    report.update(benchmark_feature='effects', method=EFFECTS_METHOD, selected_flag='lod.enabled', atmosphere_guard_absent=True)
    report['instrumentation_properties']['benchmarkFeature'] = 'effects'
    report['starting_order'] = 'ON/OFF/ON/OFF' if reverse else 'OFF/ON/OFF/ON'
    for phase in report['phases']:
        if reverse: phase['enabled'] = not phase['enabled']
        for boundary in ('inputStart', 'inputEnd'):
            state = phase[boundary]
            state['lod_settings']['enabled'] = phase['enabled']
            state['loaded_semantic_config']['lod']['enabled'] = phase['enabled']
            state['loaded_semantic_config']['rendering']['shadowFrustumCoherence'] = False
            state['shadow_coherence_configured'] = state['shadow_coherence_active'] = False
            options = state['effective_shader_options']
            for name in enabled_options: options[name] = 'true'
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(options).encode()).hexdigest()
    report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'effects')
    return report


def terrain_fixture(reverse=False):
    report = effects_fixture(reverse=reverse)
    report.update(benchmark_feature='terrain', method=TERRAIN_METHOD, selected_flag='terrain.enabled',
                  initial_enabled=True, initial_feature_enabled=True, restored_initial_feature=True,
                  settle_seconds=75, measurement_seconds=30)
    report['instrumentation_properties']['benchmarkFeature'] = 'terrain'
    report['terrain_scene_identifier'] = report['session_id'] + ':inspected-fixed-scene'
    report['workload-manifest.json'].update(adaptive_pack_sha256=TERRAIN_PHOTON_SHA256, source_world_sha256='7' * 64)
    report['loaded_mods'].update(minecraft='1.21.1', neoforge='21.1.253', distanthorizons='3.3.3',
                               iris='1.8.12-snapshot+mc1.21.1-local', sodium='0.6.13+mc1.21.1')
    window = {'availability': 'available', 'focused': 'true', 'iconified': 'false', 'visible': 'true',
              'window_width': 1488, 'window_height': 836, 'framebuffer_width': 2976, 'framebuffer_height': 1672,
              'pacing': {'pause_on_lost_focus': False, 'fullscreen': False, 'configured_framerate_limit': 260, 'vsync': False},
              'note': 'fixture cached GLFW state'}
    for phase in report['phases']:
        phase.update(elapsedSeconds=30, settleElapsedSeconds=75.01)
        for boundary in ('inputStart', 'inputEnd'):
            state = phase[boundary]
            state.update(actual_shader_sha256=TERRAIN_PHOTON_SHA256, framebuffer_width=2976, framebuffer_height=1672,
                         dh_present=True, terrain_confirmation=True, terrain_configured=phase['enabled'], terrain_active=phase['enabled'],
                         requested_chunks=24, effective_chunks=16 if phase['enabled'] else 24, control_sha256='6' * 64,
                         terrain_camera_type='FIRST_PERSON', terrain_fov=70)
            state['lod_settings']['enabled'] = True
            state['loaded_semantic_config']['lod']['enabled'] = True
            state['loaded_semantic_config']['terrain'].update(enabled=phase['enabled'], warmSourceConfirmed=True)
            state['geometry_policy'] = {'active': phase['enabled'], 'requestedChunks': 24,
                'effectiveChunks': 16 if phase['enabled'] else 24, 'cap': 16, 'warmupTicks': 600,
                'stableTicks': (1600 if boundary == 'inputStart' else 2000) if phase['enabled'] else 0,
                'reason': 'DH terrain cap active' if phase['enabled'] else 'terrain policy disabled', 'detail': ''}
            state['terrain_dh_state'] = {'world_loaded': True, 'rendering_enabled': True, 'rendering_mode': 'DEFAULT', 'chunks': 128}
            state['terrain_window_state'] = copy.deepcopy(window)
        capture = {'observations': 10, 'available_observations': 10, 'unavailable_observations': 0,
                   'start': {'state': copy.deepcopy(window)}, 'end': {'state': copy.deepcopy(window)},
                   'transition_counts': {}, 'transitions': [], 'dropped_transitions': 0}
        for flag in ('focused', 'visible', 'iconified'):
            capture[flag] = {'true_observations': 10 if window[flag] == 'true' else 0,
                             'false_observations': 10 if window[flag] == 'false' else 0, 'unknown_observations': 0}
        phase['windowState'] = {'schema': 1, 'capture': capture}
        if phase['enabled']:
            observed = copy.deepcopy(phase['inputStart'])
            observed.update(terrain_active=False, effective_chunks=24)
            observed['geometry_policy'].update(active=False, effectiveChunks=24, stableTicks=1, reason='warming DH scene')
            phase['terrainRearm'] = {'attempts': 1, 'succeeded': True, 'elapsed_settle_seconds': .1,
                'at_utc': '2026-10-05T12:00:00Z', 'scene_identifier': report['terrain_scene_identifier'],
                'api': 'GeometryLodController.confirmCurrentScene', 'observed_input': observed}
    report['terrain_initial_observation'] = copy.deepcopy(next(phase['inputStart'] for phase in report['phases'] if phase['enabled']))
    report['stable_input_reference'] = semantic_state(report['terrain_initial_observation'], True, 'terrain')
    return report


def ssr_qualification(pin):
    record = SSR_COST_ARCHIVES[pin]
    result = {'qualified': True, 'archive_sha256': pin, 'policy': record['policy'], 'member_sha256': ssr_cost_members(pin)}
    if 'metadata' in record:
        root = Path(__file__).resolve().parents[1]
        manifest_name = 'ssr-far-guard-probe.json' if record['policy'] == 'full' else 'ssr-far-reduced-probe.json'
        manifest = json.loads((root / 'patches/photon-experiments' / manifest_name).read_text())
        result['probe_metadata'] = {'experiment': manifest['experiment'], 'arm': 'enabled', 'baseline': manifest['baseline'],
            'base_sha256': TERRAIN_PHOTON_SHA256, 'base_version': 'v1.3b', 'manifest_sha256': record['manifest_sha256'],
            'baseline_metadata_sha256': manifest['baseline_metadata_sha256'], 'license_sha256': manifest['license_sha256'],
            'switch': record['switch'], 'switch_enabled': True,
            'member_sha256': {name: result['member_sha256'][name] for name in ('shaders/settings.glsl', 'shaders/include/lighting/specular_lighting.glsl')},
            'modified_baseline_files': ['shaders/include/lighting/specular_lighting.glsl', 'shaders/settings.glsl'],
            'policy': manifest['policy'], 'runtime_compilation': 'unverified',
            'performance_intent': 'More far tracing for quality/causality; no optimization claim' if record['policy'] == 'full' else 'Reduced far tracing vs full-trace control; actual quality and GPU benefit unverified'}
    return result


def ssr_cost_fixture(pin=TERRAIN_PHOTON_SHA256, reverse=False):
    report = effects_fixture(('SHADERLOD_SSR',), reverse)
    report.update(benchmark_feature='ssr_cost', method=SSR_COST_METHOD, selected_flag='lod.enabled',
                  ssr_cost_archive_qualification=ssr_qualification(pin))
    report['instrumentation_properties']['benchmarkFeature'] = 'ssr_cost'
    report['loaded_mods'].update(minecraft='1.21.1', neoforge='21.1.253',
                               iris='1.8.12-snapshot+mc1.21.1-local', sodium='0.6.13+mc1.21.1')
    report['workload-manifest.json']['source_world_sha256'] = '7' * 64
    window_template = terrain_fixture()['phases'][0]['windowState']
    for phase in report['phases']:
        phase['windowState'] = copy.deepcopy(window_template)
        for boundary in ('inputStart', 'inputEnd'):
            state = phase[boundary]
            state.update(actual_shader_sha256=pin, framebuffer_width=2976, framebuffer_height=1672,
                         ssr_cost_dh_present=False, ssr_cost_camera_type='FIRST_PERSON', ssr_cost_fov=70,
                         x=500.5, y=68.0, z=660.5, yaw=87.38, pitch=3.735, day_time=12500,
                         control_sha256='6' * 64)
            state['ssr_cost_window_state'] = copy.deepcopy(window_template['capture']['start']['state'])
            options = state['effective_shader_options']
            options.update(ENVIRONMENT_REFLECTIONS='true', SKY_REFLECTIONS='true')
            if 'switch' in SSR_COST_ARCHIVES[pin]: options[SSR_COST_ARCHIVES[pin]['switch']] = 'true'
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(options).encode()).hexdigest()
    report['stable_input_reference'] = semantic_state(report['phases'][0]['inputStart'], True, 'ssr_cost')
    return report


class InprocessTests(unittest.TestCase):
    def test_ssr_cost_three_exact_qualified_policies_in_both_orders(self):
        for pin in SSR_COST_ARCHIVES:
            for reverse in (False, True):
                result = analyze_report(ssr_cost_fixture(pin, reverse))
                self.assertEqual(result['method'], SSR_COST_METHOD)
                self.assertEqual(result['shader_sha256'], pin)
                self.assertEqual(result['ssr_cost_archive_qualification']['policy'], SSR_COST_ARCHIVES[pin]['policy'])
                self.assertEqual(len(result['adjacent_pairs']), 2)
                self.assertIsNone(result['phases'][0]['uniform_observation'])
                self.assertIn('correlated', result['uncertainty'])

    def test_ssr_cost_qualification_requires_exact_member_metadata_and_enabled_gate(self):
        for pin in SSR_COST_ARCHIVES:
            self.assertEqual(validate_ssr_cost_qualification(ssr_qualification(pin)), pin)
        for mutate in (lambda q: q.update(archive_sha256='0' * 64), lambda q: q.update(qualified=False),
                       lambda q: q['member_sha256'].update({'shaders/include/shaderlod/lod.glsl': '0' * 64}),
                       lambda q: q['member_sha256'].pop('LICENSE'), lambda q: q.update(policy='skip'),
                       lambda q: q.pop('probe_metadata'), lambda q: q['probe_metadata'].update(switch_enabled=False),
                       lambda q: q['probe_metadata'].update(arm='disabled'),
                       lambda q: q['probe_metadata'].update(manifest_sha256='0' * 64)):
            pin = next(pin for pin, data in SSR_COST_ARCHIVES.items() if data['policy'] == 'full')
            qualified = ssr_qualification(pin); mutate(qualified)
            with self.assertRaises(ValueError): validate_ssr_cost_qualification(qualified)

    def test_ssr_cost_explicit_mode_source_pin_noatmosphere_and_restore_required(self):
        for mutate in (lambda r: r.pop('ssr_cost_archive_qualification'), lambda r: r.update(method=EFFECTS_METHOD),
                       lambda r: r['instrumentation_properties'].update(benchmarkFeature='effects'),
                       lambda r: r.update(atmosphere_guard_absent=False), lambda r: r.update(restored_initial_enabled=False),
                       lambda r: r.update(selected_flag='terrain.enabled'), lambda r: r['loaded_mods'].update(distanthorizons='3.3.3')):
            report = ssr_cost_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze_report(report)
        with self.assertRaises(ValueError): analyze_report(ssr_cost_fixture(), expected_shader_sha256=PIN)

    def test_ssr_cost_ssr_only_private_gate_and_reflection_controls_preserved(self):
        pins = list(SSR_COST_ARCHIVES)
        for name, value in (('SHADERLOD_CLOUDS', 'true'), ('SHADERLOD_SSR', 'false'), ('SHADERLOD_PRESERVE_LIGHTING', 'false'),
                            ('SHADERLOD_USE_MOD_CONFIG', 'false'), ('SHADERLOD_CACHED_SKY', 'true'), ('SHADERLOD_WATER_ANALYTIC', 'true'),
                            ('ENVIRONMENT_REFLECTIONS', 'false'), ('SKY_REFLECTIONS', 'false'), ('PHOTON_SSR_FAR_FULL_TRACE_PROBE', 'true')):
            report = ssr_cost_fixture(pins[0]); state = report['phases'][0]['inputEnd']
            state['effective_shader_options'][name] = value
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            with self.assertRaises(ValueError): analyze_report(report)
        for pin in pins[1:]:
            report = ssr_cost_fixture(pin); state = report['phases'][0]['inputEnd']
            state['effective_shader_options'][SSR_COST_ARCHIVES[pin]['switch']] = 'false'
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            with self.assertRaises(ValueError): analyze_report(report)

    def test_ssr_cost_fixed_sun_pose_native_dh_and_config_identity(self):
        for name, value in (('x', 501), ('yaw', 90), ('pitch', 4), ('day_time', 7000), ('rain', 1),
                            ('ssr_cost_dh_present', True), ('framebuffer_width', 1488), ('effective_chunks', 16),
                            ('ssr_cost_camera_type', 'THIRD_PERSON_BACK'), ('terrain_active', True), ('shadow_culling_active', True)):
            report = ssr_cost_fixture(); report['phases'][0]['inputEnd'][name] = value
            with self.assertRaises(ValueError): analyze_report(report)
        report = ssr_cost_fixture(); report['phases'][0]['inputEnd']['loaded_semantic_config']['lod']['far'] = 160
        with self.assertRaises(ValueError): analyze_report(report)

    def test_ssr_cost_unknown_window_and_changed_pacing_rejected(self):
        for mutate in (lambda p: p.pop('windowState'), lambda p: p['windowState']['capture'].update(transition_counts={'focused': 2}),
                       lambda p: p['windowState']['capture']['focused'].update(unknown_observations=1),
                       lambda p: p['inputEnd']['ssr_cost_window_state'].update(availability='unavailable'),
                       lambda p: p['inputEnd']['ssr_cost_window_state']['pacing'].update(vsync=True)):
            report = ssr_cost_fixture(); mutate(report['phases'][0])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_ssr_cost_screenshots_and_hash_verification_still_required(self):
        report = ssr_cost_fixture()
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory); (profile / 'config').mkdir(); (profile / 'screenshots').mkdir()
            iris = profile / 'config/iris.properties'; iris.write_text('enableShaders=true\nshaderPack=B15.zip\n')
            fingerprint = hashlib.sha256(iris.read_bytes()).hexdigest()
            report['stable_input_reference']['config/iris.properties_sha256'] = fingerprint
            for phase in report['phases']:
                for key in ('inputStart', 'inputEnd'): phase[key]['config/iris.properties_sha256'] = fingerprint
                data = b'\x89PNG\r\n\x1a\n' + phase['image'].encode()
                (profile / 'screenshots' / phase['image']).write_bytes(data)
                phase['imageSha256'] = hashlib.sha256(data).hexdigest()
            self.assertTrue(analyze_report(report, profile=profile)['phases'][0]['image_verified'])
            (profile / 'screenshots' / report['phases'][0]['image']).write_bytes(b'bad')
            with self.assertRaises(ValueError): analyze_report(report, profile=profile)

    def test_ssr_cost_same_archive_session_compare_and_no_cross_archive_causality(self):
        a = analyze_report(ssr_cost_fixture()); b = analyze_report(ssr_cost_fixture(reverse=True))
        self.assertFalse(compare_sessions([a, b])['archive_source_delta'])
        different = analyze_report(ssr_cost_fixture(next(pin for pin, r in SSR_COST_ARCHIVES.items() if r['policy'] == 'full')))
        with self.assertRaisesRegex(ValueError, 'no cross-archive'): compare_sessions([a, different])
        b['loaded_artifacts_sha256']['iris.jar'] = '8' * 64
        with self.assertRaises(ValueError): compare_sessions([a, b])

    def test_ssr_cost_source_routes_are_explicit_and_old_modes_stay_strict(self):
        root = Path(__file__).resolve().parents[1]
        session = (root / 'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java').read_text()
        inputs = (root / 'src/devClient/java/com/pp/shaderlod/dev/WorkloadInputs.java').read_text()
        self.assertIn('case "ssr_cost" -> "' + SSR_COST_METHOD + '"', session)
        self.assertIn('inputs.qualifySsrCostArchive()', session)
        self.assertIn('WorkloadInputs.validateSsrCost(state, expectedEnabled)', session)
        reference_setup = session.split('if (stableReference == null) {', 1)[1].split('} else checkIdentity(state);', 1)[0]
        self.assertIn('if (feature.equals("ssr_cost")) cameraReference = Minecraft.getInstance().getCameraEntity();', reference_setup)
        for pin in SSR_COST_ARCHIVES: self.assertIn(pin, inputs)
        for record in SSR_COST_ARCHIVES.values():
            self.assertIn(record['specular'], inputs); self.assertIn(record['settings'], inputs)
        report = ssr_cost_fixture(); report.update(benchmark_feature='global', method=METHOD)
        with self.assertRaises(ValueError): analyze_report(report)

    def test_terrain_forward_reverse_only_selected_radius_policy_changes(self):
        for reverse in (False, True):
            result = analyze_report(terrain_fixture(reverse))
            self.assertEqual(result['method'], TERRAIN_METHOD)
            self.assertEqual(result['selected_flag'], 'terrain.enabled')
            self.assertEqual(result['shader_sha256'], TERRAIN_PHOTON_SHA256)
            self.assertEqual([p['terrain_policy_start']['effectiveChunks'] for p in result['phases']], [16, 24, 16, 24] if reverse else [24, 16, 24, 16])
            self.assertIs(result['stable_input_reference']['lod_settings']['enabled'], True)
            self.assertIs(result['stable_input_reference']['terrain_confirmation'], True)
            self.assertNotIn('enabled', result['stable_input_reference']['loaded_semantic_config']['terrain'])
            self.assertNotIn('effective_chunks', result['stable_input_reference'])
            self.assertEqual(result['phases'][1]['terrain_policy_end']['stableTicks'], 0 if reverse else 2000)
            self.assertIn('not a shader-only', result['effect_attribution'])
            self.assertIn('correlated', result['uncertainty'])

    def test_terrain_requires_explicit_label_pin_restore_and_long_settle(self):
        for mutate in (
                lambda r: r.update(method=EFFECTS_METHOD), lambda r: r.update(selected_flag='lod.enabled'),
                lambda r: r['instrumentation_properties'].update(benchmarkFeature='effects'),
                lambda r: r.update(restored_initial_feature=False), lambda r: r.update(initial_enabled=False),
                lambda r: r.pop('terrain_initial_observation'), lambda r: r.update(terrain_scene_identifier='other'),
                lambda r: r.update(settle_seconds=59), lambda r: r.update(measurement_seconds=20),
                lambda r: r['phases'][0].update(settleElapsedSeconds=50),
                lambda r: r['instrumentation_properties'].update(shadowObserve='true')):
            report = terrain_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze_report(report)
        with self.assertRaises(ValueError): analyze_report(terrain_fixture(), expected_shader_sha256=PIN)

    def test_terrain_requires_real_policy_at_both_boundaries_and_warm_eligibility(self):
        for boundary in ('inputStart', 'inputEnd'):
            for modify in (
                    lambda s: s.pop('geometry_policy'), lambda s: s['geometry_policy'].update(active=False),
                    lambda s: s['geometry_policy'].update(stableTicks=599), lambda s: s['geometry_policy'].update(warmupTicks=0),
                    lambda s: s['geometry_policy'].update(cap=8), lambda s: s['geometry_policy'].update(reason='warming DH scene'),
                    lambda s: s.update(effective_chunks=24), lambda s: s.update(terrain_active=False)):
                report = terrain_fixture(); modify(report['phases'][1][boundary])
                with self.assertRaises(ValueError): analyze_report(report)
        report = terrain_fixture(); report['phases'][0]['inputEnd']['geometry_policy']['effectiveChunks'] = 16
        with self.assertRaises(ValueError): analyze_report(report)

    def test_terrain_once_only_fixed_scene_rearm_is_required_on_and_forbidden_off(self):
        for modify in (
                lambda p: p.pop('terrainRearm'), lambda p: p['terrainRearm'].update(attempts=2),
                lambda p: p['terrainRearm'].update(succeeded=False), lambda p: p['terrainRearm'].update(elapsed_settle_seconds=20),
                lambda p: p['terrainRearm'].update(scene_identifier='new scene'),
                lambda p: p['terrainRearm'].pop('at_utc'),
                lambda p: p['terrainRearm']['observed_input'].update(x=250),
                lambda p: p['terrainRearm']['observed_input']['geometry_policy'].update(reason='Iris/DH pipeline unavailable'),
                lambda p: p['terrainRearm']['observed_input']['terrain_dh_state'].update(chunks=24)):
            report = terrain_fixture(); modify(report['phases'][1])
            with self.assertRaises(ValueError): analyze_report(report)
        report = terrain_fixture(); report['phases'][0]['terrainRearm'] = copy.deepcopy(report['phases'][1]['terrainRearm'])
        with self.assertRaises(ValueError): analyze_report(report)

    def test_terrain_rejects_counter_reset_and_full_option_change_with_valid_hash(self):
        report = terrain_fixture(); report['phases'][1]['inputEnd']['geometry_policy']['stableTicks'] = 1000
        with self.assertRaisesRegex(ValueError, 'counter reset'): analyze_report(report)
        report = terrain_fixture(); state = report['phases'][1]['inputEnd']
        state['effective_shader_options']['QUALITY'] = 'low'
        state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
        with self.assertRaisesRegex(ValueError, 'Stable inputs'): analyze_report(report)

    def test_terrain_keeps_global_options_cpu_config_request_and_dh_immutable(self):
        for modify in (
                lambda s: s['lod_settings'].update(enabled=False),
                lambda s: s['loaded_semantic_config']['lod'].update(enabled=False),
                lambda s: s.update(requested_chunks=16), lambda s: s.update(terrain_confirmation=False),
                lambda s: s['loaded_semantic_config']['terrain'].update(warmSourceConfirmed=False),
                lambda s: s['loaded_semantic_config']['terrain'].update(vanillaChunkCap=8),
                lambda s: s['effective_shader_options'].update(SHADERLOD_CLOUDS='false'),
                lambda s: s.update(shadow_culling_active=True), lambda s: s.update(shadow_coherence_active=True),
                lambda s: s['terrain_dh_state'].update(rendering_mode='DEBUG'), lambda s: s['terrain_dh_state'].update(chunks=64),
                lambda s: s.update(control_sha256='8' * 64), lambda s: s.update(framebuffer_width=1488)):
            report = terrain_fixture(); modify(report['phases'][1]['inputEnd'])
            with self.assertRaises(ValueError): analyze_report(report)
        for name, value in (('terrain_camera_type', 'THIRD_PERSON_BACK'), ('terrain_fov', 90)):
            report = terrain_fixture(); report['phases'][1]['inputEnd'][name] = value
            with self.assertRaises(ValueError): analyze_report(report)

    def test_terrain_window_transitions_unknown_or_changed_pacing_rejected(self):
        for modify in (
                lambda p: p.pop('windowState'), lambda p: p['windowState']['capture'].update(transition_counts={'focused': 2}),
                lambda p: p['windowState']['capture'].update(unavailable_observations=1),
                lambda p: p['windowState']['capture']['focused'].update(unknown_observations=1),
                lambda p: p['inputEnd']['terrain_window_state']['pacing'].update(vsync=True)):
            report = terrain_fixture(); modify(report['phases'][1])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_terrain_old_modes_remain_strict_and_session_identity_stays_exact(self):
        report = terrain_fixture(); report.update(benchmark_feature='effects', method=EFFECTS_METHOD, selected_flag='lod.enabled')
        report['instrumentation_properties']['benchmarkFeature'] = 'effects'
        with self.assertRaises(ValueError): analyze_report(report)
        a = analyze_report(terrain_fixture()); b = analyze_report(terrain_fixture(True))
        self.assertFalse(compare_sessions([a, b])['archive_source_delta'])
        b['loaded_artifacts_sha256']['iris.jar'] = '8' * 64
        with self.assertRaises(ValueError): compare_sessions([a, b])
        b = copy.deepcopy(a); b['shader_sha256'] = PIN
        with self.assertRaises(ValueError): compare_sessions([a, b])

    def test_terrain_java_route_restores_flag_and_captures_actual_policy_without_compilation(self):
        root = Path(__file__).resolve().parents[1]
        session = (root / 'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java').read_text()
        inputs = (root / 'src/devClient/java/com/pp/shaderlod/dev/WorkloadInputs.java').read_text()
        self.assertIn('case "terrain" -> "' + TERRAIN_METHOD + '"', session)
        self.assertIn('ShaderLodConfig.TERRAIN_ENABLED.set(enabled)', session)
        self.assertIn('ShaderLodConfig.TERRAIN_ENABLED.set(initialFeatureEnabled)', session)
        self.assertIn('WorkloadInputs.validateTerrain(state, expectedEnabled)', session)
        self.assertEqual(session.count('GeometryLodController.confirmCurrentScene();'), 1)
        self.assertIn('current.terrainRearm != null', session)
        self.assertIn('checkTerrainSceneIdentity();', session)
        self.assertIn('inputs.terrainInputState()', session)
        self.assertIn('toJsonTree(com.pp.shaderlod.GeometryLodController.status())', inputs)
        self.assertIn('state.add("terrain_dh_state", terrainDhState())', inputs)
        self.assertIn('getAsJsonObject("geometry_policy").remove("stableTicks")', inputs)

    def test_effects_allactive_and_mixed_options_hold_constant_in_both_orders(self):
        for switches in (EFFECT_OPTIONS, EFFECT_OPTIONS[:1], ()):
            for reverse in (False, True):
                with self.subTest(switches=switches, reverse=reverse):
                    report = effects_fixture(switches, reverse)
                    result = analyze_report(report)
                    self.assertEqual(result['benchmark_feature'], 'effects')
                    self.assertEqual(result['selected_flag'], 'lod.enabled')
                    self.assertEqual(result['method'], EFFECTS_METHOD)
                    self.assertEqual(len(result['adjacent_pairs']), 2)
                    self.assertEqual([p['enabled'] for p in result['phases']], [True, False, True, False] if reverse else [False, True, False, True])
                    expected = {name: 'true' if name in switches else 'false' for name in EFFECT_OPTIONS}
                    observed = result['stable_input_reference']['effective_shader_options']
                    self.assertEqual({name: observed[name] for name in EFFECT_OPTIONS}, expected)
                    self.assertNotIn('enabled', result['stable_input_reference']['loaded_semantic_config']['lod'])
                    self.assertIn('correlated', result['uncertainty'])
                    self.assertNotIn('confidence_interval', result)

    def test_effects_requires_explicit_feature_method_selected_flag_and_source_guard(self):
        for mutate in (
                lambda r: r.pop('benchmark_feature'),
                lambda r: r.update(benchmark_feature='other'),
                lambda r: r.update(method=METHOD),
                lambda r: r.update(selected_flag='rendering.shadowFrustumCoherence'),
                lambda r: r['instrumentation_properties'].update(benchmarkFeature='global'),
                lambda r: r.pop('atmosphere_guard_absent'),
                lambda r: r.update(atmosphere_guard_absent=False)):
            report = effects_fixture(); mutate(report)
            with self.assertRaises(ValueError): analyze_report(report)

    def test_existing_global_mode_still_rejects_active_effects(self):
        report = effects_fixture()
        report.update(benchmark_feature='global', method=METHOD)
        report['instrumentation_properties']['benchmarkFeature'] = 'global'
        with self.assertRaisesRegex(ValueError, 'Alloff option'): analyze_report(report)

    def test_effects_requires_complete_literal_boolean_flags_even_when_fingerprints_match(self):
        for name in EFFECT_OPTIONS:
            for value in (None, True, False, 'TRUE', '1', 'false ', 1):
                report = effects_fixture()
                # Modify reference and both boundaries consistently so identity checks
                # alone cannot conceal a missing or malformed effect option.
                states = [report['stable_input_reference']] + [p[b] for p in report['phases'] for b in ('inputStart', 'inputEnd')]
                for state in states:
                    if value is None: state['effective_shader_options'].pop(name)
                    else: state['effective_shader_options'][name] = value
                    state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
                with self.subTest(name=name, value=value):
                    with self.assertRaisesRegex(ValueError, 'option'): analyze_report(report)

    def test_effects_flag_change_rejected_with_valid_recomputed_option_hash(self):
        report = effects_fixture()
        state = report['phases'][1]['inputEnd']
        state['effective_shader_options']['SHADERLOD_CLOUDS'] = 'false'
        state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
        with self.assertRaisesRegex(ValueError, 'Stable inputs'): analyze_report(report)

    def test_effects_preserves_bridge_lighting_and_disables_debug_cache_analytic(self):
        for name, value in (('SHADERLOD_USE_MOD_CONFIG', 'false'), ('SHADERLOD_PRESERVE_LIGHTING', 'false'),
                            ('SHADERLOD_DEBUG', 'true'), ('SHADERLOD_CACHED_SKY', 'true'), ('SHADERLOD_WATER_ANALYTIC', 'true')):
            report = effects_fixture()
            state = report['phases'][0]['inputEnd']
            state['effective_shader_options'][name] = value
            state['effective_shader_options_sha256'] = hashlib.sha256(canonical(state['effective_shader_options']).encode()).hexdigest()
            with self.assertRaises(ValueError): analyze_report(report)

    def test_effects_rejects_mod_pipeline_hud_semantic_and_cpu_feature_changes(self):
        for mutate in (
                lambda s: s.update(mod_present=False), lambda s: s.update(shaders_in_use=False),
                lambda s: s.update(fallback=True), lambda s: s.update(hide_gui=False),
                lambda s: s['lod_settings'].update(enabled=True), lambda s: s['lod_settings'].update(debug=True),
                lambda s: s.update(terrain_active=True), lambda s: s.update(shadow_culling_active=True),
                lambda s: s.update(shadow_lists_active=True), lambda s: s.update(shadow_coherence_active=True),
                lambda s: s.pop('shadow_coherence_configured'),
                lambda s: s['loaded_semantic_config']['rendering'].update(shadowFrustumCoherence=True),
                lambda s: s['loaded_semantic_config']['rendering'].pop('shadowFrustumCoherence'),
                lambda s: s['loaded_semantic_config']['lod'].update(near=64),
                lambda s: s['loaded_semantic_config']['terrain'].update(warmSourceConfirmed=True)):
            report = effects_fixture(); mutate(report['phases'][0]['inputEnd'])
            with self.assertRaises(ValueError): analyze_report(report)

    def test_effects_frozen_archive_camera_weather_and_framebuffer_are_required(self):
        for name, value in (('actual_shader_sha256', '9' * 64), ('x', 241.5), ('yaw', -179),
                            ('rain', 1), ('day_time', 8000), ('framebuffer_width', 2976), ('effective_chunks', 16)):
            report = effects_fixture(); report['phases'][0]['inputEnd'][name] = value
            with self.assertRaises(ValueError): analyze_report(report)
        report = effects_fixture()
        report['phases'][0]['temporalEnd']['wetness']['suppliers'][0]['appliedMax'] = .5
        with self.assertRaisesRegex(ValueError, 'Wetness'): analyze_report(report)

    def test_effects_sessions_require_same_full_option_maps_artifacts_and_feature(self):
        result = analyze_report(effects_fixture())
        self.assertFalse(compare_sessions([result, analyze_report(effects_fixture())])['archive_source_delta'])
        different = analyze_report(effects_fixture(EFFECT_OPTIONS[:1]))
        with self.assertRaises(ValueError): compare_sessions([result, different])
        different = copy.deepcopy(result); different['loaded_artifacts_sha256']['iris.jar'] = '9' * 64
        with self.assertRaises(ValueError): compare_sessions([result, different])
        with self.assertRaises(ValueError): compare_sessions([result, analyze_report(fixture())])

    def test_effects_query_and_timer_free_modes_do_not_claim_gpu_metrics(self):
        result = analyze_report(effects_fixture(gpu=False, cpu=False))
        self.assertIsNone(result['adjacent_pairs'][0]['on_vs_off_percent_time_reduction']['gpu_mean_ms'])
        self.assertIsNone(result['phases'][0]['cpu_wall_mean_ms'])

    def test_effects_java_source_routes_match_analyzer_contract_without_compilation(self):
        root = Path(__file__).resolve().parents[1]
        session = (root / 'src/devClient/java/com/pp/shaderlod/dev/BenchmarkSession.java').read_text()
        inputs = (root / 'src/devClient/java/com/pp/shaderlod/dev/WorkloadInputs.java').read_text()
        self.assertIn('case "effects" -> "' + EFFECTS_METHOD + '"', session)
        self.assertIn('WorkloadInputs.validateEffects(state, expectedEnabled)', session)
        self.assertIn('if (feature.equals("global") || feature.equals("effects")) {\n                    inputs.validateNoAtmosphereGuard();', session)
        self.assertIn('header.addProperty("atmosphere_guard_absent", true)', session)
        self.assertIn('if (feature.equals("global") || feature.equals("effects")) return withoutEnabled(state);', inputs)
        effects_body = inputs.split('public static void validateEffects(', 1)[1].split('private static void requireBoolean(', 1)[0]
        for name in EFFECT_OPTIONS: self.assertIn('"' + name + '"', effects_body)
        for name in ('shadow_coherence_active', 'shadow_coherence_configured', 'shadowFrustumCoherence',
                     'SHADERLOD_CACHED_SKY', 'SHADERLOD_WATER_ANALYTIC', 'SHADERLOD_DEBUG',
                     'SHADERLOD_PRESERVE_LIGHTING', 'SHADERLOD_USE_MOD_CONFIG'):
            self.assertIn('"' + name + '"', effects_body)

    def test_coherence_selects_only_feature_and_keeps_global_enabled(self):
        report = coherence_fixture()
        result = analyze_report(report)
        self.assertEqual(result['benchmark_feature'], 'coherence')
        self.assertEqual(result['shader_sha256'], ORIGINAL_PHOTON_SHA256)
        self.assertIs(result['stable_input_reference']['lod_settings']['enabled'], True)
        for mutate in (lambda s: s['lod_settings'].update(enabled=False),
                       lambda s: s['loaded_semantic_config']['lod'].update(enabled=False),
                       lambda s: s.update(shadow_coherence_active=False),
                       lambda s: s.update(shadow_culling_active=True),
                       lambda s: s.update(actual_shader_sha256=PIN)):
            bad = coherence_fixture(); mutate(bad['phases'][1]['inputEnd'])
            with self.assertRaises(ValueError): analyze_report(bad)
        with self.assertRaises(ValueError): analyze_report(report, expected_shader_sha256=PIN)

    def test_coherence_rejects_oracle_observer_and_mixed_artifacts(self):
        for field in ('shadow_coherence_oracle', 'visibility_phase_observer'):
            bad = coherence_fixture(); bad['diagnostic_policy'][field] = True
            with self.assertRaises(ValueError): analyze_report(bad)
        bad = coherence_fixture(); bad['instrumentation_properties']['shadowCoherenceOracle'] = 'true'
        with self.assertRaises(ValueError): analyze_report(bad)
        a = analyze_report(coherence_fixture()); b = copy.deepcopy(a)
        b['loaded_artifacts_sha256']['iris.jar'] = '8' * 64
        with self.assertRaises(ValueError): compare_sessions([a, b])
        with self.assertRaises(ValueError): compare_sessions([a, analyze_report(fixture())])

    def test_valid_pairs_keep_metrics_and_unknown_windows_separate(self):
        result = analyze_report(fixture())
        self.assertEqual(len(result['adjacent_pairs']), 2)
        self.assertEqual(result['adjacent_pairs'][0]['on_vs_off_percent_time_reduction']['gpu_mean_ms'], 25)
        self.assertEqual(result['phases'][0]['window_state']['capture']['focused']['unknown_observations'], 4)
        self.assertIsNone(result['phases'][0]['cpu_thread_mean_ms'])
        self.assertIn('raw world/pipeline IDs are not exported', result['pipeline_world_check'])

    def test_query_free_and_timer_free_are_not_gpu_claims(self):
        for cpu in (True, False):
            result = analyze_report(fixture(gpu=False, cpu=cpu))
            self.assertIsNone(result['phases'][0]['gpu_mean_ms'])
            self.assertIsNone(result['adjacent_pairs'][0]['on_vs_off_percent_time_reduction']['gpu_mean_ms'])
        bad = fixture(gpu=False); bad['phases'][0]['gpu'] = fixture()['phases'][0]['gpu']
        with self.assertRaises(ValueError): analyze_report(bad)

    def test_rejects_malformed_phase_intervals_identity_and_epochs(self):
        mutations = [lambda r: r.update(status='failed'), lambda r: r['phases'].pop(),
                     lambda r: r['phases'][1].update(id=r['phases'][0]['id']), lambda r: r['phases'][0].update(enabled=True),
                     lambda r: r['phases'][1].update(startFrame=r['phases'][0]['endFrame']),
                     lambda r: r['phases'][0].update(renderFrames=99), lambda r: r['phases'][0].update(elapsedSeconds=1),
                     lambda r: r['phases'][0]['gpu'][0].update(epoch=99),
                     lambda r: r['phases'][0]['gpu'][0].update(frameId=1),
                     lambda r: r['phases'][0]['gpu'][0].pop('milliseconds'),
                     lambda r: r['phases'][0]['gpu'][0].update(milliseconds=float('nan')),
                     lambda r: r['phases'][0].update(image=r['phases'][1]['image'])]
        for mutation in mutations:
            report = fixture(); mutation(report)
            with self.assertRaises(ValueError): analyze_report(report)

    def test_rejects_semantic_config_options_and_archive_changes(self):
        report = fixture()
        # Expected enabled persistence bytes can change; every semantic field remains fixed.
        report['phases'][0]['inputEnd']['config/shaderlod-client.toml_sha256'] = '0' * 64
        analyze_report(report)
        for mutate in (lambda s: s['loaded_semantic_config']['terrain'].update(vanillaChunkCap=24),
                       lambda s: s['effective_shader_options'].update(QUALITY='low'),
                       lambda s: s.update(actual_shader_sha256='9' * 64),
                       lambda s: s.update(shadow_culling_active=True)):
            report = fixture(); mutate(report['phases'][0]['inputEnd'])
            with self.assertRaises(ValueError): analyze_report(report)
        report = fixture(); report['phases'][0]['inputStart']['loaded_semantic_config']['terrain'].pop('warmupSeconds')
        with self.assertRaises(ValueError): analyze_report(report)

    def test_images_only_verified_when_profile_supplied(self):
        report = fixture()
        with tempfile.TemporaryDirectory() as directory:
            profile = Path(directory); (profile / 'screenshots').mkdir(); (profile / 'config').mkdir()
            iris = profile / 'config/iris.properties'; iris.write_text('enableShaders=true\ncolorSpace=SRGB\n')
            iris_hash = hashlib.sha256(iris.read_bytes()).hexdigest()
            report['stable_input_reference']['config/iris.properties_sha256'] = iris_hash
            for phase in report['phases']:
                for key in ('inputStart', 'inputEnd'): phase[key]['config/iris.properties_sha256'] = iris_hash
            for phase in report['phases']:
                data = b'\x89PNG\r\n\x1a\n' + phase['image'].encode()
                (profile / 'screenshots' / phase['image']).write_bytes(data)
                phase['imageSha256'] = hashlib.sha256(data).hexdigest()
            self.assertTrue(analyze_report(report, profile=profile)['phases'][0]['image_verified'])
            (profile / 'screenshots' / report['phases'][0]['image']).write_bytes(b'corrupt')
            with self.assertRaises(ValueError): analyze_report(report, profile=profile)

    def test_semantic_iris_normalization_requires_matching_captured_bytes(self):
        a = analyze_report(fixture()); b = copy.deepcopy(a)
        a['stable_input_reference']['config/iris.properties_sha256'] = '5' * 64
        b['stable_input_reference']['config/iris.properties_sha256'] = '6' * 64
        with self.assertRaises(ValueError): compare_sessions([a, b])
        for summary in (a, b):
            summary['verified_iris_configuration'] = {'raw_sha256': summary['stable_input_reference']['config/iris.properties_sha256'], 'properties': {'enableShaders': 'true', 'colorSpace': 'SRGB'}}
        self.assertTrue(compare_sessions([a, b])['iris_semantically_verified'])
        b['verified_iris_configuration']['properties']['colorSpace'] = 'LINEAR'
        with self.assertRaises(ValueError): compare_sessions([a, b])

    def test_comparison_rejects_quality_and_runtime_changes(self):
        a = analyze_report(fixture()); b = copy.deepcopy(a)
        b['session_id'] = str(uuid.uuid4()); b['shader_sha256'] = '4' * 64
        b['stable_input_reference']['actual_shader_sha256'] = '4' * 64
        self.assertTrue(compare_sessions([a, b])['archive_source_delta'])
        b['stable_input_reference']['loaded_semantic_config']['lod']['near'] = 64
        with self.assertRaises(ValueError): compare_sessions([a, b])
        b = copy.deepcopy(a); b['sampling_policy'] = 'different'
        with self.assertRaises(ValueError): compare_sessions([a, b])


if __name__ == '__main__': unittest.main()
