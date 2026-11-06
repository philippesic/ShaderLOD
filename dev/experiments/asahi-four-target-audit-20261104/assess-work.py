"""Small read-only index of actual work/permission receipts, never a benefit gate.

The source reports remain authoritative; request flags and uniform permission do
not count avoided fragments, shader queries, GL draws or GPU completions.
"""
import collections
import hashlib
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]

def small(value):
    if isinstance(value,dict):return {k:small(v) for k,v in value.items() if not k.endswith('_source') and k not in ('vertex_source','fragment_source')}
    if isinstance(value,list):return [small(v) for v in value]
    if isinstance(value,str):
        if value.startswith('{'):
            try:return small(json.loads(value))
            except json.JSONDecodeError:pass
        if len(value)>1000:return {'long_string_sha256':hashlib.sha256(value.encode()).hexdigest(),'bytes':len(value.encode()),'see_full_summary':True}
    return value

seen=set();runs=[]
for ledger in HERE.glob('queue-*.json'):
    for job in json.loads(ledger.read_text()).get('runs',[]):
        if job['kind'] not in ('test','control','quality','check'):continue
        out=Path(job['output']);out=out if out.is_absolute() else ROOT/out
        if out in seen:continue
        seen.add(out);path=out/'summary.json'
        if not path.exists():continue
        s=json.loads(path.read_text())
        if not s.get('finished'):continue
        row={k:job.get(k) for k in ('name','shader','goal','kind','phase','case')}
        row.update(summary=str(path),validity=s.get('evaluation',{}).get('validity'),restored=s.get('configuration_restored'),error=s.get('error'))
        row['specialized_receipts']={k:small(s[k]) for k in ('untimed_work_witnesses','ao_scale_work','depth_reuse_work','reflection_reuse_work','family_early_alpha_work','early_alpha_route','early_alpha_activation','early_alpha_progress','private_work','g09_private_work','cpu_work','mip_family_assessment','g08_native_bound_diagnostic','g10_work','g10_work_images','g10_work_phase_coverage','c01_native_visibility','feature_scene','quality_motion') if k in s}
        row['graph_exports']=s.get('audit_graph_exports',{})
        row['request_and_source_states']={label:{'pack':state.get('pack'),'effective_options':state.get('effective_options'),
            'family':state.get('installed',{}).get('family'),'preset':state.get('installed',{}).get('preset'),
            'master':state.get('installed',{}).get('master'),'requested_flags':{f:state.get('installed',{}).get('flags',{}).get(f) for f in s.get('candidate_contract',{}).get('public_features',[])},
            'selected_compile_evidence':{k:state['installed'][k] for k in ('g04_selected_source_fastpath','g06_selected_source_skip','g06_selected_source_assignment','dead_frame_copy_setup','dead_mip_setup','family_early_alpha') if k in state.get('installed',{})}}
            for label,state in s.get('public_states',{}).items()}
        row['returned_work_observations']={}
        for key in ('mip_family_observations','native_control_observations'):
            for label,packet in s.get(key,{}).items():
                a=packet['actual'];events=a['events'];groups={}
                for e in events:
                    if e['kind'] not in ('completed_quad','terrain_setup'):continue
                    name=e['kind']+':'+e.get('name','unknown');g=groups.setdefault(name,{'observations':0,'scalar_controls':{}});g['observations']+=1
                    for name,value in e.get('scalar_controls',{}).items():g['scalar_controls'].setdefault(name,set()).add(value)
                for group in groups.values():group['scalar_controls']={name:sorted(values) for name,values in group['scalar_controls'].items()}
                row['returned_work_observations'][label]={'completed_frames':a.get('completed_frames'),'event_counts':dict(collections.Counter(e['kind'] for e in events)),
                    'draw_boundaries':groups,'source_names':[v['name'] for v in a['sources']],
                    'scope':'Completed quad calls or terrain setup boundaries in two observed frames. Live scalar controls are permission/request evidence, not executed guarded branches or saved-query counters.'}
        runs.append(row)
result={'scope':'Receipt index only. Timing validity, requested flags, source presence, draw permission, actual work omission, visual quality and performance benefit are separate claims. No automatic benefit verdict from this index.','runs':runs}
(HERE/'work-evidence.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'indexed_operations':len(runs)}))
