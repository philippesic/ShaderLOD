"""New N03 original Photon Nether cloud-copy benchmark admission and joins."""
from pathlib import Path
import build_photon_dead_cloud_copies as producer
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life

def identity(pack,options):
    settings=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(producer.native.__file__).resolve(),
            Path(staging.__file__).resolve(),Path(private_off.__file__).resolve(),producer.PROFILE]
    return {'candidate':'n03-photon-cloud-copy','family':'photon','original_sha256':producer.ORIGINAL,
            'required_dimension':'minecraft:the_nether',
            'native_options':settings,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N03-Photon-cloud-copy-'+life.digest(inputs[0])[:12]+'.zip',
            'scope':'Private native366 Nether ONLY, noDH/Voxy, dead RT9..12 final copies; original restored before dimension changes.'}

def stage(op,pack,options,identity):return staging.stage(op,pack,options,identity,build=producer.build)

def activation(state,identity,*,expected_pack):
    staging.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('dimension')=='minecraft:the_nether' and state.get('dh_installed') is False
                 and 'voxy' not in state.get('loaded_stack',{}) and state.get('fallback') is False
                 and state.get('width')==2560 and state.get('height')==1600,
                 'Photon cloud-copy candidate requires actual native Nether 2560x1600 without DH/Voxy/fallback')

def source_work(packet,*,active):
    actual=packet['actual'];events=actual['events'];result=[]
    life.require(actual.get('completed_frames')==2 and actual.get('stopped') is True and not actual.get('error'),
                 'Two stopped completed error-free Photon copy frames required')
    life.require(sum(e['kind']=='frame_start' and e['frame']==3 for e in events)==1,'Next-frame reset missing')
    stage=[s for s in actual['sources'] if s['name']=='composite16']
    life.require(len(stage)==1 and stage[0].get('parsed_flips')==({str(t):True for t in producer.TARGETS} if active else {}),
                 'Photon terminal explicit flips differ')
    for frame in [1,2]:
        current=[e for e in events if e['frame']==frame]
        starts=[e for e in current if e['kind']=='frame_start'];finals=[e for e in current if e['kind']=='final_return']
        life.require(len(starts)==len(finals)==1 and finals[0].get('completed_frames')==frame,'Physical frame/final boundary missing')
        images=starts[0]['images'];banks={i[k]['id']:(i['target'],k) for i in images for k in ['main','alt']}
        life.require(len(banks)==2*len(images),'Ambiguous physical image identities')
        for target in producer.TARGETS:
            matching=[i for i in images if i['target']==target]
            life.require(len(matching)==1 and all(matching[0][k].get('exists') is True and matching[0][k].get('width')==(640 if target in (9,10) else 2560)
                         and matching[0][k].get('height')==(400 if target in (9,10) else 1600) for k in ['main','alt']),'Cloud image extent/ownership differs')
        def join(texture):
            key=texture['id'] if isinstance(texture,dict) else texture
            life.require(type(key) is int and key in banks,'Unknown physical image in Photon trace')
            return banks[key]
        copies=[e for e in current if e['kind']=='swap_copy_return']
        targets=[]
        for e in copies:
            source,dest=join(e.get('source_texture')),join(e['destination_texture'])
            life.require(source[0]==dest[0] and source!=dest and dest[1]=='main','Final copy source/destination join differs')
            targets.append(dest[0])
        life.require(sorted(targets)==([4,5,6,7,14] if active else [4,5,6,7,9,10,11,12,14]),'Another retained copy changed')
        signature=[]
        for e in current:
            kind=e['kind']
            if kind in ('completed_quad','terrain_setup'):
                raster=e.get('raster_state',{})
                life.require(isinstance(raster.get('viewport'),list) and len(raster['viewport'])==4
                             and isinstance(raster.get('color_mask0'),list) and len(raster['color_mask0'])==4
                             and all(type(v) is bool for v in raster['color_mask0'])
                             and all(type(raster.get(k)) is bool for k in ['scissor','depth_test','rasterizer_discard','blend0']),
                             'Current physical raster-state receipt missing')
                life.require(not any(r['target'] in producer.TARGETS for r in e['reads']),'Unexpected live cloud sampler')
                def output_join(write):
                    if e['name']!='final':return join(write['texture'])
                    owner=e.get('presentation_target',{})
                    life.require(write['texture'] not in banks and write['location']==0
                                 and owner.get('texture')==write['texture']
                                 and type(owner.get('framebuffer')) is int and owner['framebuffer']>0
                                 and type(e.get('draw_fbo')) is int and e['draw_fbo']>0
                                 and type(owner.get('texture')) is int and owner['texture']>0
                                 and owner.get('width')==2560 and owner.get('height')==1600,
                                 'Unowned external final attachment')
                    # Iris wraps the same Minecraft color image in its own FBO.
                    return ('minecraft-presentation','color')
                signature.append({'kind':kind,'name':e['name'],'reads':[(r['target'],join(r['texture'])) for r in e['reads']],
                                  'writes':[(w['location'],output_join(w)) for w in e['writes']],
                                  'raster_state':e.get('raster_state')})
            elif kind=='mip_return':signature.append({'kind':kind,'image':join(e['texture']),'read_alt':e['read_alt']})
            if kind in ('completed_quad','terrain_setup','mip_return','swap_copy_return'):
                life.require(current.index(e)<current.index(finals[0]),'Work outside final return boundary')
        names=[s['name'] for s in signature if s['kind']=='completed_quad']
        life.require(names[-2:]==['composite16','final'] and all(names.count(n)==1 for n in ['deferred1','deferred2']),
                     'Native cloud passes or last active consumer differ')
        result.append({'frame':frame,'retained_copy_targets':sorted(targets),'copy_order':targets,
                       'physical_pass_mip_signature':signature})
    return {'frames':result,'scope':'Actual two-frame copy source/destination and earlier physical pass/mip joins; dead-value safety from pinned source proof, not pixel parity.'}

def assess(observations):
    native={s['name']:s for s in observations['native']['actual']['sources']};result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Selected native pass set changed')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source','parsed_mips','parsed_draw_buffers']:
                life.require(source.get(key)==native[name].get(key),'Native source/mip/draw directive changed: '+name+'/'+key)
            expected={str(t):True for t in producer.TARGETS} if active and name=='composite16' else native[name].get('parsed_flips')
            life.require(source.get('parsed_flips')==expected,'Another explicit flip changed')
        if label!='native':
            for row,baseline in zip(result[label]['frames'],result['native']['frames']):
                life.require(row['physical_pass_mip_signature']==baseline['physical_pass_mip_signature'],
                             'Earlier physical pass/mip bindings or raster state changed')
                expected=[t for t in baseline['copy_order'] if not active or t not in producer.TARGETS]
                life.require(row['copy_order']==expected,'Retained copy order changed')
    return result
