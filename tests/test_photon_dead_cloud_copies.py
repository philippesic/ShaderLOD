import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_photon_dead_cloud_copies as producer
import benchmark_photon_dead_copies as contract

class PhotonDeadCloudCopiesTest(unittest.TestCase):
    def packet(self,active=False,offset=0):
        def texture(target,bank):
            return {'id':offset+2*target+(1 if bank=='main' else 2),'exists':True,
                    'width':640 if target in (9,10) else 2560,'height':400 if target in (9,10) else 1600}
        images=[{'target':t,'main':texture(t,'main'),'alt':texture(t,'alt')} for t in range(16)]
        names=['deferred1','deferred2','deferred4','composite16','final'];events=[]
        raster={'viewport':[0,0,2560,1600],'color_mask0':[True]*4,'scissor':False,
                'depth_test':False,'rasterizer_discard':False,'blend0':False}
        sources=[{'name':n,'vertex_source':'native vertex '+n,'fragment_source':'native fragment '+n,
                  'parsed_mips':[5,11] if n=='deferred4' else [],'parsed_draw_buffers':[],
                  'parsed_flips':{str(t):True for t in producer.TARGETS} if active and n=='composite16' else {}} for n in names]
        for frame in [1,2]:
            events.append({'kind':'frame_start','frame':frame,'images':images})
            events.append({'kind':'terrain_setup','frame':frame,'name':'sodium_solid',
                           'reads':[{'target':4,'texture':texture(4,'main')}],'writes':[],'raster_state':raster})
            for name in names:
                if name=='deferred4':
                    for target in [5,11]:events.append({'kind':'mip_return','frame':frame,'texture':texture(target,'alt'),'read_alt':True})
                writes=([{'location':0,'texture':texture(9,'alt')['id']}] if name=='deferred1' else
                        [{'location':0,'texture':offset+900}] if name=='final' else [])
                events.append({'kind':'completed_quad','frame':frame,'name':name,'reads':[],
                               'writes':writes,'raster_state':raster,'draw_fbo':123,
                               'presentation_target':{'texture':offset+900,'framebuffer':7,'width':2560,'height':1600}})
            for target in ([4,5,6,7,14] if active else [4,5,6,7,9,10,11,12,14]):
                events.append({'kind':'swap_copy_return','frame':frame,'source_texture':texture(target,'alt')['id'],
                               'destination_texture':texture(target,'main')['id']})
            events.append({'kind':'final_return','frame':frame,'completed_frames':frame})
        events.append({'kind':'frame_start','frame':3,'images':images})
        return {'actual':{'sources':sources,'events':events,'completed_frames':2,'stopped':True,'error':''}}

    def test_physical_ids_can_change_but_pass_bindings_and_all_other_work_must_match(self):
        native=self.packet();candidate=self.packet(True,100)
        result=contract.assess({'native':native,'candidate':candidate,'return':self.packet(False,200)})
        self.assertEqual([4,5,6,7,14],result['candidate']['frames'][0]['retained_copy_targets'])
        bad=self.packet(True,100);next(e for e in bad['actual']['events'] if e['kind']=='terrain_setup')['reads'][0]['texture']['id']+=1
        with self.assertRaisesRegex(ValueError,'Earlier physical'):contract.assess({'native':native,'candidate':bad})
        bad=self.packet(True);bad['actual']['sources'][0]['fragment_source']='changed'
        with self.assertRaisesRegex(ValueError,'Native source'):contract.assess({'native':native,'candidate':bad})
        bad=self.packet(True);indices=[i for i,e in enumerate(bad['actual']['events']) if e['kind']=='swap_copy_return' and e['frame']==1]
        first,second=indices[:2];bad['actual']['events'][first],bad['actual']['events'][second]=bad['actual']['events'][second],bad['actual']['events'][first]
        with self.assertRaisesRegex(ValueError,'copy order changed'):contract.assess({'native':native,'candidate':bad})

    def test_refuses_unknown_copy_source_missing_boundaries_and_live_cloud_read(self):
        bad=self.packet(True);next(e for e in bad['actual']['events'] if e['kind']=='swap_copy_return')['source_texture']=999
        with self.assertRaisesRegex(ValueError,'Unknown physical'):contract.source_work(bad,active=True)
        bad=self.packet(True);bad['actual']['events'].pop()
        with self.assertRaisesRegex(ValueError,'Next-frame reset'):contract.source_work(bad,active=True)
        bad=self.packet(True);next(e for e in bad['actual']['events'] if e['kind']=='completed_quad')['reads']=[{'target':9}]
        with self.assertRaisesRegex(ValueError,'live cloud sampler'):contract.source_work(bad,active=True)
        bad=self.packet(True);bad['actual']['error']='incomplete'
        with self.assertRaisesRegex(ValueError,'error-free'):contract.source_work(bad,active=True)
        bad=self.packet(True);next(e for e in bad['actual']['events'] if e.get('name')=='final')['presentation_target']['texture']=777
        with self.assertRaisesRegex(ValueError,'Unowned external'):contract.source_work(bad,active=True)

    def test_only_terminal_properties_change_and_material_tuple_refused(self):
        pack=producer.ROOT/'dependencies/upstream/photon_v1.3b.zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';profile=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(profile.items())))
            out=root/'candidate.zip';producer.build(pack,options,out)
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual([producer.PROPERTIES],[n for n in original.namelist() if original.read(n)!=candidate.read(n)])
                self.assertEqual(original.read(producer.PROPERTIES)+producer.ADDITION,candidate.read(producer.PROPERTIES))
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
            profile['NORMAL_MAPPING']='true';options.write_text(''.join(f'{k}={v}\n' for k,v in profile.items()))
            with self.assertRaises(ValueError):producer.build(pack,options,root/'pbr.zip')
