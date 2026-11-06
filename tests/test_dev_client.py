"""Launcher checks use temporary profiles and fake processes; never launch Minecraft."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import zipfile

SPEC = importlib.util.spec_from_file_location('dev_client', Path(__file__).parents[1] / 'scripts/dev_client.py')
dev = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dev)

# Exact generated root program-file structure captured from the failed BSL launch;
# profile/cwd are relocated independently. This fixture does not require a build.
NEOFORGE_PROGRAM_ARGS = '''# Main Class
cpw.mods.bootstraplauncher.BootstrapLauncher

# NeoForge Run-Type Program Arguments
--launchTarget
forgeclientdev
--version
21.1.253
--assetIndex
17
--assetsDir
/Users/pippo/.gradle/caches/neoformruntime/assets
--gameDir
.
--fml.fmlVersion
4.0.45
--fml.mcVersion
1.21.1
--fml.neoForgeVersion
21.1.253
--fml.neoFormVersion
20240808.144430

# User Supplied Program Arguments'''


# Captured only after all four actual offline producers succeeded.
PORT_PROGRAM_ARGS = {'1.21.11': {'profile': '/Users/pippo/github/shaderlod/run-workloads/port-12111-runtime-r1/neoforge',
             'program': '# Main Class\n'
                        'net.neoforged.fml.startup.Client\n'
                        '\n'
                        '# NeoForge Run-Type Program Arguments\n'
                        '--version\n'
                        '21.11.45\n'
                        '--assetIndex\n'
                        '29\n'
                        '--assetsDir\n'
                        '/Users/pippo/.gradle/caches/neoformruntime/assets\n'
                        '--fml.mcVersion\n'
                        '1.21.11\n'
                        '--fml.neoForgeVersion\n'
                        '21.11.45\n'
                        '--fml.neoFormVersion\n'
                        '20251209.172050\n'
                        '\n'
                        '# User Supplied Program Arguments\n'
                        '--gameDir\n'
                        '/Users/pippo/github/shaderlod/run-workloads/port-12111-runtime-r1/neoforge\n'
                        '--username\n'
                        'ShaderLOD\n'
                        '--fullscreen\n'
                        '--width\n'
                        '1920\n'
                        '--height\n'
                        '1200\n'
                        '--quickPlaySingleplayer\n'
                        'ShaderLOD-Landscape',
             'sha256': '4216c88c44c751f3e6df71f4225ed980c84f028e20cf2194c9fa4861fa304e36'},
 '26.1.2': {'profile': '/Users/pippo/github/shaderlod/run-workloads/port-2612-runtime-r1/neoforge',
            'program': '# Main Class\n'
                       'net.neoforged.fml.startup.Client\n'
                       '\n'
                       '# NeoForge Run-Type Program Arguments\n'
                       '--version\n'
                       '26.1.2.114\n'
                       '--assetIndex\n'
                       '30\n'
                       '--assetsDir\n'
                       '/Users/pippo/.gradle/caches/neoformruntime/assets\n'
                       '\n'
                       '# User Supplied Program Arguments\n'
                       '--gameDir\n'
                       '/Users/pippo/github/shaderlod/run-workloads/port-2612-runtime-r1/neoforge\n'
                       '--username\n'
                       'ShaderLOD\n'
                       '--fullscreen\n'
                       '--width\n'
                       '1920\n'
                       '--height\n'
                       '1200\n'
                       '--quickPlaySingleplayer\n'
                       'ShaderLOD-Landscape',
            'sha256': '7c2f90809eac09a93c9da245cc67aca8a899902159d68c5a84643641c2106b31'},
 '26.2': {'profile': '/Users/pippo/github/shaderlod/run-workloads/port-262-runtime-r1/neoforge',
          'program': '# Main Class\n'
                     'net.neoforged.fml.startup.Client\n'
                     '\n'
                     '# NeoForge Run-Type Program Arguments\n'
                     '--version\n'
                     '26.2.0.88\n'
                     '--assetIndex\n'
                     '32\n'
                     '--assetsDir\n'
                     '/Users/pippo/.gradle/caches/neoformruntime/assets\n'
                     '\n'
                     '# User Supplied Program Arguments\n'
                     '--gameDir\n'
                     '/Users/pippo/github/shaderlod/run-workloads/port-262-runtime-r1/neoforge\n'
                     '--username\n'
                     'ShaderLOD\n'
                     '--fullscreen\n'
                     '--width\n'
                     '1920\n'
                     '--height\n'
                     '1200\n'
                     '--quickPlaySingleplayer\n'
                     'ShaderLOD-Landscape',
          'sha256': '6dd07829180d843e0c327e67842bb021a58d05f5e49c4378c544e2cdfa3d5539'},
 '26.3': {'profile': '/Users/pippo/github/shaderlod/run-workloads/port-263-runtime-r1/neoforge',
          'program': '# Main Class\n'
                     'net.neoforged.fml.startup.Client\n'
                     '\n'
                     '# NeoForge Run-Type Program Arguments\n'
                     '--version\n'
                     '26.3.0.52-beta\n'
                     '--assetIndex\n'
                     '34\n'
                     '--assetsDir\n'
                     '/Users/pippo/.gradle/caches/neoformruntime/assets\n'
                     '\n'
                     '# User Supplied Program Arguments\n'
                     '--gameDir\n'
                     '/Users/pippo/github/shaderlod/run-workloads/port-263-runtime-r1/neoforge\n'
                     '--username\n'
                     'ShaderLOD\n'
                     '--fullscreen\n'
                     '--width\n'
                     '1920\n'
                     '--height\n'
                     '1200\n'
                     '--quickPlaySingleplayer\n'
                     'ShaderLOD-Landscape',
          'sha256': '14c51440686807c92eaffe3522e26de53fce2274aa9752f1ec847981ed223c9e'}}


class DevClientTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.profile = self.root / 'profiles' / 'on'
        (self.profile / 'config').mkdir(parents=True)
        (self.profile / 'shaderpacks').mkdir()
        world = self.profile / 'saves' / dev.WORLD
        world.mkdir(parents=True)
        (world / 'level.dat').write_bytes(b'world stays untouched')
        self.iris = self.profile / 'config/iris.properties'
        self.iris.write_text('shaderPack=quality.zip\nenableShaders=false\nmaxShadowRenderDistance=42\n')
        self.pack = self.profile / 'shaderpacks/quality.zip'
        self.make_pack(self.pack)
        Path(str(self.pack) + '.txt').write_text('SHADOW_QUALITY=HIGH\nTAA=true\n')
        (self.profile / 'options.txt').write_text('fullscreen:false\nrenderDistance:24\n')
        (self.profile.parent / 'control.json').write_text(json.dumps(dict(enabled=False, x=2, capture='stale')))
        (self.root / 'dev').mkdir()
        (self.root / "dependencies").mkdir(exist_ok=True)
        (self.root / 'dependencies/runtime.lock.json').write_text(json.dumps({'artifacts': {'distanthorizons': {'filename': 'dh.jar'}}}))
        self.root_patch = patch.object(dev, 'ROOT', self.root)
        self.root_patch.start()
        self.addCleanup(self.root_patch.stop)

    def make_pack(self, path):
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('shaders/shaders.properties', 'profile.HIGH=SHADOW_QUALITY:HIGH\n')
            archive.writestr('shaders/gbuffers_terrain.fsh', '#version 330\n')

    def args(self, **changes):
        values = dict(profile=self.profile, mode='on', pack=None, pack_options=None, dh=False,
                      already_built=False, ready_timeout=60, foreground=True)
        return argparse.Namespace(**(values | changes))

    def test_neutral_launch_omits_presence_triggered_diagnostic_paths(self):
        for mode in ('on', 'off'):
            with self.subTest(mode=mode):
                command = dev.plan(self.args(mode=mode))['command']
                for name in ('cameraQuality', 'pixelHistoryCapture', 'physicalResolution'):
                    self.assertFalse(any(arg == '-P' + name or arg.startswith('-P' + name + '=')
                                         for arg in command), name + ' must be absent, not blank/false')

    def test_explicit_locked_performance_stack_changes_only_runtime_selection(self):
        (self.root / 'dependencies/performance-mods.lock.json').write_text(json.dumps(
            {'stacks': {'minimal': [], 'optimized': ['modernfix', 'ferritecore', 'immediatelyfast'],
                        'optimized-moreculling': ['modernfix', 'ferritecore', 'immediatelyfast', 'moreculling', 'cloth-config']}}))
        default = dev.plan(self.args())
        optimized = dev.plan(self.args(performance_stack='optimized'))
        self.assertEqual(default['performance_stack'], 'minimal')
        self.assertEqual(optimized['performance_stack'], 'optimized')
        self.assertIn('-PperformanceStack=optimized', optimized['command'])
        self.assertEqual([p for p in default['command'] if not p.startswith('-PperformanceStack=')],
                         [p for p in optimized['command'] if not p.startswith('-PperformanceStack=')])
        culling = dev.plan(self.args(performance_stack='optimized-moreculling'))
        self.assertEqual(culling['performance_stack'], 'optimized-moreculling')
        self.assertIn('-PperformanceStack=optimized-moreculling', culling['command'])
        self.assertEqual([p for p in default['command'] if not p.startswith('-PperformanceStack=')],
                         [p for p in culling['command'] if not p.startswith('-PperformanceStack=')])
        with self.assertRaisesRegex(ValueError, 'Unknown performance stack'):
            dev.plan(self.args(performance_stack='unlocked'))

    def test_mac_default_background_preparation_disables_splash_with_backup(self):
        args = self.args()
        del args.foreground
        fml = self.profile / 'config/fml.toml'
        original = b'# Personal loader settings\nearlyWindowControl = true\nearlyWindowWidth = 854\n'
        fml.write_bytes(original)
        with patch.object(dev.sys, 'platform', 'darwin'):
            p = dev.plan(args)
        self.assertTrue(p['background_launch'])
        self.assertFalse(p['fullscreen'])
        self.assertIn('-PbackgroundLaunch=true', p['command'])
        dev.prepare(p, self.root / 'backup')
        self.assertIn('fullscreen:false', (self.profile / 'options.txt').read_text())
        self.assertIn('earlyWindowControl = false', fml.read_text())
        self.assertIn('earlyWindowWidth = 854', fml.read_text())
        self.assertEqual((self.root / 'backup/on/config/fml.toml').read_bytes(), original)

    def test_explicit_context_preparation_disables_reused_early_window_with_backup(self):
        p = dev.plan(self.args())
        p['background_launch'] = False
        p['requested_gl_context'] = '4.6'
        fml = self.profile / 'config/fml.toml'
        original = b'earlyWindowControl = true\nearlyWindowWidth = 854\n'
        fml.write_bytes(original)
        dev.prepare(p, self.root / 'backup')
        self.assertIn('earlyWindowControl = false', fml.read_text())
        self.assertIn('earlyWindowWidth = 854', fml.read_text())
        self.assertEqual(original, (self.root / 'backup/on/config/fml.toml').read_bytes())

    def test_windowed_custom_world_requires_neutral_control_and_safe_existing_save(self):
        world = self.profile / 'saves' / 'ShaderLOD-Smoke'
        world.mkdir()
        (world / 'level.dat').write_bytes(b'existing')
        control = self.root / 'probe.json'
        control.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'neutral benchmark control'):
            dev.plan(self.args(world='ShaderLOD-Smoke'))
        with self.assertRaisesRegex(ValueError, 'Safe saved-world'):
            dev.plan(self.args(world='../escape', benchmark_control_probe=control))
        plan = dev.plan(self.args(world='ShaderLOD-Smoke', benchmark_control_probe=control, windowed=True))
        self.assertFalse(plan['fullscreen'])
        self.assertTrue(plan['windowed'])
        self.assertIn('-PcomparisonFullscreen=false', plan['command'])
        self.assertIn('runClientLodOn', plan['command'])
        self.assertTrue(dev.plan(self.args())['fullscreen'])

    def test_background_readiness_does_not_claim_foreground_timing(self):
        text = ('Using shaderpack: quality.zip\nShaderLOD landscape ready: side=LOD, modPresent=true\n'
                'ShaderLOD background window ready: framebuffer=854x480, target=854x480, visible=0, focused=0, iconified=0\n')
        self.assertTrue(all(dev.readiness(text, 'quality.zip', 'on', background_launch=True).values()))
        self.assertFalse(all(dev.readiness(text, 'quality.zip', 'on').values()))
        self.assertFalse(all(dev.readiness(text.replace('focused=0', 'focused=1'), 'quality.zip', 'on', background_launch=True).values()))

    def test_neutral_benchmark_control_works_with_main_mod_absent(self):
        control = self.root / 'neutral.json'
        control.write_text('{"request_id":"initial","action":"status"}')
        p = dev.plan(self.args(mode='off', benchmark_control_probe=control))
        self.assertIn('runClientLodOff', p['command'])
        self.assertIn('-PbenchmarkControlProbe=' + str(control), p['command'])
        self.assertEqual(p['benchmark_control_probe'], str(control))
        self.assertIsNone(p['benchmark_tuner_probe'])

    def test_neutral_benchmark_refuses_two_control_owners(self):
        control = self.root / 'neutral.json'
        control.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'One benchmark control owner'):
            dev.plan(self.args(benchmark_control_probe=control, benchmark_tuner_probe=control))

    def test_combination_hooks_require_explicit_neutral_main_control_without_dh(self):
        control=self.root/'neutral.json';control.write_text('{}')
        normal=dev.plan(self.args(benchmark_control_probe=control));self.assertNotIn('-PshortlistCombinationRuntime=true',normal['command'])
        active=dev.plan(self.args(benchmark_control_probe=control,shortlist_combination_runtime=True));self.assertIn('-PshortlistCombinationRuntime=true',active['command'])
        for extra in [dict(mode='off'),dict(dh=True),dict(shortlist_combination_runtime='true')]:
            args=dict(benchmark_control_probe=control,shortlist_combination_runtime=True);args.update(extra)
            with self.assertRaisesRegex(ValueError,'combination runtime'):dev.plan(self.args(**args))

    def test_combined_g10_requires_combo_and_observer_and_records_flag(self):
        control=self.root/'combined-g10-control.json';control.write_text('{}')
        args=dict(benchmark_control_probe=control,shortlist_combination_runtime=True,g10_benchmark_runtime=True,shortlist_g10_runtime=True)
        actual=dev.plan(self.args(**args));self.assertIn('-PshortlistG10Runtime=true',actual['command'])
        for extra in [dict(shortlist_combination_runtime=False),dict(g10_benchmark_runtime=False),dict(shortlist_g10_runtime='true')]:
            invalid=dict(args);invalid.update(extra)
            with self.assertRaises(ValueError):dev.plan(self.args(**invalid))

    def test_g10_observer_hooks_require_explicit_neutral_main_control_without_dh(self):
        control=self.root/'neutral.json';control.write_text('{}')
        normal=dev.plan(self.args(benchmark_control_probe=control));self.assertNotIn('-Pg10BenchmarkRuntime=true',normal['command'])
        actual=dev.plan(self.args(benchmark_control_probe=control,g10_benchmark_runtime=True))
        self.assertIn('-Pg10BenchmarkRuntime=true',actual['command']);self.assertIn('-PfragmentWorkProbe=true',actual['command'])
        for extra in [dict(mode='off'),dict(dh=True),dict(g10_benchmark_runtime='true')]:
            args=dict(benchmark_control_probe=control,g10_benchmark_runtime=True);args.update(extra)
            with self.assertRaisesRegex(ValueError,'G10 runtime'):dev.plan(self.args(**args))

    def test_c01_hooks_require_explicit_neutral_main_control_without_dh(self):
        control=self.root/'neutral.json';control.write_text('{}')
        normal=dev.plan(self.args(benchmark_control_probe=control))
        self.assertNotIn('-Pc01Bounds=true',normal['command'])
        observed=dev.plan(self.args(benchmark_control_probe=control,c01_bounds_runtime=True))
        self.assertTrue(observed['c01_bounds_runtime'])
        self.assertIn('-Pc01Bounds=true',observed['command']);self.assertIn('-Pc01NativeR66=true',observed['command'])
        for extra in [dict(mode='off'),dict(dh=True),dict(c01_bounds_runtime='true')]:
            args=dict(benchmark_control_probe=control,c01_bounds_runtime=True);args.update(extra)
            with self.assertRaisesRegex(ValueError,'C01 runtime'):dev.plan(self.args(**args))

    def test_bsl_work_witness_hooks_are_explicit_and_require_main_control(self):
        control=self.root/'neutral.json';control.write_text('{}')
        ordinary=dev.plan(self.args(benchmark_control_probe=control))
        self.assertIn('-PbslMipWitnessRuntime=false',ordinary['command'])
        observed=dev.plan(self.args(benchmark_control_probe=control,bsl_mip_witness_runtime=True))
        self.assertTrue(observed['bsl_mip_witness_runtime'])
        self.assertIn('-PbslMipWitnessRuntime=true',observed['command'])
        with self.assertRaisesRegex(ValueError,'neutral control and main mod'):
            dev.plan(self.args(mode='off',benchmark_control_probe=control,bsl_mip_witness_runtime=True))

    def test_prepare_preserves_world_quality_and_backups(self):
        p = dev.plan(self.args())
        original_iris = self.iris.read_bytes()
        shader_options = Path(str(self.pack) + '.txt').read_bytes()
        backup = self.root / 'backup'
        dev.prepare(p, backup)
        self.assertEqual(dev.properties(self.iris.read_text()), dict(shaderPack='quality.zip',
                         enableShaders='true', maxShadowRenderDistance='42'))
        self.assertEqual(Path(str(self.pack) + '.txt').read_bytes(), shader_options)
        self.assertEqual((self.profile / 'saves' / dev.WORLD / 'level.dat').read_bytes(), b'world stays untouched')
        self.assertEqual((backup / 'on/config/iris.properties').read_bytes(), original_iris)
        self.assertIn('renderDistance:24', (self.profile / 'options.txt').read_text())
        self.assertIn('fullscreen:true', (self.profile / 'options.txt').read_text())
        control = json.loads((self.profile.parent / 'control.json').read_text())
        self.assertTrue(control['enabled'])
        self.assertNotIn('capture', control)

    def test_consumer_request_requires_apply_and_is_forwarded_before_launch(self):
        request = self.root / 'consumer.json'
        request.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'explicit On-task settings Apply'):
            dev.plan(self.args(consumer_uniforms=request))
        args = self.args(consumer_uniforms=request, apply_lod_settings=True, lod_state='on', pack='quality.zip')
        before = self.iris.read_bytes()
        p = dev.plan(args)
        self.assertIn('-PsettledConsumerUniforms=' + str(request.resolve()), p['command'])
        self.assertEqual(before, self.iris.read_bytes())
        request.write_bytes(b'x' * 8193)
        with self.assertRaisesRegex(ValueError, '8192'):
            dev.plan(args)

    def test_external_pack_and_adjacent_options_are_explicitly_copied(self):
        external = self.root / 'new pack.zip'
        self.make_pack(external)
        Path(str(external) + '.txt').write_text('QUALITY=ULTRA\n')
        dev.prepare(dev.plan(self.args(pack=str(external))), self.root / 'backup')
        installed = self.profile / 'shaderpacks' / external.name
        self.assertEqual(installed.read_bytes(), external.read_bytes())
        self.assertEqual(Path(str(installed) + '.txt').read_text(), 'QUALITY=ULTRA\n')
        self.assertEqual(dev.properties(self.iris.read_text())['shaderPack'], external.name)
        self.assertTrue(self.pack.exists())

    def test_requested_lod_preset_keeps_shader_quality_options(self):
        shader_options = Path(str(self.pack) + '.txt').read_bytes()
        original_control = (self.profile.parent / 'control.json').read_bytes()
        backup = self.root / 'preset-backup'
        dev.prepare(dev.plan(self.args(preset='QUALITY')), backup)
        self.assertEqual(json.loads((self.profile.parent / 'control.json').read_text())['preset'], 'QUALITY')
        self.assertEqual(Path(str(self.pack) + '.txt').read_bytes(), shader_options)
        self.assertEqual((backup / 'control.json').read_bytes(), original_control)

    def test_apply_settings_requests_backend_with_original_pack_quality_options(self):
        original = self.root / 'original-source.zip'
        self.make_pack(original)
        Path(str(original) + '.txt').write_text('QUALITY=ULTRA\nSHADOW_QUALITY=HIGH\n')
        p = dev.plan(self.args(apply_lod_settings=True, lod_state='off', pack=str(original), preset='QUALITY'))
        self.assertEqual(p['mode'], 'on')
        self.assertEqual(p['lod_state'], 'off')
        self.assertTrue(p['apply_lod_settings'])
        self.assertEqual(p['pack'], str(original.resolve()))
        self.assertEqual(p['pack_options'], str(original.resolve()) + '.txt')
        self.assertIn('runClientLodOn', p['command'])
        dev.prepare(p, self.root / 'backend-backup')
        control = json.loads((self.profile.parent / 'control.json').read_text())
        self.assertTrue(control['applyPackSettings'])
        self.assertFalse(control['enabled'])
        self.assertEqual(control['preset'], 'QUALITY')
        self.assertEqual(dev.properties(self.iris.read_text())['shaderPack'], original.name)
        self.assertEqual((self.profile / 'shaderpacks' / (original.name + '.txt')).read_text(),
                         'QUALITY=ULTRA\nSHADOW_QUALITY=HIGH\n')
        self.assertEqual(original.read_bytes(), (self.profile / 'shaderpacks' / original.name).read_bytes())

    def test_normal_launch_clears_previous_apply_request(self):
        control_path = self.profile.parent / 'control.json'
        control_path.write_text(json.dumps(dict(enabled=False, applyPackSettings=True, x=2, preset='BALANCED')))
        p = dev.plan(self.args())
        self.assertFalse(p['apply_lod_settings'])
        self.assertIsNone(p['lod_state'])
        dev.prepare(p, self.root / 'normal-backup')
        control = json.loads(control_path.read_text())
        self.assertNotIn('applyPackSettings', control)
        self.assertTrue(control['enabled'])
        self.assertEqual(control['preset'], 'BALANCED')

    def test_apply_settings_rejects_mod_absent_and_incomplete_requests(self):
        with self.assertRaisesRegex(ValueError, 'On task'):
            dev.plan(self.args(mode='off', apply_lod_settings=True, lod_state='off', pack=str(self.pack)))
        with self.assertRaisesRegex(ValueError, 'requires --apply'):
            dev.plan(self.args(lod_state='off'))
        with self.assertRaisesRegex(ValueError, 'requires --lod-state'):
            dev.plan(self.args(apply_lod_settings=True, pack=str(self.pack)))
        with self.assertRaisesRegex(ValueError, 'source --pack'):
            dev.plan(self.args(apply_lod_settings=True, lod_state='on'))
        (self.profile.parent / 'control.json').unlink()
        with self.assertRaisesRegex(ValueError, 'control.json'):
            dev.plan(self.args(apply_lod_settings=True, lod_state='on', pack=str(self.pack)))

    def test_missing_selected_zip_refused_before_config_changes(self):
        before = self.iris.read_bytes()
        with self.assertRaisesRegex(ValueError, 'ZIP missing'):
            dev.plan(self.args(pack='missing.zip'))
        self.assertEqual(self.iris.read_bytes(), before)

    def test_invalid_zip_and_profile_mods_refused(self):
        with zipfile.ZipFile(self.pack, 'a') as archive:
            archive.writestr('../outside', 'bad')
        with self.assertRaisesRegex(ValueError, 'Unsafe'):
            dev.plan(self.args())
        self.make_pack(self.pack)
        (self.profile / 'mods').mkdir()
        (self.profile / 'mods/unmanaged.jar').write_bytes(b'foreign')
        with self.assertRaisesRegex(ValueError, 'JARs disagree'):
            dev.plan(self.args())

    def test_default_gradle_compiles_and_has_no_auto_exit(self):
        command = dev.plan(self.args())['command']
        self.assertNotIn('-x', command)
        self.assertIn('-PcomparisonFullscreen=true', command)
        for flag in ('-PgpuBenchmark=false', '-PmeasureWorkload=false', '-PsmokeTest=false'):
            self.assertIn(flag, command)
        self.assertFalse(any('measurementSeconds' in item or 'initialWarmup' in item for item in command))

    def test_already_built_refuses_source_changes(self):
        receipt = self.root / 'receipts'
        receipt.mkdir()
        (receipt / 'built.json').write_text('{"source": "old"}')
        for directory in ('src/devClient/resources', 'build/resources/devClient'):
            target = self.root / directory / 'shaderlod-dev.mixins.json'
            target.parent.mkdir(parents=True)
            target.write_text('{}')
        with patch.object(dev, 'LOGS', receipt), patch.object(dev, 'build_fingerprint', return_value={'source': 'new'}):
            with self.assertRaisesRegex(ValueError, 'changed'):
                dev.validate_built()

    def test_readiness_requires_selected_latest_pack_world_and_actual_fullscreen(self):
        text = ('Using shaderpack: quality.zip\nShaderLOD landscape ready: side=LOD, modPresent=true\n'
                'ShaderLOD inspection native fullscreen: true, monitor=1, framebuffer=1920x1200, target=1920x1200, focused=true\n')
        self.assertTrue(all(dev.readiness(text, 'quality.zip', 'on').values()))
        self.assertFalse(all(dev.readiness(text.replace('fullscreen: true', 'fullscreen: false'), 'quality.zip', 'on').values()))
        self.assertFalse(all(dev.readiness(text + 'Using shaderpack: wrong.zip\n', 'quality.zip', 'on').values()))
        self.assertFalse(all(dev.readiness('Using shaderpack: quality.zip', 'quality.zip', 'on').values()))
        self.assertTrue(dev.FATAL.search('Failed to create shader rendering pipeline'))

    def test_fullscreen_requires_native_monitor_and_matching_positive_targets(self):
        prefix = 'Using shaderpack: quality.zip\nShaderLOD landscape ready: side=LOD, modPresent=true\n'
        requested = 'ShaderLOD inspection requested fullscreen: true\n'
        self.assertFalse(dev.readiness(prefix + requested, 'quality.zip', 'on')['fullscreen'])
        for proof in (
            'monitor=0, framebuffer=1920x1200, target=1920x1200',
            'monitor=1, framebuffer=1920x1200, target=1488x836',
            'monitor=1, framebuffer=0x0, target=0x0',
        ):
            self.assertFalse(dev.readiness(prefix + 'ShaderLOD inspection native fullscreen: true, '
                + proof + '\n', 'quality.zip', 'on')['fullscreen'])

    def test_backend_readiness_waits_for_applied_generated_pack_and_request_state(self):
        initial = ('Using shaderpack: original-source.zip\n'
                   'ShaderLOD landscape ready: side=LOD, modPresent=true\n'
                   'ShaderLOD inspection native fullscreen: true, monitor=1, framebuffer=1920x1200, target=1920x1200, focused=true\n')
        request = dict(apply_lod_settings=True, lod_state='on', preset='BALANCED')
        self.assertFalse(all(dev.readiness(initial, 'original-source.zip', 'on', **request).values()))
        event = 'ShaderLOD explicit settings applied: pack=generated-lod.zip, enabled=true, preset=BALANCED\n'
        self.assertFalse(all(dev.readiness(initial + event, 'original-source.zip', 'on', **request).values()))
        loaded = initial + event + 'Using shaderpack: generated-lod.zip\n'
        self.assertTrue(all(dev.readiness(loaded, 'original-source.zip', 'on', **request).values()))
        self.assertEqual(dev.applied_settings(loaded), dict(pack='generated-lod.zip', enabled=True, preset='BALANCED'))
        self.assertFalse(all(dev.readiness(loaded, 'original-source.zip', 'on', **(request | {'preset': 'QUALITY'})).values()))
        self.assertFalse(all(dev.readiness(loaded, 'original-source.zip', 'on', **(request | {'lod_state': 'off'})).values()))
        self.assertFalse(all(dev.readiness(loaded + 'Using shaderpack: wrong.zip\n', 'original-source.zip', 'on', **request).values()))

    def test_backend_readiness_off_requires_main_mod_and_latest_applied_event(self):
        text = ('Using shaderpack: original-source.zip\n'
                'ShaderLOD landscape ready: side=baseline, modPresent=true\n'
                'ShaderLOD inspection native fullscreen: true, monitor=1, framebuffer=1920x1200, target=1920x1200, focused=true\n'
                'ShaderLOD explicit settings applied: pack=original-source.zip, enabled=false, preset=QUALITY\n')
        request = dict(apply_lod_settings=True, lod_state='off', preset='QUALITY')
        self.assertTrue(all(dev.readiness(text, 'original-source.zip', 'on', **request).values()))
        self.assertFalse(all(dev.readiness(text.replace('modPresent=true', 'modPresent=false'), 'original-source.zip', 'on', **request).values()))
        changed = text + 'ShaderLOD explicit settings applied: pack=generated-lod.zip, enabled=true, preset=QUALITY\n'
        self.assertFalse(all(dev.readiness(changed, 'original-source.zip', 'on', **request).values()))
        self.assertTrue(dev.FATAL.search('ShaderLOD explicit settings failed: unsupported source'))

    def session(self, proofs):
        session = self.root / 'session'
        session.mkdir()
        dev.write_json(session / 'owner.json', dict(plan={'profile': str(self.profile)}, clients=proofs))
        return session

    def test_native_argv_preserves_apostrophe_and_space_in_property(self):
        game = dict(pid=12, ppid=4, birth='observed', argv="java -Dpack=Sildur's Vibrant Shaders.zip net.fabricmc.devlaunchinjector.Main --gameDir " + str(self.profile))
        arguments = ['java', "-Dpack=Sildur's Vibrant Shaders.zip", 'net.fabricmc.devlaunchinjector.Main', '--gameDir', str(self.profile)]
        with patch.object(dev.sys, 'platform', 'darwin'), patch.object(dev, 'mac_process_argv', return_value=arguments), patch.object(dev.subprocess, 'run') as fallback:
            self.assertTrue(dev.associated(game, self.profile)); fallback.assert_not_called()

    def test_native_profile_mismatch_duplicate_and_property_text_refuse_without_cwd_fallback(self):
        game = dict(pid=12, ppid=4, birth='observed', argv='java net.fabricmc.devlaunchinjector.Main')
        vectors = [
            ['java', '--gameDir', str(self.root / 'wrong')],
            ['java', '--gameDir', str(self.profile), '--gameDir', str(self.profile)],
            ['java', '-Dfake=--gameDir ' + str(self.profile)],
            ['java', '--gameDir'],
        ]
        for arguments in vectors:
            with patch.object(dev.sys, 'platform', 'darwin'), patch.object(dev, 'mac_process_argv', return_value=arguments), patch.object(dev.subprocess, 'run') as fallback:
                self.assertFalse(dev.associated(game, self.profile)); fallback.assert_not_called()

    def test_exact_native_profile_supports_space_without_shell_interpretation(self):
        profile = self.root / 'profile with spaces'
        self.assertTrue(dev.argv_profile_matches(['java', '--gameDir', str(profile)], profile.resolve()))
        self.assertFalse(dev.argv_profile_matches(['java', '--gameDir=' + str(profile)], profile.resolve()))

    def neo_argfile(self, text=None, path=None):
        path = path or self.root / 'build/moddev/clientLodOnRunProgramArgs.txt'
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text or 'cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n.\n')
        vm = path.with_name(path.name.replace('ProgramArgs.txt', 'VmArgs.txt'))
        vm.write_text('# User JVM Arguments\n-Dfile.encoding=UTF-8\n')
        game = dict(pid=12, ppid=4, birth=time.ctime(), argv='authentic-shaped NeoForge Java process')
        argv = ['java', '@' + str(vm),
                'net.neoforged.devlaunch.Main', '@' + str(path)]
        return game, argv, path

    def neo_associated(self, game, arguments, profile=None, cwd=None):
        with patch.object(dev.sys, 'platform', 'darwin'), \
             patch.object(dev, 'mac_process_argv', return_value=arguments), \
             patch.object(dev, 'process_cwd', return_value=cwd or (profile or self.profile)), \
             patch.object(dev, 'process_table', return_value={game['pid']: game}):
            return dev.associated(game, profile or self.profile)

    def test_neoforge_authentic_program_argfile_and_relative_game_dir_require_exact_cwd(self):
        game, argv, _ = self.neo_argfile(NEOFORGE_PROGRAM_ARGS)
        self.assertTrue(self.neo_associated(game, argv))
        self.assertFalse(self.neo_associated(game, argv, cwd=self.root))
        game, argv, _ = self.neo_argfile(NEOFORGE_PROGRAM_ARGS,
                self.root / 'build/moddev/clientLodOffRunProgramArgs.txt')
        self.assertTrue(self.neo_associated(game, argv))

    def test_unavailable_native_argv_never_uses_a_broad_cwd_fallback(self):
        game = dict(pid=12, ppid=4, birth='observed', argv='java net.neoforged.devlaunch.Main @unknown')
        with patch.object(dev.sys, 'platform', 'darwin'), \
             patch.object(dev, 'mac_process_argv', return_value=None), patch.object(dev.subprocess, 'run') as cwd:
            self.assertFalse(dev.associated(game, self.profile)); cwd.assert_not_called()

    def test_packaged_main_uses_the_same_exact_argfile_bootstrap_and_cwd_contract(self):
        game, argv, path = self.neo_argfile(NEOFORGE_PROGRAM_ARGS,
                self.root / 'build/moddev/packagedMainRunProgramArgs.txt')
        self.assertTrue(self.neo_associated(game, argv))
        self.assertFalse(self.neo_associated(game, argv, cwd=self.root))
        path.write_text(path.read_text().replace('cpw.mods.bootstraplauncher.BootstrapLauncher',
                'unrecognized.Bootstrap'))
        self.assertFalse(self.neo_associated(game, argv))

    def test_different_four_port_argfile_producers_are_not_inferred_from_root_grammar(self):
        for version in ('1.21.11', '26.1.2', '26.2', '26.3'):
            relative = Path('ports') / version / 'neoforge/build/moddev/clientRunProgramArgs.txt'
            # Observed current port files use this bootstrap and omit gameDir entirely.
            text = '# Main Class\nnet.neoforged.fml.startup.Client\n--username\nShaderLOD\n'
            game, argv, _ = self.neo_argfile(text, self.root / relative)
            self.assertFalse(self.neo_associated(game, argv), version)

    def test_neoforge_program_argfile_changes_missing_foreign_and_nested_refuse(self):
        game, argv, path = self.neo_argfile()
        self.assertTrue(self.neo_associated(game, argv))
        path.write_text(path.read_text() + '--version\nforeign\n')
        self.assertFalse(self.neo_associated(game, argv))
        path.unlink(); self.assertFalse(self.neo_associated(game, argv))
        foreign = self.root / 'foreign.txt'; foreign.write_text('cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n.\n')
        self.assertFalse(self.neo_associated(game, argv[:-1] + ['@' + str(foreign)]))
        game, argv, _ = self.neo_argfile('@nested.txt\n--gameDir\n.\n')
        self.assertFalse(self.neo_associated(game, argv))

    def test_neoforge_program_and_direct_conflicts_missing_duplicate_and_embedded_property_refuse(self):
        for body in ('cpw.mods.bootstraplauncher.BootstrapLauncher\n',
                     'cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n',
                     'cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n.\n--gameDir\n.\n',
                     'cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n.\n--gameDir=foreign\n',
                     'cpw.mods.bootstraplauncher.BootstrapLauncher\n-Dfake=--gameDir\n.\n'):
            game, argv, _ = self.neo_argfile(body)
            self.assertFalse(self.neo_associated(game, argv), body)
        game, argv, _ = self.neo_argfile()
        self.assertFalse(self.neo_associated(game, argv + ['--gameDir', str(self.profile)]))
        self.assertFalse(self.neo_associated(game, argv[:1] + ['-Dfake=--gameDir ' + str(self.profile)] + argv[1:]))

    def test_neoforge_java_quoted_profile_and_escapes_are_not_shell_grammar(self):
        profile = self.root / 'profile with Sildur\'s "quote"'
        encoded = '"' + str(profile).replace('\\', '\\\\').replace('"', '\\"') + '"'
        game, argv, _ = self.neo_argfile('cpw.mods.bootstraplauncher.BootstrapLauncher\n--gameDir\n' + encoded + '\n')
        self.assertTrue(self.neo_associated(game, argv, profile=profile))
        self.assertEqual(dev.neoforge_program_arguments('"a\\tb"\n"a\\141b"\n'), ['a\tb', 'aab'])
        for malformed in ('"unterminated\n', 'one two\n', 'one#comment\n', '"a"suffix\n'):
            with self.assertRaises(ValueError): dev.neoforge_program_arguments(malformed)

    def test_neoforge_future_modified_argfile_and_process_identity_reuse_refuse(self):
        game, argv, path = self.neo_argfile()
        dev.os.utime(path, (time.time() + 20, time.time() + 20))
        self.assertFalse(self.neo_associated(game, argv))
        game, argv, _ = self.neo_argfile()
        with patch.object(dev, 'process_cwd', return_value=self.profile), \
             patch.object(dev, 'process_table', return_value={12: game | {'birth': 'reused'}}):
            self.assertFalse(dev.neoforge_argfile_profile_matches(game, argv, self.profile))

    def test_relative_root_game_directory_rejects_native_or_vm_user_directory_overrides(self):
        game, argv, path = self.neo_argfile()
        self.assertFalse(self.neo_associated(game, argv[:1] + ['-Duser.dir=' + str(self.profile)] + argv[1:]))
        vm = path.with_name(path.name.replace('ProgramArgs.txt', 'VmArgs.txt'))
        vm.write_text('-Duser.dir=' + str(self.profile) + '\n')
        self.assertFalse(self.neo_associated(game, argv))
        vm.unlink(); self.assertFalse(self.neo_associated(game, argv))

    def test_actual_devlaunch_tokenizer_differential_cases_preserve_both_quote_characters(self):
        # These accepted outputs were replayed through pinned DevLaunch1.0.2's
        # private expandValidArgumentFile; see tokenizer-differential.json.
        for text, expected in (
                ('"profile with Sildur\'s space"\n', ["profile with Sildur's space"]),
                ("'profile with spaces'\n", ['profile with spaces']),
                ('"a\\tb"\n"a\\141b"\n', ['a\tb', 'aab']),
                ('"profile with \\"quote\\""\n', ['profile with "quote"'])):
            self.assertEqual(dev.neoforge_program_arguments(text), expected)
        # Java splits mixed forms or discards inline comments; generated subset refuses.
        for text in ("Sildur's\n", 'word#comment\n', '"one"suffix\n'):
            with self.assertRaises(ValueError): dev.neoforge_program_arguments(text)

    def port_neo_argfile(self, version):
        fixture = PORT_PROGRAM_ARGS[version]
        profile = self.root / ('port profile ' + version + " Sildur's")
        encoded = '"' + str(profile).replace('\\', '\\\\').replace('"', '\\"') + '"'
        # Only relocate the genuine producer's directory line; every other byte stays genuine.
        marker = '\n' + fixture['profile'] + '\n'
        self.assertEqual(fixture['program'].count(marker), 1)
        text = fixture['program'].replace(marker, '\n' + encoded + '\n')
        path = self.root / 'ports' / version / 'neoforge/build/moddev/clientRunProgramArgs.txt'
        game, argv, path = self.neo_argfile(text, path)
        return game, argv, path, profile

    def test_all_four_genuine_regenerated_port_program_files_require_absolute_profile_and_cwd(self):
        for version in PORT_PROGRAM_ARGS:
            game, argv, _, profile = self.port_neo_argfile(version)
            self.assertTrue(self.neo_associated(game, argv, profile=profile), version)
            self.assertFalse(self.neo_associated(game, argv, profile=profile, cwd=self.root), version)
            self.assertFalse(self.neo_associated(game, argv, profile=self.profile, cwd=self.profile), version)

    def test_four_port_missing_duplicate_relative_foreign_bootstrap_and_changed_files_refuse(self):
        for version in PORT_PROGRAM_ARGS:
            game, argv, path, profile = self.port_neo_argfile(version)
            original = path.read_text()
            for bad in (
                    original.replace('--gameDir\n', '--notGameDir\n'),
                    original + '\n--gameDir\n' + str(profile) + '\n',
                    original.replace('net.neoforged.fml.startup.Client', 'cpw.mods.bootstraplauncher.BootstrapLauncher'),
                    'net.neoforged.fml.startup.Client\n--gameDir\n.\n',
                    'net.neoforged.fml.startup.Client\n--gameDir\nrelative\n'):
                path.write_text(bad)
                self.assertFalse(self.neo_associated(game, argv, profile=profile), version)
            game, argv, path, profile = self.port_neo_argfile(version)
            self.assertTrue(self.neo_associated(game, argv, profile=profile), version)
            path.write_text(path.read_text() + '\n--username\nforeign\n')
            self.assertFalse(self.neo_associated(game, argv, profile=profile), version)
            unknown = path.parent / 'foreignProgramArgs.txt'; unknown.write_text(original)
            self.assertFalse(self.neo_associated(game, argv[:-1] + ['@' + str(unknown)], profile=profile), version)

    def test_stop_refuses_pid_reuse_or_wrong_profile(self):
        game = dict(pid=12, ppid=4, birth='before', argv='java net.neoforged.devlaunch.Main')
        session = self.session({'12': dev.identity(game)})
        with patch.object(dev, 'process_table', return_value={12: game | {'birth': 'reused'}}), patch.object(dev.os, 'kill') as kill:
            with self.assertRaisesRegex(ValueError, 'changed'):
                dev.stop(session)
            kill.assert_not_called()
        with patch.object(dev, 'process_table', return_value={12: game}), patch.object(dev, 'associated', return_value=False), patch.object(dev.os, 'kill') as kill:
            with self.assertRaisesRegex(ValueError, 'changed'):
                dev.stop(session)
            kill.assert_not_called()

    def test_stop_signals_only_verified_game_and_preserves_gradle(self):
        game = dict(pid=12, ppid=4, birth='now', argv='java net.neoforged.devlaunch.Main')
        daemon = dict(pid=67529, ppid=1, birth='old', argv='java org.gradle.launcher.daemon.bootstrap.GradleDaemon')
        session = self.session({'12': dev.identity(game)})
        table = {12: game, 67529: daemon}
        with patch.object(dev, 'process_table', side_effect=[table, table, {67529: daemon}]), patch.object(dev, 'associated', return_value=True), patch.object(dev.os, 'kill') as kill:
            dev.stop(session)
            kill.assert_called_once_with(12, dev.signal.SIGTERM)
        self.assertEqual(dev.clients({67529: daemon}), {})

    def test_absolute_stop_is_opt_in_and_uses_monotonic_target(self):
        self.assertIsNone(dev.plan(self.args())['stop_at_utc'])
        self.assertIsNone(dev.stop_deadline(None))
        with patch.object(dev.time, 'time', return_value=100), \
             patch.object(dev.time, 'monotonic', return_value=500):
            self.assertEqual(dev.stop_deadline('1970-01-01T00:02:00Z'), 520)
            self.assertEqual(dev.stop_deadline('1969-12-31T16:02:00-08:00'), 520)
            for value in ('1970-01-01T00:02:00', 'not a date', '1970-01-01T00:01:40Z',
                          '1970-01-02T00:02:00Z'):
                with self.assertRaises(ValueError):
                    dev.plan(self.args(stop_at=value))
        self.assertEqual(dev.properties(self.iris.read_text())['enableShaders'], 'false')

    def test_deadline_stop_publishes_fresh_proofs_and_signals_only_owned_client(self):
        game = dict(pid=12, ppid=4, birth='now', argv='java net.neoforged.devlaunch.Main')
        daemon = dict(pid=67529, ppid=1, birth='old', argv='java org.gradle.launcher.daemon.bootstrap.GradleDaemon')
        session = self.session({})
        state = json.loads((session / 'owner.json').read_text())
        state['clients'] = {'12': dev.identity(game)}
        table = {12: game, 67529: daemon}
        with patch.object(dev.time, 'monotonic', return_value=520), \
             patch.object(dev.time, 'time', return_value=-10000), \
             patch.object(dev, 'process_table', side_effect=[table, table, {67529: daemon}]), \
             patch.object(dev, 'associated', return_value=True), patch.object(dev.os, 'kill') as kill:
            dev.deadline_stop(session, state, 520)
            kill.assert_called_once_with(12, dev.signal.SIGTERM)
        saved = json.loads((session / 'owner.json').read_text())
        self.assertEqual(saved['clients'], state['clients'])
        self.assertIn('deadline_stop_requested_utc', saved)
        self.assertIn('deadline_stop_completed_utc', saved)
        self.assertNotIn('deadline_stop_error', saved)
        with patch.object(dev, 'stop') as stop:
            dev.deadline_stop(session, state, 520)
            stop.assert_not_called()

    def test_deadline_stop_refuses_reused_pid_without_retry_or_early_stop(self):
        game = dict(pid=12, ppid=4, birth='before', argv='java net.neoforged.devlaunch.Main')
        session = self.session({'12': dev.identity(game)})
        state = json.loads((session / 'owner.json').read_text())
        with patch.object(dev.time, 'monotonic', return_value=519), patch.object(dev, 'stop') as stop:
            dev.deadline_stop(session, state, 520)
            dev.deadline_stop(session, state, None)
            stop.assert_not_called()
        with patch.object(dev.time, 'monotonic', return_value=520), \
             patch.object(dev, 'process_table', return_value={12: game | {'birth': 'reused'}}), \
             patch.object(dev.os, 'kill') as kill:
            dev.deadline_stop(session, state, 520)
            dev.deadline_stop(session, state, 520)
            kill.assert_not_called()
        self.assertIn('identity/profile changed', state['deadline_stop_error'])
        self.assertNotIn('deadline_stop_completed_utc', state)

    def test_supervisor_deadline_survives_discovery_failure_without_busy_loop(self):
        p = dev.plan(self.args())
        p['stop_at_utc'] = '1970-01-01T00:00:10Z'
        session = self.root / 'deadline-supervised'
        session.mkdir()
        state = dict(status='pending', plan=p, clients={})
        supervisor = dict(pid=dev.os.getpid(), ppid=1, birth='now', argv='python dev_client.py')
        launcher = dict(pid=80, ppid=supervisor['pid'], birth='now', argv='GradleWrapperMain')
        game = dict(pid=81, ppid=67529, birth='now', argv='java net.neoforged.devlaunch.Main')
        daemon = dict(pid=67529, ppid=1, birth='old', argv='java org.gradle.launcher.daemon.bootstrap.GradleDaemon')
        live = {80: launcher, 81: game, 67529: daemon}
        gone = {80: launcher, 67529: daemon}
        with patch('run_storage.admit'), patch.object(dev, 'stop_deadline', return_value=10), \
             patch.object(dev, 'source_fingerprint', return_value={'source': 'current'}), \
             patch.object(dev.subprocess, 'Popen') as popen, \
             patch.object(dev, 'process_table', side_effect=[{}, {supervisor['pid']: supervisor},
                          live, RuntimeError('probe unavailable at deadline'), live, live, gone, gone]), \
             patch.object(dev, 'associated', return_value=True), \
             patch.object(dev.os, 'getsid', return_value=80), \
             patch.object(dev.time, 'monotonic', side_effect=[0, 8, 8, 8, 10, 10, 10]), \
             patch.object(dev.time, 'sleep') as sleep, patch.object(dev.os, 'kill') as kill:
            popen.return_value.pid = 80
            popen.return_value.poll.side_effect = [None, 0]
            popen.return_value.returncode = 0
            dev.supervise_locked(session, state)
            kill.assert_called_once_with(81, dev.signal.SIGTERM)
            self.assertTrue(all(call.args[0] > 0 for call in sleep.call_args_list))
        saved = json.loads((session / 'owner.json').read_text())
        self.assertIn('deadline_stop_requested_utc', saved)
        self.assertIn('deadline_stop_completed_utc', saved)
        self.assertIn('probe unavailable at deadline', saved['error'])

    def test_deadline_receipt_failure_does_not_release_ownership_or_signal(self):
        session = self.session({})
        state = json.loads((session / 'owner.json').read_text())
        with patch.object(dev.time, 'monotonic', return_value=10), \
             patch.object(dev, 'write_json', side_effect=OSError('receipt unavailable')), \
             patch.object(dev.os, 'kill') as kill:
            dev.deadline_stop(session, state, 10)
            kill.assert_not_called()
        self.assertIn('receipt unavailable', state['deadline_stop_error'])
        self.assertIn('receipt unavailable', state['deadline_stop_receipt_error'])

    def test_ready_capture_is_opt_in_and_preserves_scene_control(self):
        session = self.session({})
        state = json.loads((session / 'owner.json').read_text())
        control = self.profile.parent / 'control.json'
        before = control.read_bytes()
        dev.ready_capture(session, state)
        self.assertEqual(control.read_bytes(), before)
        state['plan']['capture_when_ready'] = 'native-original'
        dev.ready_capture(session, state)
        self.assertEqual(json.loads(control.read_text()), json.loads(before) | {'capture': 'native-original'})
        self.assertIn('ready_capture_requested_utc', state)
        captured = control.read_bytes()
        dev.ready_capture(session, state)
        self.assertEqual(control.read_bytes(), captured)

    def test_unsafe_ready_capture_refused_before_any_launch_or_profile_mutation(self):
        session = self.session({})
        before = self.iris.read_bytes()
        for name in ('../outside', '', 'x' * 65, 123):
            state = dict(plan=dev.plan(self.args()) | {'capture_when_ready': name}, clients={}, status='pending')
            with patch.object(dev.subprocess, 'Popen') as popen:
                with self.assertRaisesRegex(ValueError, 'safe'):
                    dev.supervise_locked(session, state)
                popen.assert_not_called()
            self.assertEqual(self.iris.read_bytes(), before)

    def test_ready_capture_refuses_apply_or_on_before_mutation(self):
        session = self.session({})
        before = self.iris.read_bytes()
        for request in ({'mode': 'on'}, {'mode': 'off', 'apply_lod_settings': True}):
            state = dict(plan=dev.plan(self.args()) | request | {'capture_when_ready': 'original-still'},
                         clients={}, status='pending')
            with patch.object(dev.subprocess, 'Popen') as popen:
                with self.assertRaisesRegex(ValueError, 'original-Off'):
                    dev.supervise_locked(session, state)
                popen.assert_not_called()
            self.assertEqual(self.iris.read_bytes(), before)

    def test_supervisor_recovers_probe_failure_and_keeps_game_until_exit(self):
        p = dev.plan(self.args())
        session = self.root / 'supervised'
        session.mkdir()
        state = dict(status='pending', plan=p, clients={})
        supervisor = dict(pid=dev.os.getpid(), ppid=1, birth='now', argv='python dev_client.py')
        launcher = dict(pid=80, ppid=supervisor['pid'], birth='now', argv='GradleWrapperMain')
        game = dict(pid=81, ppid=67529, birth='now', argv='java net.neoforged.devlaunch.Main')
        with patch('run_storage.admit'), patch.object(dev, 'source_fingerprint', return_value={'source': 'current'}), \
             patch.object(dev.subprocess, 'Popen') as popen, \
             patch.object(dev, 'process_table', side_effect=[{}, {supervisor['pid']: supervisor},
                          RuntimeError('probe unavailable'), {80: launcher, 81: game}, {}]), \
             patch.object(dev, 'associated', return_value=True), \
             patch.object(dev.os, 'getsid', return_value=80), \
             patch.object(dev.time, 'sleep') as sleep, \
             patch.object(dev.os, 'kill') as kill:
            popen.return_value.pid = 80
            popen.return_value.returncode = 0
            popen.return_value.poll.side_effect = [None, 0]
            dev.supervise_locked(session, state)
            self.assertGreaterEqual(sleep.call_count, 2)
            kill.assert_not_called()
        self.assertEqual(state['clients']['81'], dev.identity(game))
        self.assertEqual(state['status'], 'startup-failed')
        self.assertIn('probe unavailable', state['error'])

    @unittest.skipIf(dev.os.name == 'nt', 'Unix shared flock protocol')
    def test_existing_shared_runtime_lock_refuses_competing_launcher(self):
        with patch.object(dev, 'LOCK', self.root / 'runtime.lock'):
            with dev.runtime_lock():
                with self.assertRaisesRegex(ValueError, 'already owned'):
                    with dev.runtime_lock():
                        self.fail('Second lock acquired')


if __name__ == '__main__':
    unittest.main()
