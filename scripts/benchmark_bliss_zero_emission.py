"""Private original-derived Bliss opaque zero-contribution benchmark contract."""
from pathlib import Path
import build_bliss_zero_emission as producer
import benchmark_bliss_exact_ao as ao
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life

def identity(pack,options):
    native=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(ao.__file__).resolve(),
            Path(staging.__file__).resolve(),Path(private_off.__file__).resolve(),producer.PROFILE,
            Path(producer.native.__file__).resolve()]
    return {'candidate':'n04-bliss-zero','family':'bliss-shader','original_sha256':producer.ORIGINAL,
            'native_options':native,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N04-Bliss-zero-'+life.digest(inputs[0])[:12]+'.zip',
            'scope':'Private original native463 opaque zero-emission, Overworld noDH/Voxy; empty public requests.'}

def stage(op,pack,options,identity):return staging.stage(op,pack,options,identity,build=producer.build)

def activation(state,identity,*,expected_pack):
    ao.activation(state,identity,expected_pack=expected_pack)
    life.require(identity['native_options'].get('Emissive_Curve')=='2.0'
                 and identity['native_options'].get('Emissive_Brightness')=='1.0'
                 and identity['native_options'].get('EMISSIVE_TYPE')=='1','Exact native opaque emission required')

def source_work(packet,*,active,guard=producer.GUARD):
    actual=packet['actual'];sources={s['name']:s for s in actual['sources']}
    life.require('composite2' in sources,'Opaque emission consumer missing')
    frag=ao.canonical(sources['composite2']['fragment_source']);guard=ao.canonical(guard.decode())
    life.require(frag.count(guard)==int(active),'Actual opaque zero-emission guard differs')
    life.require('pow(Emission,2.0)' in frag and 'Albedo*1.0*autoBrightnessAdjust' in frag,
                 'Actual native emission fallback/constants missing')
    life.require('Emission(iris_FragData0.rgb,albedo,SpecularTex.a,exposure);' in frag,
                 'Actual opaque emission call missing')
    for frame in [1,2]:
        life.require(any(e['kind']=='completed_quad' and e['frame']==frame and e['name']=='composite2'
                         for e in actual['events']),'Opaque consuming quad did not return')
    return {'compiled_guard':active,'consuming_stage':'composite2','completed_observed_frames':2,
            'scope':'Attached guard/native fallback and returned opaque consumer; no branch hits or physical operation counts; translucent call inactive.'}

def assess(observations,*,guard=producer.GUARD):
    native={s['name']:s for s in observations['native']['actual']['sources']};result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active,guard=guard)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Selected pass set changed')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source']:
                value=ao.canonical(source[key])
                if active and name=='composite2' and key=='fragment_source':
                    value=value.replace(ao.canonical(guard.decode()),'',1)
                life.require(value==ao.canonical(native[name][key]),'Other attached operation changed: '+name+'/'+key)
            for key in ['parsed_mips','parsed_flips','parsed_draw_buffers']:
                life.require(source.get(key)==native[name].get(key),'Native pass directives changed')
    return result
