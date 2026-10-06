"""Preparation and fail-closed raw-trace fixtures; never launch a client."""
import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_work_trace_runtime as prep

class WorkTraceRuntimeTests(unittest.TestCase):
    def test_fresh_ignored_two_arm_plan(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);target=root/'run-workloads/new'
            p=prep.plan(target,root);self.assertEqual(p['arms'],['original','adaptive']);self.assertFalse(target.exists())
            self.assertTrue(p['diagnostic_only']);self.assertFalse(p['performanceEvidence'])
            with self.assertRaises(ValueError):prep.plan(root/'tracked',root)
            target.mkdir(parents=True)
            with self.assertRaises(ValueError):prep.plan(target,root)

    def test_actual_ledger_and_registration_exact(self):
        rows=prep.qualify();self.assertEqual(set(rows),{'original','adaptive'})
        self.assertEqual(rows['original']['source'],'original');self.assertTrue(rows['original']['trace_default'])
        self.assertEqual(rows['adaptive']['metadata']['capture']['colortexes'],[9,10,11])
        self.assertNotEqual(rows['original']['sha256'],rows['adaptive']['sha256'])
        for arm,row in rows.items():
            self.assertEqual((row['sha256'],row['registration_sha256']),prep.REVIEWED_ENABLED[arm])
            self.assertEqual(row['metadata']['traceContract']['layout']['cloud_extent_targets'],[9,10,11,12,15])
        with mock.patch.object(prep.shared,'file_hash',return_value='drift'):
            with self.assertRaises(ValueError):prep.qualify()

    def test_commands_both_have_mod_and_no_timers(self):
        cmd=prep.command(Path('/absolute/profile'),Path('/absolute/registration.json'),Path('/absolute/raw.json'))
        self.assertEqual(cmd[1],'runClientLodOn')
        for p in ('gpuTiming','cpuTiming','cpuLayerTiming'):self.assertIn('-P'+p+'=false',cmd)
        for p in ('workTrace','workTraceOnly','measureWorkload'):self.assertIn('-P'+p+'=true',cmd)
        self.assertIn('-PworkTraceMetadata=/absolute/registration.json',cmd)

    def fixture(self):
        d=dict(status='VALID_SCHEMA_1',primaryIterations=10,densityEvaluations=18,secondaryVisits=8,textureEvaluations=50,
            originalWork=20,continuousRequestedWork=17.,continuousSelectedWork=17.,selectedCeilTarget=17,pathTag=2,reasonTag=0)
        c=dict(status='RAW_SAME_DRAW_CAPTURE',performanceEvidence=False,schema=1,attachments=[0,1,2],schemaUniform={'value':1,'type':5124,'location':3},viewport=[0,0,100,100],program=1,drawFramebuffer=1,
            attachmentImages=[dict(internalFormat=34836,width=100,height=100,object=1,level=0)]*3,drawOrdinal=1,renderFrameOrdinal=1,
            samples=[dict(x=1,y=1,pages=[[10,18,8,50],[20,17,17,2],[1,0,1,0]],decoded=d)])
        return dict(performanceEvidence=False,captureWindowFinished=True,terminalStatus='CAPTURED',captures=[c])

    def test_valid_raw_counts_distinct_from_budget(self):
        s=prep.validate_trace(self.fixture());self.assertEqual(s['counts']['primaryIterations'],10)
        self.assertEqual(s['counts']['densityEvaluations'],18);self.assertFalse(s['performanceEvidence'])

    def test_unknown_sentinel_format_state_and_schema_rejected(self):
        fixture=self.fixture()
        mutations=[lambda c:c.update(status='UNKNOWN',reason='FORMAT'),lambda c:c.update(restoreErrors=['failed']),
            lambda c:c['attachmentImages'][0].update(internalFormat=34842),lambda c:c['samples'][0]['decoded'].update(status='UNKNOWN',reason='INVALID_SENTINEL'),
            lambda c:c['samples'][0]['pages'][2].__setitem__(0,2)]
        for mutate in mutations:
            changed=copy.deepcopy(fixture);mutate(changed['captures'][0])
            with self.assertRaises(ValueError):prep.validate_trace(changed)

if __name__=='__main__':unittest.main()
