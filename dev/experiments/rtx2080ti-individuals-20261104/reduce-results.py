"""Report completed maintained comparisons; never infer performance from rejected arms."""
import json
from pathlib import Path
root=Path(__file__).resolve().parent
names={'G01':'Framebuffer copies','G02':'Unused mip/pass work','G03':'Early alpha rejection','G04':'Translucent fast path','G05':'Fixed16 reflection traversal','G06':'AO attenuation','G07':'AO depth reuse','G08':'Cloud bounds','G09':'POM height prefix','G10':'Exact bloom taps','C01':'Occlusion bounds','C02':'2×2 bloom shading (quality tradeoff)','N01':'CPU cache/compaction bundle','N02':'Bliss unused RT12','N03':'Bliss fog copy','N04':'Bliss sky'}
rows=[]
for goal,name in names.items():
    run=goal.lower()+('-r2' if goal in ('G01','G02') else '-r1')
    p=root/run/'summary.json'
    if not p.exists():raise ValueError('Missing result: '+str(p))
    s=json.loads(p.read_text());e=s.get('evaluation',{})
    valid=e.get('validity')=='VALID' and s.get('execution_completed') and s.get('configuration_restored') and s.get('clients_remaining')==0
    row=dict(goal=goal,name=name,run=run,valid=bool(valid),error=s.get('error'),restored=s.get('configuration_restored'),speedup_percent=None)
    if valid:
        a=e['arms'];native=[a[x]['mean_total_frame_ms'] for x in ('native','return')];candidate=a['candidate']['mean_total_frame_ms'];gain=100*((sum(native)/2)/candidate-1)
        assert abs(gain-100*(e['speedup_ratio']-1))<1e-10
        row.update(speedup_percent=gain,native_ms=native[0],candidate_ms=candidate,return_ms=native[1],speedup_against_controls_percent=[100*(x/candidate-1) for x in native],drift_comparison=e['drift_comparison'],native_return_drift_percent=e['native_return_drift_percent'])
    rows.append(row)
result={'machine':'RTX 2080 Ti / Windows / NVIDIA 610.88','scope':'One individual representative per goal at 2560×1600; one native/candidate/return bracket, 20 seconds per arm, 120 seconds conditioning. Descriptive results, not statistical qualification. Different goals use different packs/scenes.','definition':'100 × (mean of native control frame times / candidate mean frame time − 1); positive = faster.','quality_tradeoff':'C02 only: 2×2 bloom shading, not image-equivalent.','rows':rows}
(root/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps([{'goal':r['goal'],'speedup_percent':r['speedup_percent'],'error':(r['error'] or '')[:130]} for r in rows],indent=2))
