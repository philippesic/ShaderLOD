"""Read-only CPU arm/source safety and secondary render-scope clock evidence."""
import hashlib
import json
from pathlib import Path
import re
import statistics as stats

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
rows=[];seen=set()
for ledger in HERE.glob('queue-*.json'):
    for job in json.loads(ledger.read_text()).get('runs',[]):
        if job.get('goal')!='N01':continue
        out=Path(job['output']);out=out if out.is_absolute() else ROOT/out
        if out in seen:continue
        seen.add(out)
        if not (out/'summary.json').exists():continue
        s=json.loads((out/'summary.json').read_text())
        if not s.get('finished'):continue
        row={k:job.get(k) for k in ('name','shader','phase','case')}
        row.update(validity=s.get('evaluation',{}).get('validity'),restored=s.get('configuration_restored'),
                   reload_deltas=s.get('cpu_reload_deltas'),source_arms={},secondary_clocks={})
        source_maps=[]
        for label,directory in s.get('audit_graph_exports',{}).items():
            directory=Path(directory);manifest=json.loads((directory/'manifest.json').read_text());sources={};checks=[]
            for program in manifest['linked_programs']:
                if not program['name'].startswith('sodium_'):continue
                for stage in program['stages']:
                    text=(directory/stage['file']).read_text();key=program['name']+':'+str(stage['type'])
                    sources[key]=hashlib.sha256(text.encode()).hexdigest()
                    if stage['type']==35633:
                        # Exclude comments from the actual attached vertex text.
                        body=re.sub(r'/\*.*?\*/|//[^\n]*','',text,flags=re.S)
                        checks.append({'program':program['name'],'uses_gl_DrawID':bool(re.search(r'\bgl_DrawID(?:ARB)?\b',body)),
                                       'uses_gl_BaseInstance':bool(re.search(r'\bgl_BaseInstance(?:ARB)?\b',body)),
                                       'chunk_id_from_vertex':bool(re.search(r'_draw_id\s*=\s*a_LightAndData\s*\[\s*3\s*\]',body))})
            row['source_arms'][label]={'attached_stage_sha256':sources,'vertex_checks':checks};source_maps.append(sources)
        row['attached_terrain_stages_identical_across_arms']=len(source_maps)>=3 and len(source_maps[0])==6 and all(m==source_maps[0] for m in source_maps)
        for directory in (out/'windows').glob('*'):
            manifest=json.loads((directory/'result.json').read_text())['manifest']['windows'][0]
            samples=[v for v in manifest['samples'] if type(v.get('totalFrameNs')) in (int,float) and v['totalFrameNs']>0]
            clocks={}
            for key in ('renderWallNs','renderThreadCpuNs','processCpuNs'):
                values=[v[key]/1e6 for v in samples if type(v.get(key)) in (int,float)]
                clocks[key]={'n':len(values),'mean_ms':stats.mean(values) if values else None,
                             'sample_sd_ms':stats.stdev(values) if len(values)>1 else None,
                             'sample_variance_ms2':stats.variance(values) if len(values)>1 else None}
            row['secondary_clocks'][directory.name]=clocks
        row['work']=s.get('cpu_work');rows.append(row)
result={'scope':'N01 native command-list CPU bundle. Identical attached terrain source and per-vertex chunk IDs support zero-command compaction safety; shadow/API and scene ownership remain separately guarded by the runtime. Command entries removed are not GL draws or visible geometry. Program identity is stable within each window, not across arm transitions. Secondary clocks cover the renderer head/return scope, not total-frame CPU work or GPU time. No GPU queries were issued.', 'runs':rows}
(HERE/'cpu-evidence.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'runs':len(rows),'terrain_source_equal':[r['name'] for r in rows if r['attached_terrain_stages_identical_across_arms']]}))
