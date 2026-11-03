"""Executed omission checks, separate from each family's source liveness proof."""
import experiment_lifecycle as life


def assess(observations,*,family,states):
    target=0 if family in ('complementary-reimagined','complementary-unbound') else 11 if family=='photon' else 1
    result={'family':family,'target':target,'arms':{},'scope':'Actual selected pass source/directive and per-pass physical mip-return omission, two completed frames plus next-frame reset. Separate source/formal-sampler and full lifetime reasoning still required; no image parity or speed inference.'}
    for label,packet in observations.items():
        active=label in ('candidate','public');actual=packet['actual'];events=actual['events'];sources=actual['sources']
        options=states[label]['effective_options']
        role=('deferred1' if family in ('complementary-reimagined','complementary-unbound') else 'deferred4' if family=='photon'
              else 'composite13' if family=='astralex' else 'composite16' if family=='solas-shader' and options.get('SHADERLOD_SOLAS_PIXEL_GRAPH')=='1'
              else 'composite15' if family=='solas-shader' else 'composite6')
        roles=[role,'composite7'] if family=='bsl-shaders' else [role]
        finals=[e['frame'] for e in events if e['kind']=='final_return'];starts=[e for e in events if e['kind']=='frame_start']
        life.require(len(finals)==2 and finals[1]==finals[0]+1 and len(starts)==3 and starts[-1]['frame']==finals[-1]+1,'Missing consecutive complete mip frames/reset')
        rows=[]
        for name in roles:
            match=[s for s in sources if s['name']==name]
            life.require(len(match)==1 and (target in match[0]['parsed_mips']) is (not active),'Selected linked-pass/directive differs: '+name)
            for frame in finals:
                pending=[];found=[]
                for event in events:
                    if event['frame']!=frame:continue
                    if event['kind']=='mip_return':pending.append(event)
                    elif event['kind']=='completed_quad':
                        if event['name']==name:found.append((event,list(pending)))
                        pending=[]
                life.require(len(found)==1,'Selected consuming quad absent/repeated: '+name)
                quad,mips=found[0]
                life.require(len(mips)==len(match[0]['parsed_mips']),'Parsed versus actual selected mip count differs: '+name)
                start=next(e for e in starts if e['frame']==frame);images=[e for e in start['images'] if e['target']==target]
                ids={bank['id'] for image in images for bank in (image['main'],image['alt'])}
                omitted=[e for e in mips if e['texture']['id'] in ids]
                life.require(len(omitted)==(0 if active else 1),'Actual selected physical generation differs: '+name)
                rows.append({'frame':frame,'name':name,'physical_mip_returns':len(omitted),'images':images,'quad':quad})
        reset=[i for i in starts[-1]['images'] if i['target']==target]
        life.require((not reset and active and family=='photon') or len(reset)==1
                     and all(reset[0][bank].get('min_filter')==9729 for bank in ('main','alt')),'Native final/next-frame target filter reset differs')
        result['arms'][label]={'roles':roles,'executed':rows,'next_frame_reset':reset,'candidate_image_absent':not reset}
    return result
