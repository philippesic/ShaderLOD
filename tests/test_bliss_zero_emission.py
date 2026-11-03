import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_bliss_zero_emission as producer
import benchmark_bliss_zero_emission as contract

class BlissZeroEmissionTest(unittest.TestCase):
    def packet(self,active=False):
        frag='void Emission(inout vec3 Lighting,vec3 Albedo,float Emission,float exposure){'+(producer.GUARD.decode() if active else '')
        frag+='float autoBrightnessAdjust=5.0; Lighting=mix(Lighting,Albedo*1.0*autoBrightnessAdjust,pow(Emission,2.0));}\n'
        frag+='void main(){Emission(iris_FragData0.rgb,albedo,SpecularTex.a,exposure);}'
        return {'actual':{'sources':[{'name':'composite2','vertex_source':'void main(){}','fragment_source':frag}],
                          'events':[{'kind':'completed_quad','frame':f,'name':'composite2'} for f in [1,2]]}}

    def test_attached_guard_fallback_and_other_operations(self):
        contract.assess({'native':self.packet(),'candidate':self.packet(True),'return':self.packet()})
        with self.assertRaisesRegex(ValueError,'guard differs'):contract.source_work(self.packet(),active=True)
        bad=self.packet(True);bad['actual']['sources'][0]['fragment_source']=bad['actual']['sources'][0]['fragment_source'].replace('pow(Emission,2.0)','pow(Emission,0.0)')
        with self.assertRaisesRegex(ValueError,'fallback/constants'):contract.source_work(bad,active=True)
        bad=self.packet(True);bad['actual']['sources'][0]['vertex_source']='void main(){changed();}'
        with self.assertRaisesRegex(ValueError,'Other attached'):contract.assess({'native':self.packet(),'candidate':bad})
        bad=self.packet(True);bad['actual']['events'].pop()
        with self.assertRaisesRegex(ValueError,'did not return'):contract.source_work(bad,active=True)

    def test_exact_single_member_derivation_and_native_domain(self):
        pack=producer.ROOT/'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';profile=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(profile.items())))
            out=root/'candidate.zip';producer.build(pack,options,out)
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual([producer.MEMBER],[n for n in original.namelist() if original.read(n)!=candidate.read(n)])
                guard=producer.GUARD.replace(b'\n',b'\r\n')
                self.assertEqual(original.read(producer.MEMBER),candidate.read(producer.MEMBER).replace(guard,b'',1))
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
            profile['Emissive_Curve']='0.0';options.write_text(''.join(f'{k}={v}\n' for k,v in profile.items()))
            with self.assertRaises(ValueError):producer.build(pack,options,root/'wrong.zip')
