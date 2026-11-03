import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import benchmark_bliss_albedo_domain as domain


class AlbedoDomainContractTest(unittest.TestCase):
    def packet(self):
        row = dict(complete=True, tested_inputs=65536, outside_guard=0,
                   internal_format=32859, width=2560, height=1600, zeros=256,
                   minimum_nonzero_absolute=.0000596, maximum_absolute=1.0,
                   function_source_sha256='a' * 64)
        return {'actual': dict(error='', completed_frames=2,
                               events=[dict(kind='completed_quad', frame=i,
                                            name='composite2', bliss_albedo_domain=copy.deepcopy(row))
                                       for i in [1, 2]])}

    def test_closed_input_domain_and_consumer_frames(self):
        domain.validate(self.packet())
        for key, value in [('tested_inputs', 65535), ('internal_format', 34842),
                           ('minimum_nonzero_absolute', 1e-40), ('outside_guard', 1),
                           ('width', 1920), ('complete', False)]:
            p = self.packet()
            p['actual']['events'][0]['bliss_albedo_domain'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                domain.validate(p)
        p = self.packet()
        p['actual']['events'][1]['frame'] = 1
        with self.assertRaises(ValueError):
            domain.validate(p)
