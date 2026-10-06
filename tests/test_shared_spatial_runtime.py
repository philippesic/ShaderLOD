"""Frozen general spatial control qualification; no build or interactive client."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest import mock
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_shared_spatial_runtime as prep


class SharedSpatialRuntimeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)

    def mutated_archive(self, change):
        path=ROOT/'build/Photon-SharedSpatial-enabled.zip'
        members=prep.adapter.archive_members(path); change(members)
        target=self.root/'changed.zip'
        with zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as z:
            for name,data in members.items(): z.writestr(name,data)
        return target

    def test_actual_source_and_full_forest_option_inventory(self):
        qualified=prep.qualify(); scene=prep.inspect_scene(qualified=qualified)
        self.assertTrue(qualified['qualified']); self.assertEqual(len(scene['upstream_effective_options']),366)
        self.assertEqual(len(scene['native_options']),354)
        self.assertEqual(scene['native_options'] | scene['option_evidence']['omitted_non_settings_defaults'],scene['upstream_effective_options'])
        self.assertEqual(scene['control']['time'],7000)
        self.assertEqual(scene['control']['yaw'],135.)
        for arm,row in qualified['artifacts'].items():
            self.assertEqual(row['sha256'],prep.ARCHIVES[arm])
            self.assertEqual(len(row['metadata']['reference_member_sha256']),10 if arm=='original' else 25)

    def test_fresh_ignored_four_control_plan_and_untimed_commands(self):
        target=self.root/'run-workloads/new'
        result=prep.plan(target,root=self.root)
        self.assertFalse(target.exists()); self.assertEqual(list(result['scene_operations'][0]['target_profiles']),list(prep.ARMS))
        self.assertTrue(result['diagnostic_only']); self.assertFalse(result['performanceEvidence'])
        self.assertEqual(result['manifest'],str(self.root/prep.adapter.MANIFEST))
        args=prep.command(target/'forest/privateON-ADAPTIVE')
        for flag in ('gpuTiming','cpuTiming','cpuLayerTiming','gpuPassTiming','workTrace'):
            self.assertIn('-P'+flag+'=false',args)
        self.assertIn('-PcloudSamplerProbe=true',args)
        with self.assertRaisesRegex(ValueError,'ignored'):prep.plan(self.root/'tracked',root=self.root)
        target.mkdir(parents=True)
        with self.assertRaisesRegex(ValueError,'fresh'):prep.plan(target,root=self.root)

    def test_member_inventory_and_license_mutations_rejected(self):
        for mutate,error in (
            (lambda m:m.update(extra=b'extra'),'inventory'),
            (lambda m:m.update(LICENSE=b'changed'),'license'),
            (lambda m:m.update({'shaders/program/shaderlod_spatial_prepass.fsh':b'changed'}),'member bytes')):
            with self.subTest(error=error):
                target=self.mutated_archive(mutate)
                with self.assertRaisesRegex(ValueError,error):prep.qualify(paths={'enabled':target})

    def test_complete_metadata_layout_and_options_mutations_rejected(self):
        for field,value in (('gpu_benefit','claimed'),('layout',{'target':0}),('required_effective_options',{})):
            def mutate(m):
                metadata=json.loads(m[prep.adapter.METADATA]); metadata[field]=value
                m[prep.adapter.METADATA]=json.dumps(metadata).encode()
            with self.subTest(field=field):
                target=self.mutated_archive(mutate)
                with self.assertRaisesRegex(ValueError,'metadata/layout/options'):prep.qualify(paths={'enabled':target})

    def test_whole_archive_and_source_registration_mutations_rejected(self):
        target=self.mutated_archive(lambda m:None)
        with self.assertRaisesRegex(ValueError,'Complete reviewed archive bytes'):prep.qualify(paths={'enabled':target})
        ledger=json.loads((ROOT/prep.FREEZE).read_text()); ledger['enabled']['reference_registration']['sourceBindings'][0]['sourceSha256']='0'*64
        path=self.root/'freeze.json';path.write_text(json.dumps(ledger))
        with self.assertRaisesRegex(ValueError,'complete source freeze'):prep.qualify(freeze=path)

    def test_forest_world_drift_and_coastal_report_rejected(self):
        with mock.patch.object(prep,'world_fingerprint',return_value='changed'):
            with self.assertRaisesRegex(ValueError,'forest world drift'):prep.inspect_scene()
        with mock.patch.object(prep,'world_fingerprint',return_value=prep.WORLD_SHA256):
            with self.assertRaisesRegex(ValueError,'forest source report drift'):prep.inspect_scene(prep.copies.DEFAULT_SOURCES['coastalwater'])

    def test_complete_option_map_mutation_rejected_even_if_report_pin_mocked(self):
        source_report=prep.SOURCE/'workload-measurement.json'
        report=json.loads(source_report.read_text()); report['effective_shader_options']['NORMAL_MAPPING']='true'
        original_read=Path.read_text
        def read(path,*args,**kwargs):
            return json.dumps(report) if path==source_report else original_read(path,*args,**kwargs)
        with mock.patch.object(Path,'read_text',read):
            with self.assertRaisesRegex(ValueError,'Complete forest upstream option map'):prep.inspect_scene()

    def test_explicit_diagnostic_policy_and_no_other_effect_changes(self):
        for mode in ('FULL','ADAPTIVE'):
            effects=tomllib.loads(prep.effects_config(mode))['effects']
            self.assertEqual(effects['opaque_lighting'],dict(mode=mode,near=32.,far=96.,transition=8.,mediumWorkRatio=1.,farWorkRatio=.25,preserveSalientLighting=True))
            for name,values in effects.items():
                if name!='opaque_lighting':
                    self.assertEqual(values['mode'],'INHERIT');self.assertEqual(values['mediumWorkRatio'],1.);self.assertEqual(values['farWorkRatio'],1.)

    def test_report_rejects_stale_timed_and_option_drift(self):
        request=dict(run_id='diagnostic',diagnostic_only=True,performanceEvidence=False)
        expected=dict(request=request,archive_sha256=prep.ARCHIVES['enabled'],expected_effective_options={'k':'v'},expected_effective_options_sha256='pin')
        report=dict(status='complete',request=request,gpu_samples=[],cpu_samples=[],modPresent=True,dhPresent=False,
            actual_shader_sha256=prep.ARCHIVES['enabled'],effective_shader_options={'k':'v'},effective_shader_options_sha256='pin')
        for update,error in (({'request':request|{'run_id':'stale'}},'stale'),({'gpu_samples':[1]},'Timing stream'),
                             ({'effective_shader_options':{'k':'wrong'}},'complete option map')):
            with self.subTest(error=error):
                with self.assertRaisesRegex(ValueError,error):prep.validate_report(report|update,expected,{})


if __name__=='__main__':unittest.main()
