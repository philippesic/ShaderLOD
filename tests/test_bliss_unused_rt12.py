import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import build_bliss_unused_rt12 as producer
import benchmark_bliss_unused_rt12 as contract

class BlissUnusedRt12Test(unittest.TestCase):
    def packet(self,active=False,offset=0):
        def texture(t,bank):return {'id':offset+2*t+(1 if bank=='main' else 2),'exists':True,'format':34842,'width':2560,'height':1600}
        images=[{'target':t,'main':texture(t,'main'),'alt':texture(t,'alt')} for t in range(16) if not(active and t==12)]
        fragment='void main(){float depth=z;'+('' if active else contract.STORE)+'other_native_operations();}'
        if not active:fragment=contract.DECLARATION+fragment
        sources=[{'name':'deferred1','vertex_source':'native vertex','fragment_source':'native deferred',
                  'parsed_draw_buffers':[4] if active else [4,12],'parsed_mips':[],'parsed_flips':{}},
                 {'name':'composite1','vertex_source':'native vertex','fragment_source':fragment,
                  'parsed_draw_buffers':[3,14] if active else [3,14,12],'parsed_mips':[],'parsed_flips':{}}]
        events=[]
        for frame in [1,2]:
            events.append({'kind':'frame_start','frame':frame,'images':images})
            for name,outputs in [('deferred1',[4]),('composite1',[3,14]),('final',[])]:
                writes=[{'location':i,'texture':texture(t,'alt')['id']} for i,t in enumerate(outputs)]
                if not active and name!='final':writes.append({'location':1 if name=='deferred1' else 2,'texture':texture(12,'alt')['id']})
                if name=='final':writes=[{'location':0,'texture':offset+900}]
                events.append({'kind':'completed_quad','frame':frame,'name':name,'reads':[],'writes':writes,
                               'raster_state':{'viewport':[0,0,2560,1600]},'draw_fbo':123,
                               'presentation_target':{'texture':offset+900,'framebuffer':123,'width':2560,'height':1600}})
            for target in [3,5,7,13]:events.append({'kind':'swap_copy_return','frame':frame,
                                                  'source_texture':texture(target,'alt')['id'],'destination_texture':texture(target,'main')['id']})
            events.append({'kind':'final_return','frame':frame,'completed_frames':frame})
        events.append({'kind':'frame_start','frame':3,'images':images})
        return {'actual':{'completed_frames':2,'stopped':True,'error':'','sources':sources,'events':events}}

    def test_only_dead_storage_and_writes_can_disappear(self):
        native=self.packet();candidate=self.packet(True,100)
        result=contract.assess({'native':native,'candidate':candidate,'return':self.packet(False,200)})
        self.assertEqual(0,result['candidate']['frames'][0]['nominal_pair_bytes'])
        bad=self.packet(True);bad['actual']['sources'][1]['fragment_source']+='changed_live_output();'
        with self.assertRaisesRegex(ValueError,'Another attached'):contract.assess({'native':native,'candidate':bad})
        bad=self.packet(True);next(e for e in bad['actual']['events'] if e.get('name')=='composite1')['writes'][0]['texture']+=1
        with self.assertRaisesRegex(ValueError,'Surviving physical'):contract.assess({'native':native,'candidate':bad})

    def test_refuses_retained_allocation_live_sampler_or_missing_lifecycle(self):
        bad=self.packet(True);bad['actual']['events'][0]['images'].append(self.packet()['actual']['events'][0]['images'][12])
        with self.assertRaisesRegex(ValueError,'allocation differs'):contract.source_work(bad,active=True)
        bad=self.packet(True);next(e for e in bad['actual']['events'] if e.get('name')=='composite1')['reads']=[{'target':12}]
        with self.assertRaisesRegex(ValueError,'live RT12 sampler'):contract.source_work(bad,active=True)
        bad=self.packet(True);bad['actual']['events'].pop()
        with self.assertRaisesRegex(ValueError,'frames/reset missing'):contract.source_work(bad,active=True)

    def test_only_unused_mrt_and_store_changes_with_native_dh_branch_retained(self):
        pack=producer.ROOT/'dependencies/upstream/pack-expansion/Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip'
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);options=root/'native.txt';profile=json.loads(producer.PROFILE.read_text())
            options.write_text(''.join(f'{k}={v}\n' for k,v in sorted(profile.items())))
            out=root/'candidate.zip';producer.build(pack,options,out)
            with zipfile.ZipFile(pack) as original,zipfile.ZipFile(out) as candidate:
                self.assertEqual(original.namelist(),candidate.namelist())
                self.assertEqual(sorted(producer.MRT_ANCHORS),sorted(n for n in original.namelist() if original.read(n)!=candidate.read(n)))
                for member,(anchor,replacement) in producer.MRT_ANCHORS.items():
                    data=candidate.read(member).replace(producer.conditional(anchor,replacement),anchor,1)
                    if member.endswith('/composite.fsh'):
                        data=data.replace(b'#ifdef DISTANT_HORIZONS\r\n'+producer.OUTPUT+b'\r\n#endif',producer.OUTPUT,1)
                    self.assertEqual(original.read(member),data)
            self.assertEqual(options.read_bytes(),Path(str(out)+'.txt').read_bytes())
            profile['TAA']='false';options.write_text(''.join(f'{k}={v}\n' for k,v in profile.items()))
            with self.assertRaises(ValueError):producer.build(pack,options,root/'wrong.zip')
