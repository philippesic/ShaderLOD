"""Physical joins for the reviewed native463 Bliss RT13 bounded overwrite.

Requires untimed raster-state observations. No optimization admission is granted.
"""
import experiment_lifecycle as life


def assess(packet,*,removed=False):
    actual=packet['actual'];events=actual['events'];rows=[]
    life.require(actual.get('completed_frames')==2 and actual.get('stopped') is True and not actual.get('error'),
                 'Two completed stopped copy-trace frames without errors required')
    life.require(len([e for e in events if e['kind']=='frame_start' and e['frame']==3])==1,
                 'Next-frame physical reset missing')
    for frame in [1,2]:
        current=[e for e in events if e['frame']==frame]
        life.require(len([e for e in current if e['kind']=='final_return' and e.get('completed_frames')==frame])==1,
                     'Completed final boundary missing')
        starts=[e for e in current if e['kind']=='frame_start']
        life.require(len(starts)==1,'Missing current physical images')
        images=[i for i in starts[0]['images'] if i['target']==13]
        life.require(len(images)==1,'Physical RT13 missing')
        image=images[0];banks={image[k]['id'] for k in ['main','alt']}
        life.require(len(banks)==2 and all(image[k].get('exists') is True and image[k].get('width')==2560
                     and image[k].get('height')==1600 for k in ['main','alt']),
                     'RT13 physical extent/identity differs from reviewed footprint')
        quads=[e for e in current if e['kind']=='completed_quad']
        producers=[q for q in quads if q['name']=='composite']
        consumers=[q for q in quads if q['name']=='composite2']
        life.require(len(producers)==len(consumers)==1,'Native fog producer/consumer stage differs')
        producer,consumer=producers[0],consumers[0]
        life.require(quads.index(producer)<quads.index(consumer),'RT13 read precedes current producer')
        writes=[w for w in producer['writes'] if w['location']==0]
        reads=[r for r in consumer['reads'] if r['target']==13]
        life.require(len(writes)==len(reads)==1 and writes[0]['texture'] in banks
                     and reads[0]['texture']['id']==writes[0]['texture'],
                     'RT13 consuming texture is not the current producer attachment')
        for q in quads:
            if q is not consumer:
                life.require(not any(r['target']==13 for r in q['reads']),'Unexpected active RT13 reader')
            if q is not producer:
                life.require(not any(w['texture'] in banks for w in q['writes']),'Unexpected RT13 writer')
        state=producer.get('raster_state',{})
        life.require(state.get('viewport')==[0,0,2560,1600] and state.get('color_mask0')==[True]*4
                     and all(state.get(k) is False for k in ['scissor','depth_test','rasterizer_discard','blend0']),
                     'Fog coverage clipped/masked/blended or raster-state observation missing')
        copies=[e for e in current if e['kind']=='swap_copy_return' and e['destination_texture'] in banks]
        final=next(e for e in current if e['kind']=='final_return')
        life.require(all(current.index(e)<current.index(final) for e in [*quads,*copies]),
                     'Copy or consuming quad lies outside completed final boundary')
        life.require(len(copies)==(0 if removed else 1),'RT13 copy omission differs')
        if not removed:
            life.require(copies[0].get('source_texture')==writes[0]['texture']
                         and copies[0]['destination_texture']!=writes[0]['texture'],'RT13 final copy source/destination join failed')
        rows.append({'frame':frame,'producer_texture':writes[0]['texture'],'consuming_texture':reads[0]['texture']['id'],
                     'sample_footprint':[0,1281,0,801],'producer_fraction':0.51,'retained_copy_count':len(copies)})
    return {'frames':rows,'scope':'Physical/raster joins for reviewed native463 Overworld 2560x1600 RT13 source footprint; no pixel/motion or broad compatibility proof'}
