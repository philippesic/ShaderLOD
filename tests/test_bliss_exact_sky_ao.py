import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_bliss_exact_sky_ao as producer
import benchmark_bliss_exact_ao as controller

class BlissExactSkyAoTest(unittest.TestCase):
    def packet(self,active=False):
        fragment=(producer.REPLACEMENT if active else producer.ANCHOR).decode()+'\nif(swappedDepth >= 1.0) SSAO_SSS = vec2(1.0,0.0);'
        return {'actual':{'sources':[{'name':'composite1','fragment_source':fragment,'vertex_source':'void main(){}',
                                     'parsed_mips':[],'parsed_flips':{},'parsed_draw_buffers':[0]}],
                          'events':[{'kind':'completed_quad','frame':i,'name':'composite1'} for i in [1,2]]}}

    def test_exact_attached_source_delta_and_completed_consumer(self):
        controller.assess({'native':self.packet(),'candidate':self.packet(True),'return':self.packet()})
        bad=self.packet(True);bad['actual']['sources'][0]['vertex_source']='void main(){discard;}'
        with self.assertRaisesRegex(ValueError,'another attached'):
            controller.assess({'native':self.packet(),'candidate':bad})
        bad=self.packet(True);bad['actual']['events']=[]
        with self.assertRaisesRegex(ValueError,'did not execute'):controller.source_work(bad,active=True)
        with self.assertRaisesRegex(ValueError,'branch missing'):controller.source_work(self.packet(),active=True)

    def test_emitter_float_suffix_preserves_literal_type_and_identifiers(self):
        self.assertEqual(controller.canonical('vec2(1.0,0.0)'),controller.canonical('vec2(1.0f, 0.0f)'))
        self.assertNotEqual(controller.canonical('x1.0f'),controller.canonical('x1.0'))
        packet=self.packet();packet['actual']['sources'][0]['fragment_source']=packet['actual']['sources'][0]['fragment_source'].replace('1.0','1.0f').replace('0.0','0.0f')
        controller.source_work(packet,active=False)

    def test_depth_census_requires_complete_finite_bounded_frame_evidence(self):
        packet=self.packet()
        packet['actual']['settled_rearm']=True
        for event in packet['actual']['events']:
            event['bliss_ao_depth_census']={'width':2560,'height':1600,'sky_input_texels':100,
                                          'non_sky_input_texels':2560*1600-100,'nonfinite_input_texels':0,
                                          'texture':8,'owned_depth_texture':8}
            event['bliss_ao_depth_census_before']=dict(event['bliss_ao_depth_census'])
        self.assertEqual(2,len(controller.depth_census(packet)['frames']))
        packet['actual']['settled_rearm']=False
        with self.assertRaisesRegex(ValueError,'Settled'):controller.depth_census(packet)
        packet['actual']['settled_rearm']=True
        packet['actual']['events'][0]['bliss_ao_depth_census']['owned_depth_texture']=9
        with self.assertRaisesRegex(ValueError,'owner'):controller.depth_census(packet)
        packet['actual']['events'][0]['bliss_ao_depth_census']['owned_depth_texture']=8
        packet['actual']['events'][0]['bliss_ao_depth_census_before']['sky_input_texels']=0
        with self.assertRaisesRegex(ValueError,'changed'):controller.depth_census(packet)
        packet['actual']['events'][0]['bliss_ao_depth_census_before']['sky_input_texels']=100
        packet['actual']['events'][0]['bliss_ao_depth_census']['sky_input_texels']=True
        with self.assertRaisesRegex(ValueError,'Typed'):controller.depth_census(packet)
        packet['actual']['events'][0]['bliss_ao_depth_census']['sky_input_texels']=101
        with self.assertRaisesRegex(ValueError,'Complete'):controller.depth_census(packet)
        packet['actual']['events'][0]['bliss_ao_depth_census']['sky_input_texels']=100
        packet['actual']['events'][0]['bliss_ao_depth_census']['nonfinite_input_texels']=1
        with self.assertRaisesRegex(ValueError,'Complete'):controller.depth_census(packet)

    def test_activation_refuses_changed_dimension_dh_or_native_options(self):
        native=json.loads(producer.PROFILE.read_text());identity={'native_options':native}
        state={'pack':'test.zip','effective_options':native,'installed':{'master':False,'flags':{}},
               'dimension':'minecraft:overworld','dh_installed':False,'loaded_stack':{},'fallback':False}
        controller.activation(state,identity,expected_pack='test.zip')
        for changes in [{'dimension':'minecraft:the_nether'},{'dh_installed':True},{'loaded_stack':{'voxy':'1'}},
                        {'effective_options':native|{'TAA':'false'}}]:
            with self.assertRaises(ValueError):controller.activation(state|changes,identity,expected_pack='test.zip')

    def test_pinned_native_derivation_changes_only_discarded_call(self):
        pack=producer.ROOT/'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';native=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(native.items())))
            output=root/'candidate.zip';receipt=producer.build(pack,options,output)
            self.assertEqual(native,receipt['native_options'])
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(output) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual(['shaders/dimensions/composite.fsh'],[n for n in original.namelist() if original.read(n)!=candidate.read(n)])
                native_fragment=original.read(receipt['changed_members'][0]);new=candidate.read(receipt['changed_members'][0])
                self.assertEqual(native_fragment,new.replace(producer.REPLACEMENT,producer.ANCHOR))
            self.assertEqual(options.read_bytes(),Path(str(output)+'.txt').read_bytes())
            options.write_text(options.read_text().replace('TAA=true','TAA=false'))
            with self.assertRaisesRegex(ValueError,'native463'):producer.validate(pack,options)
