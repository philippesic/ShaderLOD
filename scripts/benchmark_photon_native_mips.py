"""Maintained owned private native366/no-DH Nether G02 experiment."""
from pathlib import Path
import build_photon_native_mips as producer
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life

def identity(pack,options):
    native=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(staging.__file__).resolve(),Path(private_off.__file__).resolve(),producer.PROFILE]
    pin=life.digest(inputs[0])
    return {'candidate':'g02-photon-native','family':'photon','original_sha256':producer.ORIGINAL,'native_options':native,
            'producers':{str(p):life.digest(p) for p in inputs},'generated':'ShaderLOD-G02-Photon-native-'+pin[:12]+'.zip',
            'scope':'Private native366 no-DH Nether d1+d2+RT11 deletion; no public adapter/bridge or quality change.'}
def stage(op,pack,options,identity):return staging.stage(op,pack,options,identity,build=producer.build)
def activation(state,identity,*,expected_pack):
    staging.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:the_nether' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack',{}) and state.get('fallback') is False,
                 'Private Photon work deletion requires actual native Nether without DH/Voxy/fallback')
def assess(observations):
    baseline={v['name']:v['parsed_mips'] for v in observations['native']['actual']['sources']}
    result={}
    for label,packet in observations.items():
        active=label=='candidate';actual=packet['actual'];sources={v['name']:v['parsed_mips'] for v in actual['sources']}
        expected=set(baseline)-({'deferred1','deferred2'} if active else set())
        life.require(set(sources)==expected,'Private Photon selected shader set differs beyond two cloud passes')
        life.require(all(v==sources[n] for n,v in baseline.items() if n not in ('deferred1','deferred2','deferred4'))
                     and sources['deferred4']==([5] if active else [5,11]),'Private Photon changed another native mip directive')
        frames=[]
        for frame in [1,2]:
            quads=[e for e in actual['events'] if e['kind']=='completed_quad' and e['frame']==frame]
            names=[q['name'] for q in quads]
            native_order=[e['name'] for e in observations['native']['actual']['events'] if e['kind']=='completed_quad' and e['frame']==frame]
            expected_order=[n for n in native_order if not active or n not in ('deferred1','deferred2')]
            life.require(names==expected_order,'Private Photon changed another native executed pass/order')
            life.require(all(names.count(n)==(0 if active else 1) for n in ['deferred1','deferred2'])
                         and not any(9<=r['target']<=12 for q in quads for r in q['reads']),
                         'Private Photon unused cloud pass execution/read trace differs')
            frames.append({'frame':frame,'cloud_quads':[q for q in quads if q['name'] in ['deferred1','deferred2']],
                           'target9_to12_active_reads':0,'completed_quad_count':len(quads)})
        result[label]={'frames':frames,'earlier_live_RT5_mip_retained':True,'all_other_selected_sources_and_mip_sets_retained':True}
    return result
