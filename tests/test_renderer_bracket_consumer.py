"""Tiny boundary checks; authentic r2/r3 inputs are never changed or rerun."""
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
spec=importlib.util.spec_from_file_location('consumer',ROOT/'scripts/run_renderer_bracket.py')
c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)
RECORDED=ROOT/'dev/logs/speed-port-r1/262-fabric-makeup-r3'


class ConsumerTests(unittest.TestCase):
    def report(self,revision='r3'):
        return json.loads((ROOT/('dev/logs/speed-port-r1/262-fabric-makeup-'+revision)/'runtime-report.json').read_text())

    def test_authentic_r3_qualifies_automatically_and_preserves_negative_signal(self):
        result=c.qualified_report(self.report(),RECORDED/'runtime-report.json')
        self.assertFalse(result['measurementValid'])
        self.assertFalse(result['causalSpeedClaimAccepted'])
        self.assertAlmostEqual(result['comparisons']['returnFrameTimeDriftPercent'],108.8034,places=3)
        self.assertLess(result['comparisons']['throughputVsInitialPercent'],0)
        self.assertGreater(result['comparisons']['throughputVsReturnPercent'],0)
        self.assertEqual(result['workObservation']['removedCommands'],3470)
        self.assertIn('minimumConfiguredFramerateLimit',' '.join(result['errors']))
        self.assertIn('rateUnavailableFrames',' '.join(result['errors']))
        self.assertFalse(result['qualityAccepted'])

    def test_authentic_failed_r2_is_retained_not_promoted(self):
        report=self.report('r2');result=c.qualified_report(report,'recorded r2')
        self.assertFalse(result['measurementValid'])
        self.assertEqual(result['arms']['original']['renderFrames'],report['original']['renderFrames'])
        self.assertIn('Incomplete actual bracket',result['errors'])

    def test_new_fields_required_and_unavailable_count_not_ignored(self):
        # Test-only boundary facts on a copied authentic report, never runtime evidence.
        report=copy.deepcopy(self.report())
        for arm in ('original','on','off'):
            report[arm].update(expectedConfiguredFramerateLimit=260,rateUnavailableFrames=0,
                minimumConfiguredFramerateLimit=260,maximumConfiguredFramerateLimit=260,
                minimumEffectiveFramerateLimit=260,maximumEffectiveFramerateLimit=260,
                rateObservedFrames=report[arm]['renderFrames'])
        self.assertTrue(c.qualified_report(report,'test-only virtual bounds')['measurementValid'])
        report['on']['rateUnavailableFrames']=1
        self.assertFalse(c.qualified_report(report,'test-only virtual bounds')['measurementValid'])
        report['on']['rateUnavailableFrames']=False
        self.assertFalse(c.qualified_report(report,'test-only virtual bounds')['measurementValid'])

    def test_unknown_tuple_refuses_without_version_extrapolation(self):
        report=self.report();report['minecraftTarget']='26.3'
        result=c.qualified_report(report,'test-only wrong tuple')
        self.assertFalse(result['measurementValid'])
        self.assertIn('No admitted finite consumer tuple',' '.join(result['errors']))

    def test_closed_historical_preflight_batches_cheap_defects_before_hashing(self):
        plan=json.loads((RECORDED/'plan.json').read_text());c.PROFILE=Path(plan['profile'])
        with patch.object(c,'sha',side_effect=AssertionError('Expensive hashing reached')):
            result=c.preflight(RECORDED,c.PROFILE)
        self.assertFalse(result.get('readyForExplicitRun',False))
        self.assertGreaterEqual(len(result['errors']),5)
        self.assertTrue(any('One-shot output already exists' in e for e in result['errors']))
        self.assertTrue(any('runtime receipts' in e for e in result['errors']))
        self.assertFalse(result['launchPerformed'])

    def test_environment_and_launcher_errors_join_cheap_defects(self):
        plan=json.loads((RECORDED/'plan.json').read_text());profile=Path(plan['profile'])
        plan['explicit_env']={'JAVA_HOME':'wrong'};plan['command']=['wrong','runClient','--args=wrong']
        errors=c.cheap_boundary_errors(plan,RECORDED,profile)
        self.assertTrue(any('explicit environment' in e for e in errors))
        self.assertTrue(any('pinned launcher' in e for e in errors))
        self.assertTrue(any('launcher project' in e for e in errors))
        self.assertTrue(any('gameDir' in e for e in errors))

    def test_output_is_separate_and_cannot_overwrite_evidence(self):
        with tempfile.TemporaryDirectory() as tmp:
            output=Path(tmp)/'result.json';c.write_new(output,{'measurementValid':False})
            with self.assertRaises(FileExistsError):c.write_new(output,{'measurementValid':True})
            self.assertFalse(json.loads(output.read_text())['measurementValid'])


if __name__=='__main__':unittest.main()
