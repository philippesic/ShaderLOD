import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_sildur_lean_alpha as contract


class SildurLeanAlphaContractTest(unittest.TestCase):
    def packet(self, active=False):
        fragment = 'uniform float iris_currentAlphaTest;\n'
        if active:
            fragment += contract.producer.HELPER
        fragment += 'void main(){\n'
        if active:
            fragment += contract.producer.GUARD
        fragment += (contract.producer.REWRITTEN_QUERY if active else contract.producer.QUERY) + ';\n'
        fragment += 'if (!(iris_FragData0.a > iris_currentAlphaTest)) {discard;}\n}'
        return {'actual': {'settled_rearm': True, 'sources': [
            {'name': 'sodium_cutout', 'program': 42, 'fragment_source': fragment, 'vertex_source': 'native vertex'},
            {'name': 'composite', 'program': 43, 'fragment_source': 'native post', 'vertex_source': 'native quad'}],
            'events': [{'kind': 'terrain_setup', 'frame': f, 'name': 'sodium_cutout'} for f in [1, 2]]}}

    def test_approval_requires_exact_other_operations_and_native_greater(self):
        observations = {'native': self.packet(), 'candidate': self.packet(True), 'return': self.packet()}
        contract.assess(observations)
        bad = copy.deepcopy(observations)
        bad['candidate']['actual']['sources'][1]['fragment_source'] += 'changed operation'
        with self.assertRaisesRegex(ValueError, 'another attached operation'):
            contract.assess(bad)
        bad = self.packet(True)
        bad['actual']['sources'][0]['fragment_source'] = bad['actual']['sources'][0]['fragment_source'].replace('iris_FragData0.a >', 'iris_FragData0.a >=')
        with self.assertRaisesRegex(ValueError, 'GREATER'):
            contract.source_work(bad, active=True)
        bad = self.packet(True)
        bad['actual']['settled_rearm'] = False
        with self.assertRaisesRegex(ValueError, 'Settled'):
            contract.source_work(bad, active=True)

    def test_no_runtime_arm_when_source_comparison_fails(self):
        control = Mock()
        observations = {'native': self.packet(), 'candidate': self.packet(True)}
        observations['candidate']['actual']['sources'][0]['vertex_source'] += 'different'
        with self.assertRaisesRegex(ValueError, 'another attached operation'):
            contract.arm(control, observations, {'generated': 'candidate.zip'}, 'a' * 64)
        control.send.assert_not_called()

    def test_translucent_water_must_remain_native(self):
        observations={'native':self.packet(),'candidate':self.packet(True)}
        water={'name':'sodium_translucent','program':44,'fragment_source':'native water','vertex_source':'native water vertex'}
        for packet in observations.values():packet['actual']['sources'].append(dict(water))
        contract.assess(observations)
        observations['candidate']['actual']['sources'][-1]['fragment_source']+=contract.producer.HELPER
        with self.assertRaisesRegex(ValueError,'water shader'):contract.assess(observations)

    def test_typed_permission_progress_and_inactive_control(self):
        row = {'armed': True, 'setups': 10, 'grants': 5, 'refusals': 5,'draw_calls':10}
        contract.progress(row, active=True)
        with self.assertRaisesRegex(ValueError, 'progress'):
            contract.progress(row, active=True, before=row)
        with self.assertRaisesRegex(ValueError, 'Typed'):
            contract.progress(row | {'grants': True}, active=True)
        with self.assertRaisesRegex(ValueError, 'ownership'):
            contract.progress(row, active=False)
        contract.progress(row | {'armed': False}, active=False)


if __name__ == '__main__':
    unittest.main()
