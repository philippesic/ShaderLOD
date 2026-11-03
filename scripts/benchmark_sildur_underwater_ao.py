"""Private native115 unused AO result contract; actual underwater uniform required."""
from pathlib import Path

import build_sildur_underwater_ao as producer
import benchmark_bliss_exact_ao as text_contract
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life


def identity(pack, options):
    native=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),producer.PROFILE,
            Path(text_contract.__file__).resolve(),Path(staging.__file__).resolve(),Path(private_off.__file__).resolve()]
    return {'candidate':'n04-sildur-underwater-ao','family':'sildurs-vibrant-shaders','original_sha256':producer.ORIGINAL,
            'native_options':native,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N04-Sildur-underwater-'+life.digest(inputs[0])[:12]+'.zip',
            'required_dimension':'minecraft:overworld',
            'scope':'Private native115 SSDO result avoidance under existing uniform condition; actual underwater uniform/source and timing required. No DH/Voxy.'}


def stage(op,pack,options,contract):
    return staging.stage(op,pack,options,contract,build=producer.build)


def activation(state,contract,*,expected_pack):
    staging.activation(state,contract,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:overworld' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack',{}) and state.get('fallback') is False,
                 'Private Sildur AO requires native Overworld without DH/Voxy/fallback')


def canonical(text):
    return text_contract.canonical(text)


def compiled(text):
    # Only the exact native profile constant is expanded; all other operations compare whole-source.
    return canonical(text.decode().replace('ao_strength','1.0'))


def source_work(packet,*,active):
    actual=packet['actual'];sources={r['name']:r for r in actual['sources']}
    life.require(actual.get('settled_rearm') is True and 'deferred' in sources,
                 'Settled Sildur deferred consumer required')
    fragment=canonical(sources['deferred']['fragment_source'])
    life.require(fragment.count(compiled(producer.REPLACEMENT if active else producer.ANCHOR))==1
                 and 'uniformintisEyeInWater;' in fragment,
                 'Actual Sildur native/guarded SSDO or uniform differs')
    rows=[]
    for frame in [1,2]:
        events=[e for e in actual['events'] if e['kind']=='completed_quad' and e['frame']==frame and e['name']=='deferred']
        life.require(len(events)==1,'Exactly one Sildur AO consumer per observed frame required')
        uniform=events[0].get('sildur_ao_uniform',{})
        life.require(type(uniform.get('location')) is int and uniform['location']>=0
                     and type(uniform.get('is_eye_in_water')) is int and uniform['is_eye_in_water']==1,
                     'Actual underwater Sildur uniform not established')
        rows.append(uniform)
    return {'compiled_guard':active,'consuming_stage':'deferred','uniform_frames':rows,
            'scope':'Actual attached call guard and settled underwater uniform; not executed branches, avoided samples or backend work.'}


def assess(observations):
    native={s['name']:s for s in observations['native']['actual']['sources']};result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Private Sildur selected program set differs')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source']:
                value=canonical(source[key])
                if active and name=='deferred' and key=='fragment_source':
                    value=value.replace(compiled(producer.REPLACEMENT),compiled(producer.ANCHOR),1)
                life.require(value==canonical(native[name][key]),'Private Sildur changed another attached operation: '+name+'/'+key)
            for key in ['parsed_mips','parsed_flips','parsed_draw_buffers']:
                life.require(source.get(key)==native[name].get(key),'Private Sildur pass directives differ')
    return result
