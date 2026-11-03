"""Owned original-derived Bliss exact sky-AO benchmark admission."""
from pathlib import Path
import re
import build_bliss_exact_sky_ao as producer
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life


def identity(pack,options):
    native=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(staging.__file__).resolve(),
            Path(private_off.__file__).resolve(),producer.PROFILE]
    pin=life.digest(inputs[0])
    return {'candidate':'n04-bliss-sky','family':'bliss-shader','original_sha256':producer.ORIGINAL,
            'native_options':native,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N04-Bliss-sky-'+pin[:12]+'.zip',
            'scope':'Private original native463 Overworld sky SSAO bypass; no DH/Voxy, public requests empty.'}


def stage(op,pack,options,identity):
    return staging.stage(op,pack,options,identity,build=producer.build)


def activation(state,identity,*,expected_pack):
    staging.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:overworld' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack',{}) and state.get('fallback') is False,
                 'Bliss exact sky-AO requires native Overworld without DH/Voxy/fallback')
    life.require(identity['native_options'].get('indirect_effect')=='1',
                 'Native consuming SSAO branch required')


def canonical(source):
    source=re.sub(r'/\*.*?\*/|//[^\n]*','',source,flags=re.S)
    # Iris's GLSL emitter writes float suffixes on unsuffixed native float literals.
    source=re.sub(r'(?<![\w.])(\d+\.\d*(?:[eE][+-]?\d+)?)[fF]\b',r'\1',source)
    return re.sub(r'\s+','',source)


def source_work(packet,*,active):
    """Actual attached consuming source and returned draws; not branch-hit counts."""
    actual=packet['actual'];sources={r['name']:r for r in actual['sources']}
    life.require('composite1' in sources,'Bliss consuming composite1 source missing')
    frag=canonical(sources['composite1']['fragment_source'])
    call=canonical(producer.ANCHOR.decode());guard=canonical(producer.REPLACEMENT.decode())
    life.require(frag.count(guard if active else call)==1,
                 'Bliss compiled native/sky guard branch missing')
    life.require('if(swappedDepth>=1.0)SSAO_SSS=vec2(1.0,0.0);' in frag,
                 'Bliss native final sky overwrite missing')
    for frame in [1,2]:
        life.require(any(e['kind']=='completed_quad' and e['frame']==frame and e['name']=='composite1'
                         for e in actual['events']),'Bliss consuming composite1 did not execute')
    return {'compiled_guard':active,'consuming_stage':'composite1','completed_observed_frames':2,
            'scope':'Actual attached source and consuming returned quads; no avoided-fragment count'}


def depth_census(packet):
    """Validate optional census separately from historical source-only observations."""
    life.require(packet['actual'].get('settled_rearm') is True,'Settled no-reload AO census required')
    rows=[]
    for frame in [1,2]:
        events=[e for e in packet['actual']['events'] if e['kind']=='completed_quad'
                and e['frame']==frame and e['name']=='composite1']
        life.require(len(events)==1,'One AO consumer per observed frame required')
        row=events[0].get('bliss_ao_depth_census',{})
        before=events[0].get('bliss_ao_depth_census_before',{})
        for key in ['sky_input_texels','non_sky_input_texels','nonfinite_input_texels']:
            life.require(type(row.get(key)) is int and row[key]>=0,'Typed AO depth census required')
        life.require(row.get('width')==2560 and row.get('height')==1600
                     and row['sky_input_texels']+row['non_sky_input_texels']==2560*1600
                     and row['nonfinite_input_texels']==0,'Complete finite pinned AO depth census required')
        life.require(type(row.get('texture')) is int and row['texture']>0 and row.get('owned_depth_texture')==row['texture'],
                     'AO census actual Iris depth owner differs')
        life.require(before==row,'AO depth changed across actual consumer quad')
        rows.append(row)
    return {'frames':rows,'scope':'Actual bound depth texel opportunity, not executed branches, physical work or image parity.'}


def assess(observations):
    native={s['name']:s for s in observations['native']['actual']['sources']}
    result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Bliss selected pass set changed')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source']:
                value=canonical(source[key])
                if active and name=='composite1' and key=='fragment_source':
                    value=value.replace(canonical(producer.REPLACEMENT.decode()),canonical(producer.ANCHOR.decode()),1)
                life.require(value==canonical(native[name][key]),'Bliss changed another attached shader operation: '+name+'/'+key)
            for key in ['parsed_mips','parsed_flips','parsed_draw_buffers']:
                life.require(source.get(key)==native[name].get(key),'Bliss changed native pass directives')
    return result
