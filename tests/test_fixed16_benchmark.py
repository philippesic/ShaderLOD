import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import benchmark_fixed16 as candidate


class Fixed16BenchmarkTest(unittest.TestCase):
    def test_staged_trace_matches_retained_accepted_source_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary);profile=directory/'client';(profile/'shaderpacks').mkdir(parents=True)
            options=directory/'native.txt'
            values=json.loads((ROOT/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
            options.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
            pack=ROOT/'dependencies/shortlist-test-inputs/originals/MakeUpNativeOriginal.zip'
            identity=candidate.identity(pack,options)
            op=Mock();op.output=directory/'report';op.output.mkdir()
            op.data={'profile':str(profile),'configuration':{str(profile/'shaderpacks'/(identity[key]+suffix)):{'expected':None}
                       for key in ('generated','work') for suffix in ('','.txt')}}
            result=candidate.stage(op,pack,options,identity)
            self.assertEqual(result,{'lean_sha256':candidate.LEAN,'work_sha256':candidate.WORK})
            op.acknowledge_mutation.assert_called_once_with('stage-g05-fixed16')

    def test_native_control_cannot_keep_resources_or_produce_work(self):
        native={'fixed16':True,'cell_size':16,'cost_opt_in':True,'producer_enabled':False,
                'binding_active':False,'idle_last_program_permission':0,'image':0,'program':0,
                'builds':5,'positive_permissions':10}
        candidate.progress(native,active=False,before=native)
        for key,value in [('image',3),('producer_enabled',True),('builds',6),('positive_permissions',11)]:
            with self.assertRaises(ValueError):candidate.progress(native|{key:value},active=False,before=native)


if __name__=='__main__':unittest.main()
