import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_native_control as control


def packet(offset=0):
    events=[]
    images=[{'target':0,'main':{'id':1+offset},'alt':{'id':2+offset}}]
    for frame in (1,2):
        events.extend([
            {'kind':'frame_start','frame':frame,'images':images},
            {'kind':'completed_quad','frame':frame,'name':'composite','reads':[{'target':0,'texture':{'id':1+offset}}],
             'writes':[{'location':0,'texture':2+offset}],'raster_state':{'viewport':[0,0,2560,1600]}},
            {'kind':'mip_return','frame':frame,'texture':{'id':2+offset},'read_alt':True},
            {'kind':'swap_copy_return','frame':frame,'source_texture':1+offset,'destination_texture':2+offset},
            {'kind':'final_return','frame':frame}])
    events.append({'kind':'frame_start','frame':3,'images':images})
    source={'name':'composite','vertex_source':'void main(){}','fragment_source':'void main(){float x=1.0;}',
            'parsed_mips':[0],'parsed_flips':{},'parsed_draw_buffers':[0]}
    return {'actual':{'sources':[source],'events':events}}


class NativeControlTest(unittest.TestCase):
    def test_custom_sampler_override_is_joined_to_current_iris_owner(self):
        observations={'native':packet(),'candidate':packet(10)}
        for offset,row in enumerate(observations.values()):
            image={'id':500+offset,'exists':True,'width':256,'height':256,'format':6408}
            for event in row['actual']['events']:
                if event['kind']=='frame_start':
                    event['custom_textures']=[{'stage':'DEFERRED','name':'colortex0','texture':image.copy()}]
                if event['kind']=='completed_quad':event['reads'][0]['texture']=image.copy()
        control.assess(observations)
        observations['candidate']['actual']['events'][1]['reads'][0]['texture']['width']=128
        with self.assertRaisesRegex(ValueError,'changed across draw'):control.assess(observations)

    def test_terrain_sources_have_no_fullscreen_flip_or_draw_metadata(self):
        observations={'native':packet(),'candidate':packet(10)}
        for row in observations.values():
            terrain=copy.deepcopy(row['actual']['sources'][0]);terrain['name']='sodium_solid'
            terrain.pop('parsed_flips');terrain.pop('parsed_draw_buffers');row['actual']['sources'].append(terrain)
        control.assess(observations)
        observations['candidate']['actual']['sources'][-1]['parsed_flips']={}
        with self.assertRaisesRegex(ValueError,'parsed graph differs'):control.assess(observations)

    def test_object_names_can_change_but_logical_reads_must_match(self):
        observations={'native':packet(),'candidate':packet(10),'return':packet(20)}
        self.assertEqual(3,len(control.assess(observations)))
        observations['candidate']['actual']['events'][1]['reads'][0]['texture']['id']=12
        with self.assertRaisesRegex(ValueError,'physical work differs'):control.assess(observations)

    def test_shader_or_directive_changes_are_not_known_null(self):
        for field,value in [('fragment_source','void main(){float x=2.0;}'),('parsed_mips',[])]:
            changed=packet(10);changed['actual']['sources'][0][field]=value
            with self.assertRaisesRegex(ValueError,'differs'):control.assess({'native':packet(),'candidate':changed})

    def test_unknown_images_and_partial_capture_are_refused(self):
        changed=packet();changed['actual']['events'][1]['writes'][0]['texture']=900
        with self.assertRaisesRegex(ValueError,'Unknown.*writer'):control.physical_signature(changed)
        changed=packet();changed['actual']['events'].pop()
        with self.assertRaisesRegex(ValueError,'complete frames'):control.physical_signature(changed)

    def test_native_control_never_activates_public_requests(self):
        identity={'native_options':{'TAA':'true'}}
        state={'pack':'Alias.zip','effective_options':identity['native_options'],'installed':{'master':False,'flags':{}},
               'dimension':'minecraft:overworld','dh_installed':False,'loaded_stack':{}}
        control.activation(state,identity,expected_pack='Alias.zip')
        state['installed']['flags']['early_alpha']='true'
        with self.assertRaisesRegex(ValueError,'no public requests'):control.activation(state,identity,expected_pack='Alias.zip')


if __name__=='__main__':unittest.main()
