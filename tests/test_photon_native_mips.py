import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_photon_native_mips as producer
import benchmark_photon_native_mips as controller
import run_benchmark as runner

class PhotonNativeMipTest(unittest.TestCase):
    def test_real_original_derivation_preserves_every_other_member_and_native_map(self):
        pack=producer.ROOT/'dependencies/upstream/photon_v1.3b.zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';native=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(native.items())))
            before=producer.digest(pack.read_bytes());out=root/'candidate.zip';receipt=producer.build(pack,options,out)
            self.assertEqual(before,producer.digest(pack.read_bytes()));self.assertEqual(native,receipt['native_options'])
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                changed=[n for n in original.namelist() if original.read(n)!=candidate.read(n)]
                self.assertEqual(set(producer.MEMBERS),set(changed))
                frag=candidate.read('shaders/program/d4_deferred_shading.fsh')
                self.assertEqual(1,frag.count(b'const bool colortex5MipmapEnabled = true;'))
                self.assertIn(b'#if defined WORLD_NETHER',frag)
                props=candidate.read('shaders/shaders.properties')
                for i in [1,2]:self.assertIn(f'program.world-1/deferred{i}.enabled = false'.encode(),props)
                self.assertFalse(b'program.world0/deferred1.enabled = false' in props)
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
            self.assertEqual('photon',controller.identity(pack,options)['family'])
            changed=dict(native);changed['TAAU']='true';options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(changed.items())))
            with self.assertRaisesRegex(ValueError,'native366'):producer.validate(pack,options)

    def test_private_activation_refuses_wrong_world_lod_and_native_quality(self):
        native=json.loads(producer.PROFILE.read_text());identity={'native_options':native}
        state={'pack':'test.zip','effective_options':native,'installed':{'master':False,'flags':{}},'dimension':'minecraft:the_nether','dh_installed':False,'loaded_stack':{},'fallback':False}
        controller.activation(state,identity,expected_pack='test.zip')
        for change in [{'dimension':'minecraft:overworld'},{'dh_installed':True},{'loaded_stack':{'voxy':'1'}},{'effective_options':native|{'TAAU':'true'}}]:
            with self.assertRaises(ValueError):controller.activation(state|change,identity,expected_pack='test.zip')

if __name__=='__main__':unittest.main()
