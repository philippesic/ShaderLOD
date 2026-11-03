"""Maintained private native463 RT13 late-flip benchmark contracts."""
from pathlib import Path
import build_bliss_dead_fog_copy as producer
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import benchmark_bliss_copy_trace as trace
import experiment_lifecycle as life


def identity(pack,options):
    native=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(staging.__file__).resolve(),
            Path(private_off.__file__).resolve(),Path(trace.__file__).resolve(),Path(producer.native.__file__).resolve(),producer.PROFILE]
    pin=life.digest(inputs[0])
    return {'candidate':'n03-bliss-fog-copy','family':'bliss-shader','original_sha256':producer.ORIGINAL,
            'native_options':native,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N03-Bliss-fog-copy-'+pin[:12]+'.zip',
            'scope':'Private original native463 Overworld 2560x1600 RT13 lateflip; noDH/Voxy/public requests.'}


def stage(op,pack,options,identity):
    return staging.stage(op,pack,options,identity,build=producer.build)


def activation(state,identity,*,expected_pack):
    staging.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:overworld' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack',{}) and state.get('fallback') is False
                 and state.get('width')==2560 and state.get('height')==1600,
                 'Bliss fog-copy requires native Overworld 2560x1600 without DH/Voxy/fallback')


def source_work(packet,*,active):
    result=trace.assess(packet,removed=active)
    stage=[s for s in packet['actual']['sources'] if s['name']=='composite13']
    life.require(len(stage)==1 and stage[0].get('parsed_flips')==({'13':True} if active else {}),
                 'Bliss explicit late-flip directive differs')
    for frame in [1,2]:
        rows=[e for e in packet['actual']['events'] if e['frame']==frame]
        start=next(e for e in rows if e['kind']=='frame_start')
        target_by_texture={i[k]['id']:i['target'] for i in start['images'] for k in ['main','alt']}
        copies=[target_by_texture.get(e['destination_texture']) for e in rows if e['kind']=='swap_copy_return']
        life.require(all(type(t) is int for t in copies) and sorted(copies)==([3,5,7] if active else [3,5,7,13]),'Another native retained-copy target changed')
    return result


def assess(observations):
    native={s['name']:s for s in observations['native']['actual']['sources']};result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Bliss selected pass set changed')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source','parsed_mips','parsed_draw_buffers']:
                life.require(source.get(key)==native[name].get(key),'Bliss native attached source/directive changed: '+name+'/'+key)
            expected={'13':True} if active and name=='composite13' else native[name].get('parsed_flips')
            life.require(source.get('parsed_flips')==expected,'Another native explicit flip changed')
    return result
