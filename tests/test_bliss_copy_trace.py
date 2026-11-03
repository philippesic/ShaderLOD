import copy
import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_bliss_copy_trace as trace
import benchmark_bliss_dead_copy as candidate

class BlissCopyTraceTest(unittest.TestCase):
    def packet(self,removed=False):
        events=[]
        for frame in [1,2]:
            state=dict(viewport=[0,0,2560,1600],color_mask0=[True]*4,scissor=False,
                       depth_test=False,rasterizer_discard=False,blend0=False)
            events.extend([
                dict(frame=frame,kind='frame_start',images=[dict(target=13,
                     main=dict(id=66,exists=True,width=2560,height=1600),alt=dict(id=67,exists=True,width=2560,height=1600))]),
                dict(frame=frame,kind='completed_quad',name='composite',reads=[],writes=[dict(location=0,texture=67)],raster_state=state),
                dict(frame=frame,kind='completed_quad',name='composite2',reads=[dict(target=13,texture=dict(id=67))],writes=[]),
            ])
            if not removed:events.append(dict(frame=frame,kind='swap_copy_return',source_texture=67,destination_texture=66))
            events.append(dict(frame=frame,kind='final_return',completed_frames=frame))
        events.append(dict(frame=3,kind='frame_start',images=[]))
        return {'actual':dict(completed_frames=2,stopped=True,error='',events=events)}

    def test_native_and_removed_copy_require_physical_current_writer(self):
        trace.assess(self.packet());trace.assess(self.packet(True),removed=True)
        p=self.packet();p['actual']['events'][2]['reads'][0]['texture']['id']=66
        with self.assertRaisesRegex(ValueError,'current producer'):trace.assess(p)

    def test_masked_scissored_missing_state_and_wrong_extent_refused(self):
        for update in [dict(scissor=True),dict(depth_test=True),dict(blend0=True),dict(color_mask0=[True,False,True,True]),dict(viewport=[0,0,1280,800])]:
            p=self.packet();p['actual']['events'][1]['raster_state'].update(update)
            with self.subTest(update=update),self.assertRaises(ValueError):trace.assess(p)
        p=self.packet();del p['actual']['events'][1]['raster_state']
        with self.assertRaises(ValueError):trace.assess(p)
        p=self.packet();p['actual']['events'][0]['images'][0]['main']['width']=1280
        with self.assertRaises(ValueError):trace.assess(p)

    def test_new_reader_or_unremoved_copy_refused(self):
        p=self.packet();extra=copy.deepcopy(p['actual']['events'][2]);extra['name']='composite3';p['actual']['events'].insert(3,extra)
        with self.assertRaisesRegex(ValueError,'Unexpected active'):trace.assess(p)
        with self.assertRaisesRegex(ValueError,'omission'):trace.assess(self.packet(),removed=True)

    def test_incomplete_final_reset_or_observer_error_refused(self):
        for kind in ['final_return','frame_start']:
            p=self.packet();p['actual']['events']=[e for e in p['actual']['events'] if e['kind']!=kind]
            with self.subTest(kind=kind),self.assertRaises(ValueError):trace.assess(p)
        p=self.packet();p['actual']['error']='observer failed'
        with self.assertRaises(ValueError):trace.assess(p)

    def full_packet(self,removed=False):
        p=self.packet(removed)
        p['actual']['sources']=[dict(name='composite13',vertex_source='nativeV',fragment_source='nativeF',
                                    parsed_flips={'13':True} if removed else {},parsed_mips=[],parsed_draw_buffers=[7])]
        for frame in [1,2]:
            start=next(e for e in p['actual']['events'] if e['frame']==frame and e['kind']=='frame_start')
            for i in [3,5,7]:
                start['images'].append(dict(target=i,main=dict(id=i*10),alt=dict(id=i*10+1)))
                boundary=next(e for e in p['actual']['events'] if e['frame']==frame and e['kind']=='final_return')
                index=p['actual']['events'].index(boundary)
                p['actual']['events'].insert(index,dict(frame=frame,kind='swap_copy_return',destination_texture=i*10,source_texture=i*10+1))
        return p

    def test_source_and_other_copy_contract(self):
        candidate.assess({'native':self.full_packet(),'candidate':self.full_packet(True),'return':self.full_packet()})
        p=self.full_packet(True);p['actual']['sources'][0]['vertex_source']='changed'
        with self.assertRaisesRegex(ValueError,'attached source'):candidate.assess({'native':self.full_packet(),'candidate':p})
        p=self.full_packet(True);p['actual']['events']=[e for e in p['actual']['events'] if not(e['kind']=='swap_copy_return' and e['destination_texture']==30)]
        with self.assertRaisesRegex(ValueError,'retained-copy'):candidate.source_work(p,active=True)
