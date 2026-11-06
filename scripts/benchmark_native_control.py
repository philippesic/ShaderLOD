"""Byte-identical original ZIP alias for cross-family reload noise checks.

Uses the existing private-Off lifecycle. This is never an optimization.
"""
from pathlib import Path
import shutil

import benchmark_iteration as iteration
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import benchmark_bliss_exact_ao as source_tools
import dev_client as dev
import experiment_lifecycle as life

ROOT=Path(__file__).resolve().parents[1]


def identity(pack,options):
    original=iteration.public_identity(pack,ROOT)
    native=dev.properties(Path(options).read_text())
    life.require(native and not any(key.startswith('SHADERLOD_') for key in native),
                 'Native control requires explicit original options without adapter selectors')
    inputs=[Path(__file__).resolve(),Path(staging.__file__).resolve(),Path(private_off.__file__).resolve()]
    return {'candidate':'native-control','family':original['family'],
            'original_sha256':life.digest(pack),'native_options':native,
            'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-native-control-'+life.digest(pack)[:16]+'.zip',
            'scope':'Known-null byte-identical original ZIP alias; Master Off, no public requests. Not an optimization.'}


def build(pack,options,output):
    output=Path(output);sidecar=Path(str(output)+'.txt')
    life.require(not output.exists() and not sidecar.exists(),'Fresh native-control outputs required')
    output.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(pack,output);shutil.copyfile(options,sidecar)
    pin=life.digest(pack);life.require(life.digest(output)==pin,'Native-control ZIP alias differs')
    return {'original_sha256':pin,'candidate_sha256':pin,'known_null':True}


def stage(op,pack,options,identity):
    return staging.stage(op,pack,options,identity,build=build)


def activation(state,identity,*,expected_pack):
    staging.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:overworld' and not state.get('dh_installed')
                 and 'voxy' not in state.get('loaded_stack',{}),'Native control requires Overworld without DH/Voxy')


def source_work(packet,*,active):
    actual=packet['actual'];events=actual['events'];sources=actual['sources']
    life.require(sources and len([e for e in events if e['kind']=='final_return'])==2,
                 'Native-control returned source/work capture incomplete')
    return {'completed_frames':2,'source_count':len(sources),'known_null':True,
            'scope':'Byte-identical alias, actual linked sources and returned observed work; no optimization benefit.'}


def physical_signature(packet):
    """Compare logical image banks, never transient GL object names."""
    events=packet['actual']['events'];starts=[e for e in events if e['kind']=='frame_start']
    finals=[e['frame'] for e in events if e['kind']=='final_return'];result=[]
    life.require(len(starts)==3 and len(finals)==2,'Native-control complete frames/reset absent')
    for frame in finals:
        start=next(e for e in starts if e['frame']==frame)
        banks={image[bank]['id']:(image['target'],bank) for image in start['images'] for bank in ('main','alt')}
        life.require(len(banks)==2*len(start['images']),'Native-control image ownership ambiguous')
        custom={}
        for owner in start.get('custom_textures',[]):
            image=owner['texture'];key=image.get('id')
            life.require(image.get('exists') is True and type(key) is int and key>0
                         and image.get('width',0)>0 and image.get('height',0)>0,
                         'Native-control custom texture owner is incomplete')
            descriptor=(owner['stage'],owner['name'],tuple(sorted((k,v) for k,v in image.items() if k!='id')))
            custom.setdefault(key,[]).append(descriptor)
        def bank(value,*,custom_read=False):
            key=value['id'] if isinstance(value,dict) else value
            if key in banks:return banks[key]
            if custom_read and key in custom and isinstance(value,dict):
                shape=tuple(sorted((k,v) for k,v in value.items() if k!='id'))
                life.require(all(owner[2]==shape for owner in custom[key]),'Native-control custom texture changed across draw')
                return ('custom',tuple(sorted(custom[key])))
            raise ValueError('Unowned native-control image')
        rows=[]
        for event in events:
            if event['frame']!=frame:continue
            kind=event['kind'];row={'kind':kind}
            if kind in ('completed_quad','terrain_setup'):
                row['name']=event.get('name',event.get('pass'))
                row['reads']=[(read['target'],bank(read['texture'],custom_read=True)) for read in event['reads']]
                writes=[]
                for write in event['writes']:
                    image=write['texture']
                    if image in banks:writes.append((write['location'],bank(image)))
                    else:
                        owner=event.get('presentation_target',{})
                        life.require(owner.get('texture')==image and write['location']==0,'Unknown native-control writer')
                        writes.append((0,('presentation',owner['width'],owner['height'])))
                row['writes']=writes;row['raster']=event['raster_state']
            elif kind=='mip_return':row['image']=bank(event['texture']);row['read_alt']=event['read_alt']
            elif kind=='swap_copy_return':row['source']=bank(event['source_texture']);row['destination']=bank(event['destination_texture'])
            else:continue
            rows.append(row)
        result.append(rows)
    return result


def assess(observations):
    native_label=next(label for label in observations if label.startswith('native'))
    native={s['name']:s for s in observations[native_label]['actual']['sources']}
    baseline=physical_signature(observations[native_label]);result={}
    for label,packet in observations.items():
        result[label]=source_work(packet,active=label=='candidate')
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Native-control selected pass set changed')
        for name,source in sources.items():
            for key in ('vertex_source','fragment_source'):
                life.require(source_tools.canonical(source[key])==source_tools.canonical(native[name][key]),
                             'Native-control attached source differs: '+name+'/'+key)
            for key in ('parsed_mips','parsed_flips','parsed_draw_buffers'):
                life.require((key in source)==(key in native[name]) and source.get(key)==native[name].get(key),
                             'Native-control parsed graph differs')
        life.require(physical_signature(packet)==baseline,'Native-control physical work differs')
    return result
