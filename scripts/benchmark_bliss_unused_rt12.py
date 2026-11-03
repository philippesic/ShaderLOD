"""N02 native463 no-DH unused attachment/source contracts; no public widening."""
from pathlib import Path
import build_bliss_unused_rt12 as producer
import benchmark_bliss_exact_ao as ao
import benchmark_native_mips as staging
import benchmark_private_off as private_off
import experiment_lifecycle as life

STORE='if(depth<1.0)iris_FragData2=vec4(vec3(0.0),depth*depth*65000.0);elseiris_FragData2=vec4(vec3(0.0),65000.0);'
DECLARATION='layout(location=2)outvec4iris_FragData2;'
MRT={'deferred1':[4,12],'composite1':[3,14,12]}

def identity(pack,options):
    settings=producer.validate(pack,options)
    inputs=[Path(producer.__file__).resolve(),Path(__file__).resolve(),Path(producer.native.__file__).resolve(),
            Path(ao.__file__).resolve(),Path(staging.__file__).resolve(),Path(private_off.__file__).resolve(),producer.PROFILE]
    return {'candidate':'n02-bliss-unused-rt12','family':'bliss-shader','original_sha256':producer.ORIGINAL,
            'required_dimension':'minecraft:overworld',
            'native_options':settings,'producers':{str(p):life.digest(p) for p in inputs},
            'generated':'ShaderLOD-N02-Bliss-unused-rt12-'+life.digest(inputs[0])[:12]+'.zip',
            'scope':'Private original native463 Overworld noDH/Voxy: unused RT12 output/storage removal; no other option/AA changes.'}

def stage(op,pack,options,identity):return staging.stage(op,pack,options,identity,build=producer.build)

def activation(state,identity,*,expected_pack):
    ao.activation(state,identity,expected_pack=expected_pack)
    life.require(state.get('width')==2560 and state.get('height')==1600,'Pinned unused-attachment viewport required')

def source_work(packet,*,active):
    actual=packet['actual'];events=actual['events'];sources={s['name']:s for s in actual['sources']};rows=[]
    life.require(actual.get('completed_frames')==2 and actual.get('stopped') is True and not actual.get('error'),
                 'Two stopped complete error-free attachment frames required')
    starts=[e for e in events if e['kind']=='frame_start']
    life.require([s['frame'] for s in starts]==[1,2,3],'Consecutive attachment frames/reset missing')
    for name,outputs in MRT.items():
        expected=[t for t in outputs if t!=12] if active else outputs
        life.require(name in sources and sources[name].get('parsed_draw_buffers')==expected,'Unused attachment output map differs: '+name)
    fragment=ao.canonical(sources['composite1']['fragment_source'])
    life.require(fragment.count(STORE)==int(not active) and fragment.count(DECLARATION)==int(not active),
                 'Actual unused depth-store/declaration differs')
    for start in starts:
        frame=start['frame'];images={i['target']:i for i in start['images']}
        life.require((12 not in images) if active else 12 in images and all(images[12][bank].get('exists') is True
                     and images[12][bank].get('format')==34842 and images[12][bank].get('width')==2560
                     and images[12][bank].get('height')==1600 for bank in ['main','alt']),
                     'Actual RT12 allocation differs from expected absent/native RGBA16F pair')
        if frame==3:continue
        current=[e for e in events if e['frame']==frame];finals=[e for e in current if e['kind']=='final_return']
        life.require(len(finals)==1 and finals[0].get('completed_frames')==frame,'Completed attachment final boundary missing')
        banks={i[bank]['id']:(i['target'],bank) for i in images.values() for bank in ['main','alt']}
        life.require(len(banks)==2*len(images),'Ambiguous attachment images')
        def join(image):
            key=image['id'] if isinstance(image,dict) else image
            life.require(type(key) is int and key in banks,'Unowned attachment image')
            return banks[key]
        signature=[];copies=[]
        for event in current:
            kind=event['kind']
            if kind in ('completed_quad','terrain_setup'):
                life.require(not any(r['target']==12 for r in event['reads']),'Unexpected live RT12 sampler')
                reads=[(r['target'],join(r['texture'])) for r in event['reads']];writes=[]
                for w in event['writes']:
                    if event['name']=='final':
                        owner=event.get('presentation_target',{})
                        life.require(w['location']==0 and w['texture'] not in banks and owner.get('texture')==w['texture']
                                     and type(owner.get('framebuffer')) is int and owner['framebuffer']>0
                                     and type(event.get('draw_fbo')) is int and event['draw_fbo']>0 and owner.get('width')==2560
                                     and owner.get('height')==1600,'Actual final presentation owner missing')
                        writes.append((0,('presentation','color')));continue
                    target,bank=join(w['texture'])
                    if target==12:
                        life.require(not active and (event['name'],w['location']) in [('deferred1',1),('composite1',2)],
                                     'Unexpected RT12 writer')
                    else:writes.append((w['location'],(target,bank)))
                life.require(isinstance(event.get('raster_state'),dict),'Physical attachment raster receipt missing')
                signature.append({'kind':kind,'name':event['name'],'reads':reads,'writes':writes,'raster':event['raster_state']})
            elif kind=='mip_return':signature.append({'kind':kind,'image':join(event['texture']),'read_alt':event['read_alt']})
            elif kind=='swap_copy_return':
                source,dest=join(event.get('source_texture')),join(event['destination_texture'])
                life.require(source[0]==dest[0] and source!=dest and dest[1]=='main','Copy image join changed')
                copies.append(dest[0])
            if kind in ('completed_quad','terrain_setup','mip_return','swap_copy_return'):
                life.require(current.index(event)<current.index(finals[0]),'Attachment work outside final return')
        life.require(sorted(copies)==[3,5,7,13],'Another native copy changed')
        rows.append({'frame':frame,'allocated_rt12':not active,'nominal_pair_bytes':65536000 if not active else 0,
                     'physical_pass_signature':signature})
    return {'frames':rows,'scope':'Actual omitted target pair/output binding and unchanged surviving physical work; nominal storage is not process VRAM or measured bandwidth. No full-image/motion or DH/PBR claim.'}

def assess(observations):
    native={s['name']:s for s in observations['native']['actual']['sources']};result={}
    for label,packet in observations.items():
        active=label=='candidate';result[label]=source_work(packet,active=active)
        sources={s['name']:s for s in packet['actual']['sources']}
        life.require(sources.keys()==native.keys(),'Native selected pass set changed')
        for name,source in sources.items():
            for key in ['vertex_source','fragment_source']:
                expected=ao.canonical(native[name][key])
                if active and name=='composite1' and key=='fragment_source':
                    life.require(expected.count(STORE)==expected.count(DECLARATION)==1,'Native dead-store shape changed')
                    expected=expected.replace(STORE,'',1).replace(DECLARATION,'',1)
                life.require(ao.canonical(source[key])==expected,'Another attached operation changed: '+name+'/'+key)
            for key in ['parsed_mips','parsed_flips']:
                life.require(source.get(key)==native[name].get(key),'Another pass directive changed')
            expected=[t for t in MRT[name] if t!=12] if active and name in MRT else native[name].get('parsed_draw_buffers')
            life.require(source.get('parsed_draw_buffers')==expected,'Another native output map changed')
        if label!='native':
            for row,baseline in zip(result[label]['frames'],result['native']['frames']):
                life.require(row['physical_pass_signature']==baseline['physical_pass_signature'],'Surviving physical work or bindings changed')
    return result
