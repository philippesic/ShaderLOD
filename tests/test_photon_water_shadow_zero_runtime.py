"""Bounded preparation and actual sampler replay; no launch/Gradle/GPU."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_photon_water_shadow_zero_runtime as prep


class WaterShadowZeroRuntimeTests(unittest.TestCase):
    def actual_probe(self):
        path=ROOT/'run-workloads/shadow-atlas-probe-r1/shadow2048/control/logs/cloud-sampler-state.json'
        if not path.exists():self.skipTest('Local actual 2048 probe unavailable')
        return json.loads(path.read_text())

    def test_owned_plan_serial_controls_cost_blocked_no_mutation(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);out=root/'run-workloads/water-shadow-zero-runtime-r1';build=root/'build/water-shadow-zero-runtime-r1'
            plan=prep.plan(out,build,root)
            self.assertEqual(plan['arms'],['original','global-off','global-on']);self.assertFalse(out.exists())
            self.assertFalse(plan['launch_authorized']);self.assertFalse(plan['performanceEvidence'])
            self.assertEqual(plan['global_enabled'],{'original':False,'global-off':False,'global-on':True})
            out.mkdir(parents=True)
            with self.assertRaisesRegex(ValueError,'Preserve'):prep.plan(out,build,root)
        recipe=prep.cost_source_plan();self.assertIn('BLOCKED',recipe['status']);self.assertFalse(recipe['profile_created'])
        self.assertEqual(recipe['phases'],[False,True,False,True]);self.assertEqual(recipe['requested_recipe']['initial_warmup_seconds'],30)
        self.assertFalse(recipe['requested_recipe']['cloudSamplerProbe'])

    def test_exact_queryfree_flags_true_sampler_only_and_hiddenGUI(self):
        argv=prep.command(ROOT/'run-workloads/water-shadow-zero-runtime-r1/original/control')
        self.assertEqual(argv[:2],['./gradlew','runClientLodOn'])
        for flag in ('cloudSamplerProbe=true','hideWorkloadGui=true','initialWarmup=60','measurementSeconds=8','measureWorkload=true','measureOnly=true','includeDh=false','performanceStack=minimal'):
            self.assertIn('-P'+flag,argv)
        self.assertEqual(argv.count('-PcloudSamplerProbe=true'),1);self.assertNotIn('-PcloudSamplerProbe=false',argv)
        for key in prep.OFF:self.assertIn('-P'+key+'=false',argv)
        for task in prep.canonical.BUILD_TASKS:self.assertEqual(argv[argv.index(task)-1],'-x')


    def test_actual_original_sampler_contract_unknown_core_remains_unknown(self):
        raw=self.actual_probe();before=copy.deepcopy(raw);result=prep.qualify_probe(raw,'original')
        self.assertEqual(result['status'],'OBSERVED_RASTER_SHADOWTEX1_LOD0_SAMPLER_CONTRACT')
        self.assertTrue(result['unknown_core_readbacks']);self.assertEqual(raw,before)
        self.assertTrue(all(x['stage_identity']=='unknown' for x in result['observations']))
        self.assertFalse(result['performanceEvidence'])
        with self.assertRaisesRegex(ValueError,'known core'):prep.qualify_probe(raw,'global-off')

    def test_actual_state_missing_nonmip_comparison_alias_restoration_reject(self):
        for mutation in ('filter','bias','aniso','base','compare','allocation','sampler','restore'):
            raw=copy.deepcopy(self.actual_probe())
            program=next(p for p in raw['programs'] if p['draw_origin']=='sodium-raster-submission' and 'shadowtex1' in p.get('samplers',{}))
            sampler=program['samplers']['shadowtex1'];effective=sampler['effective_sampling_parameters']
            if mutation=='filter':effective['min_filter']=9987;sampler['texture_parameters']['min_filter']=9987
            elif mutation=='bias':effective['lod_bias']=1;sampler['texture_parameters']['lod_bias']=1
            elif mutation=='aniso':effective['max_anisotropy']=2;sampler['texture_parameters']['max_anisotropy']=2
            elif mutation=='base':effective['base_level']=1;sampler['texture_parameters']['base_level']=1
            elif mutation=='compare':effective['compare_func']=513;sampler['texture_parameters']['compare_func']=513
            elif mutation=='allocation':sampler['texture_level_zero']['width']=1536
            elif mutation=='sampler':sampler['bound_sampler']=1
            else:program['active_texture_after']+=1
            with self.assertRaises(ValueError,msg=mutation):prep.qualify_probe(raw,'original')

    def test_linked_core_typed_model_requires_same_program_inventory_and_value(self):
        # A typed guard model extends a COPY of real sampler fields solely to
        # test linkage predicates; it is never labeled actual candidate evidence.
        model=copy.deepcopy(self.actual_probe())
        program=next(p for p in model['programs'] if p['draw_origin']=='sodium-raster-submission' and 'shadowtex1' in p.get('samplers',{}))
        for name,value in (('shaderlodBridgeVersion',2),('shaderlodEnabled',1)):
            program['core_uniforms'][name]=dict(expected_type=5124,found_in_linked_inventory=True,status='queried_without_gl_error_consumption',
                active_uniform_type=5124,active_uniform_size=1,active_uniform_name=name,uniform_location=20,value=value)
            program['linked_uniforms'][name]=dict(type=5124,size=1)
        result=prep.qualify_probe(model,'global-on');self.assertTrue(any(len(x['known_core_global'])==2 for x in result['observations']))
        for field,value in (('value',0),('uniform_location',-1),('active_uniform_type',5126),('active_uniform_size',2)):
            changed=copy.deepcopy(model);target=next(p for p in changed['programs'] if p['current_program']==program['current_program'])
            target['core_uniforms']['shaderlodEnabled'][field]=value
            with self.assertRaises(ValueError):prep.qualify_probe(changed,'global-on')


if __name__=='__main__':unittest.main()
