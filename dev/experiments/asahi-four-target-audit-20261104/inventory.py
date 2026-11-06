"""Read-only native target/copy screen from maintained graph/returned-work reports.

This inventory does not certify dead histories or authorize deleting an image.
"""
import collections
import json
from pathlib import Path
import re

HERE=Path(__file__).resolve().parent
# Nominal sized-format channel bytes; no compression/padding/residency inference.
BPP={33321:1,33325:2,33326:4,33327:4,33328:8,32849:3,32856:4,32857:4,32859:8,
     34842:8,34843:6,34836:16,35898:4,36758:3,36759:4}
ALIASES={'gcolor':0,'gdepth':1,'gnormal':2,'composite':3,'gaux1':4,'gaux2':5,'gaux3':6,'gaux4':7}

def root_index(name):
    m=re.fullmatch(r'colortex(\d+)',name)
    return int(m[1]) if m else ALIASES.get(name)

result={'scope':'Native Overworld, the recorded full settings/pose of each run only. Current bound sampler is a possible read, not proof that a texel is fetched. Terrain setup is not a returned draw count. Parsed nonexecuted roots are included conservatively. Zero observed reads never proves dead lifetime.',
        'limits':['The physical observer inventories colortexN uniforms only; legacy gcolor/gdepth/gnormal/composite/gaux aliases are covered conservatively by parsed roots, not complete live-read receipts.',
                  'Two returned observed frames do not cover startup, resize, dimension changes, animation or every material/branch.',
                  'Nominal level-0 target-pair and copy payload bytes are not GPU traffic, allocation cost, occupancy, VRAM residency or time.',
                  'Native custom textures can shadow a colortex name; physical image ownership is used for observed reads.',
                  'History, clears, swap order, compute/image roots and all downstream consumers need proof before omission.'],
        'targets':{}}
for shader in ['reimagined','unbound','bsl','makeup']:
    path=HERE/(shader+'-null-abba-v2-r1')/'summary.json'
    if not path.exists():continue
    summary=json.loads(path.read_text())
    if summary.get('evaluation',{}).get('validity')!='VALID':continue
    actual=summary['native_control_observations']['native_0']['actual'];events=actual['events']
    starts={e['frame']:e for e in events if e['kind']=='frame_start'}
    finals=[e['frame'] for e in events if e['kind']=='final_return']
    tables={image['target']:{'target':image['target'],'main':image['main'],'alt':image['alt'],
                            'actual_sampler_users':set(),'actual_write_bindings':set(),
                            'mips':0,'final_copies':0,'parsed_sampler_roots':set(),'parsed_draw_roots':set()}
            for image in starts[finals[0]]['images']}
    for frame in finals:
        banks={image[bank]['id']:image['target'] for image in starts[frame]['images'] for bank in ('main','alt')}
        for e in events:
            if e['frame']!=frame:continue
            for read in e.get('reads',[]):
                target=banks.get(read['texture']['id'])
                if target in tables:tables[target]['actual_sampler_users'].add(e.get('name',e['kind']))
            for write in e.get('writes',[]):
                target=banks.get(write['texture'])
                if target in tables:tables[target]['actual_write_bindings'].add(e.get('name',e['kind']))
            if e['kind']=='mip_return':tables[banks[e['texture']['id']]]['mips']+=1
            if e['kind']=='swap_copy_return':
                target=banks[e['source_texture']];assert target==banks[e['destination_texture']]
                tables[target]['final_copies']+=1
    baseline=HERE/(shader+'-repaired-baseline-r1')
    graph=next(baseline.glob('native-graph-*'));manifest=json.loads((graph/'manifest.json').read_text())
    for file in sorted(graph.glob('*')):
        if file.suffix not in ('.fsh','.vsh','.glsl','.csh'):continue
        source=re.sub(r'/\*.*?\*/|//[^\n]*','',file.read_text(),flags=re.S)
        for token in set(re.findall(r'\b(?:colortex\d+|gcolor|gdepth|gnormal|composite|gaux[1-4])\b',source)):
            index=root_index(token)
            if index in tables:tables[index]['parsed_sampler_roots'].add(file.name)
    for program in manifest['programs']:
        if not program.get('valid'):continue
        for index in program.get('draw_buffers',[]):
            if index in tables:tables[index]['parsed_draw_roots'].add(program['name'])
    for row in tables.values():
        for key,value in list(row.items()):
            if isinstance(value,set):row[key]=sorted(value)
        shape=row['main'];bpp=BPP.get(shape['format'])
        row['nominal_level0_pair_bytes']=None if bpp is None else 2*bpp*shape['width']*shape['height']
        row['mips_per_observed_final']=row.pop('mips')/len(finals)
        row['copies_per_observed_final']=row.pop('final_copies')/len(finals)
        row['nominal_copy_payload_bytes_per_final']=None if bpp is None else row['copies_per_observed_final']*bpp*shape['width']*shape['height']
        row['disposition']='NO_DELETION_CERTIFICATE'
    targets=list(tables.values())
    result['targets'][shader]={'source':str(path),'native_graph':str(graph),'completed_frames':len(finals),
        'custom_texture_inventory':starts[finals[0]].get('custom_textures',[]),
        'compute_roots':manifest['compute_programs'],'images':targets,
        'copies_per_observed_final':sum(row['copies_per_observed_final'] for row in targets),
        'nominal_copy_payload_bytes_per_final':None if any(row['copies_per_observed_final'] and row['nominal_copy_payload_bytes_per_final'] is None for row in targets) else sum(row['nominal_copy_payload_bytes_per_final'] or 0 for row in targets),
        'unobserved_but_parsed_targets':[r['target'] for r in targets if not r['actual_sampler_users'] and r['parsed_sampler_roots']],
        'wholly_unobserved_targets':[r['target'] for r in targets if not r['actual_sampler_users'] and not r['parsed_sampler_roots']]}
(HERE/'target-liveness-screen.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({shader:{k:value[k] for k in ['copies_per_observed_final','nominal_copy_payload_bytes_per_final','unobserved_but_parsed_targets','wholly_unobserved_targets']} for shader,value in result['targets'].items()}))
