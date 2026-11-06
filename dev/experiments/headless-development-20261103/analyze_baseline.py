"""Descriptive analysis of maintained captures; never launches or filters runs."""
import json,math,statistics,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'scripts'))
import benchmark_bliss_qualification as qualification
HERE=Path(__file__).resolve().parent
runs=[HERE/f'null-p0-{i:02d}' for i in range(1,7)]
cohort=qualification.cohort(runs,mode='null')
(HERE/'baseline-cohort.json').write_text(json.dumps(cohort,indent=2)+'\n')
rows=[]
def percentile(v,p):
 return sorted(v)[max(0,math.ceil(len(v)*p)-1)]
for run in runs:
 summary=json.loads((run/'summary.json').read_text())
 for label,arm in summary['evaluation']['arms'].items():
  raw=json.loads((run/'windows'/label/'result.json').read_text())['manifest']['windows'][0]['samples']
  values=[s['totalFrameNs']/1e6 for s in raw if s['totalFrameNs'] is not None]
  assert len(values)==arm['samples'] and math.isclose(statistics.mean(values),arm['mean_total_frame_ms'],abs_tol=1e-9)
  mem=summary['qualification_memory'][label];delta=mem['delta'];telemetry=summary['host_telemetry'][label]
  row={'block':run.name,'window':label,'samples':len(values),'mean_ms':statistics.mean(values),'median_ms':statistics.median(values),'p95_ms':percentile(values,.95),'p99_ms':percentile(values,.99),'max_ms':max(values),'sd_ms':statistics.stdev(values),'cv_percent':100*statistics.stdev(values)/statistics.mean(values),'frames_over_100ms':sum(v>100 for v in values),'frames_over_200ms':sum(v>200 for v in values),'memory_delta':delta,'available_memory_kib':[mem[t]['memory']['MemAvailable_kib'] for t in ['before','after']],'unevictable_kib':[mem[t]['memory']['Unevictable_kib'] for t in ['before','after']],'battery_milliC':[telemetry[t].get('/sys/class/thermal/thermal_zone0/temp') for t in ['before','after']]}
  row['mean_render_wall_ms']=statistics.mean(v['renderWallNs']/1e6 for v in raw if v.get('renderWallNs') is not None)
  row['mean_render_thread_cpu_ms']=statistics.mean(v['renderThreadCpuNs']/1e6 for v in raw if v.get('renderThreadCpuNs') is not None)
  row['mean_process_cpu_ms']=statistics.mean(v['processCpuNs']/1e6 for v in raw if v.get('processCpuNs') is not None)
  row['gpu_timing_available']=any(v.get('gpuElapsedNs') is not None for v in raw)
  rows.append(row)
means=[statistics.mean(r['mean_ms'] for r in rows if r['block']==run.name) for run in runs]
result={'scope':'Six fresh-process mixed-terrain Bliss/native463 identical-shader null blocks, 2560x1600, 1536m heap, closed lid, KWin virtual M1 GPU. Descriptive frame statistics; inference uses process-block effects, not individual frames. No sky scene or full two-scene calibration.','all_valid_restored':all(x['valid'] for x in cohort['attempts']),'scene_decision':cohort['scenes']['0']['decision'],'null_effect_percent':cohort['scenes']['0']['effect'],'checks':cohort['scenes']['0']['checks'],'process_mean_ms':means,'equal_weight_mean_ms':statistics.mean(means),'process_mean_cv_percent':100*statistics.stdev(means)/statistics.mean(means),'first_to_last_process_drift_percent':100*(means[-1]-means[0])/means[0],'median_within_window_cv_percent':statistics.median(r['cv_percent'] for r in rows),'p95_window_range_ms':[min(r['p95_ms'] for r in rows),max(r['p95_ms'] for r in rows)],'maximum_same_treatment_spread_percent':max(max(b['same_arm_spread_percent'].values()) for b in cohort['scenes']['0']['blocks']),'major_faults_total':sum(r['memory_delta']['client_fault_deltas']['major_faults'] for r in rows),'host_swap_in_bytes':sum(r['memory_delta']['host_swap_bytes']['pswpin'] for r in rows),'host_swap_out_bytes':sum(r['memory_delta']['host_swap_bytes']['pswpout'] for r in rows),'cgroup_high_events':sum(v.get('high',0) for r in rows for v in r['memory_delta']['cgroup_memory_events'].values()),'frames_over_200ms_total':sum(r['frames_over_200ms'] for r in rows),'windows':rows,'cohort_report':'baseline-cohort.json'}
(HERE/'baseline-analysis.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='windows'},indent=2))
