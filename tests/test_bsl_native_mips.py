import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import build_bsl_native_mips as producer
import benchmark_native_mips as benchmark


class NativeMipDiscriminatorTest(unittest.TestCase):
    def test_observer_must_complete_both_frames_with_actual_expected_generations(self):
        for active in (False,True):
            row={'exactApi':True,'complete':True,'frames':[{'candidate':active,'c6Candidate':active,
                 'generatedTextures':[] if active else [1,2]} for _ in range(2)]}
            benchmark.witness(row,active=active)
            row['frames'][1]['generatedTextures']=[1]
            with self.assertRaises(ValueError):benchmark.witness(row,active=active)
    def test_only_two_declarations_change_and_native_options_are_exact(self):
        pack=ROOT/'dependencies/shortlist-test-inputs/originals/BSL_v10.1.8.zip'
        values=json.loads((ROOT/'src/main/resources/assets/shaderlod/bslquad/native384.json').read_text())
        with tempfile.TemporaryDirectory() as directory:
            options=Path(directory)/'native.txt'
            options.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
            output=Path(directory)/'candidate.zip'
            result=producer.build(pack,options,output)
            with zipfile.ZipFile(pack) as native,zipfile.ZipFile(output) as candidate:
                self.assertEqual(native.namelist(),candidate.namelist())
                changed={n for n in native.namelist() if native.read(n)!=candidate.read(n)}
                self.assertEqual(changed,set(producer.MEMBERS))
                for name in changed:
                    self.assertEqual(candidate.read(name).replace(producer.ANCHOR.replace(b'true',b'false'),producer.ANCHOR),native.read(name))
            self.assertEqual(Path(str(output)+'.txt').read_bytes(),options.read_bytes())
            self.assertEqual(result['native_options'],values)
            values['ADVANCED_MATERIALS']='true'
            options.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
            pbr=producer.build(pack,options,Path(directory)/'pbr.zip')
            self.assertEqual(pbr['candidate_sha256'],result['candidate_sha256'])
            self.assertEqual(pbr['native_options'],values)
            values['PARALLAX_QUALITY']='64'
            options.write_text(''.join(k+'='+v+'\n' for k,v in values.items()))
            with self.assertRaisesRegex(ValueError,'native384'):
                producer.build(pack,options,Path(directory)/'wrong.zip')
            self.assertFalse((Path(directory)/'wrong.zip').exists())


if __name__=='__main__':unittest.main()
