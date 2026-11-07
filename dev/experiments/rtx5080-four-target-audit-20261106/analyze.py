"""Reduce maintained reports; chart descriptive frame jitter separately from run variation."""
import csv
import html
import hashlib
import json
import math
from pathlib import Path
import statistics as stats
import sys

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import benchmark_iteration as iteration

TARGETS=['reimagined','unbound','bsl','makeup']
TITLES={'reimagined':'Complementary Reimagined','unbound':'Complementary Unbound','bsl':'BSL','makeup':'MakeUp Ultra Fast'}
GOALS=[f'G{i:02}' for i in range(1,11)]+['C01','C02']+[f'N{i:02}' for i in range(1,5)]
def spread(values):
    return {'n':len(values),'mean':stats.mean(values),'sd':stats.stdev(values) if len(values)>1 else None,
            'variance':stats.variance(values) if len(values)>1 else None}

jobs=[]
# New continuation definitions supersede the unexecuted historical plans for
# identical output paths (notably their repaired contexts and implementation).
for name in ('rtx5080-scoring-v3-contexts.json','rtx5080-scoring-v2-continuation.json','rtx5080-post-repair-v2.json','rtx5080-repairs-v2-checks.json','rtx5080-observed-v1.json','rtx5080-baselines-v1.json','rtx5080-comparisons-v1.json','rtx5080-port-validation-v2.json','rtx5080-port-validation-v3.json','rtx5080-scoring-v1-block1.json'):
    path=ROOT/'benchmarks/campaigns'/name
    if path.exists():jobs.extend(json.loads(path.read_text()))
seen=set();runs=[];raw_rows=[]
for job in jobs:
    output=Path(job['output'])
    if not output.is_absolute():output=ROOT/output
    if output in seen:continue
    seen.add(output)
    path=output/'summary.json'
    if not path.exists():continue
    summary=json.loads(path.read_text());evaluation=summary.get('evaluation',{})
    row={key:job.get(key) for key in ['name','shader','goal','kind','variant','phase','case']}
    row.update(output=str(output),validity=evaluation.get('validity','RUNNING' if not summary.get('finished') else 'INCOMPLETE'),
               restored=summary.get('configuration_restored',False),error=summary.get('error'),arms={})
    row['measurement_qualified']=row['kind'] in ('test','control','baseline') and summary.get('purpose')=='benchmark'
    row['work_qualification']='SEE_WORK_EVIDENCE'
    if row['kind']=='baseline':row['work_qualification']='ORIGINAL_PACK_MAIN_MOD_ABSENT'
    elif row['kind']=='control':row['work_qualification']=('NATIVE_EQUIVALENT_PRIVATE_G10_CONTROL' if row.get('case')=='g10-native-control' else 'BYTE_IDENTICAL_NATIVE_CONTROL') if row['validity']=='VALID' else 'CONTROL_REQUEST_UNQUALIFIED'
    elif row['goal']=='G01' and any(k.startswith('candidate') for k in summary.get('untimed_work_witnesses',{})):
        row['work_qualification']='COPY_WITNESSES_RETAINED'
    elif row['goal']=='G02' and summary.get('mip_family_assessment'):
        row['work_qualification']='OBSERVED_MIP_OMISSION'
    elif row['goal']=='N01' and any(k.startswith('candidate') for k in summary.get('cpu_work',{})):
        row['work_qualification']='NATIVE_CPU_CONSUMER_PROGRESS'
    elif row.get('case')=='native-rp1-fallback':row['work_qualification']='NO_POM_COMPILED_FALLBACK'
    elif row['goal']=='G09':
        if 'pom_height_cache' in summary.get('selected_features',[]):
            observations=[o.get('actual',{}) for key,o in summary.get('mip_family_observations',{}).items() if key.startswith('candidate')]
            live=any(e.get('scalar_controls',{}).get('shaderlodOptPomHeightCache')==1 for o in observations for e in o.get('events',[]))
            row['work_qualification']='NATIVE_POM_CACHE_CONTROL_OBSERVED' if live else 'CACHE_SOURCE_ONLY_NO_LIVE_UNIFORM'
        elif summary.get('g09_private_work'):
            progress=any(v.get('after',{}).get('active_setups',0)>v.get('before',{}).get('active_setups',0) for k,v in summary['g09_private_work'].items() if k.startswith('candidate'))
            row['work_qualification']='EXACT_WINDOW_POM_PREFIX_SETUP_ADMISSION' if progress else 'PREFIX_SETUP_UNVERIFIED'
    elif row['goal']=='G10' and summary.get('g10_work_phase_coverage',{}).get('complete'):
        row['work_qualification']='BLOOM_DRAW_AND_FINITE_QUERY_OPPORTUNITIES'
    elif row['goal']=='G03':
        alpha=summary.get('early_alpha_route',{})
        row['work_qualification']='LINKED_NATIVE_CUTOUT_SUBMISSIONS' if alpha.get('route') in ('asahi_linked_cutout_submissions','linked_cutout_submissions') else 'QUALIFIED_CUTOUT_PROGRESS' if any(k.startswith('candidate') for k in summary.get('family_early_alpha_work',{})) else 'SOURCE_SETUP_OR_UNVERIFIED'
    if row['goal']=='G03' and any(v.get('before',{}).get('route')=='linked_cutout_submissions'
                                 for key,v in summary.get('family_early_alpha_work',{}).items() if key.startswith('candidate')):
        row['work_qualification']='LINKED_NATIVE_CUTOUT_SUBMISSIONS_UNTIMED'
    if row['goal']=='G06' and row['shader']!='makeup':
        row['ao_exact_window_work_proven']=any(k.startswith('candidate') for k in summary.get('ao_scale_work',{}))
        row['work_qualification']='EXACT_WINDOW_AO_PRODUCER_CONSUMER' if row['ao_exact_window_work_proven'] else 'REQUEST_ONLY_AO_WORK_UNVERIFIED'
    if row['goal']=='G07' and row['shader']!='makeup':
        row['depth_reuse_exact_window_work_proven']=any(k.startswith('candidate') for k in summary.get('depth_reuse_work',{}))
        row['work_qualification']='EXACT_WINDOW_DEPTH_REUSE_PERMISSION' if row['depth_reuse_exact_window_work_proven'] else 'DEPTH_REUSE_WORK_UNVERIFIED'
        if not row['depth_reuse_exact_window_work_proven']:
            permissions=[v for e in summary.get('mip_family_observations',{}).get('candidate',{}).get('actual',{}).get('events',[])
                         for key,v in e.get('scalar_controls',{}).items() if key in ('shaderlodReimaginedAODepthSamplingQualified','shaderlodUnboundAODepthSamplingQualified','shaderlodBslAoDepthLod0Qualified')]
            if permissions and set(permissions)=={0}:row['work_qualification']='UNTIMED_DEPTH_PERMISSION_ZERO'
    if row['goal']=='G05' and row['shader'] in ('makeup','bsl'):
        row['reflection_exact_window_work_proven']=any(k.startswith('candidate') for k in summary.get('reflection_reuse_work',{}))
        row['work_qualification']='EXACT_WINDOW_NEAREST_WATER_PERMISSION' if row['reflection_exact_window_work_proven'] else 'TRANSIENT_REFLECTION_PERMISSION_UNVERIFIED'
    if summary.get('quality_motion'):row['quality_evidence']=summary['quality_motion']
    ready=summary.get('ready',{})
    row['context']={'pack':ready.get('pack'),'dimension':ready.get('dimension'),
                    'native_viewport':[ready.get('width'),ready.get('height')],
                    'render_distance_chunks':ready.get('distance'),'day_time':ready.get('day_time'),
                    'rain_level':ready.get('rain_level'),'main_mod':ready.get('main_mod'),
                    'preset':ready.get('installed',{}).get('preset'),'camera':ready.get('camera'),
                    'saved_pose':ready.get('saved_authored_pose'),'world':ready.get('saved_world'),
                    'scene':summary.get('candidate_contract',{}).get('scene'),
                    'fixture':summary.get('feature_scene'),
                    'native_options':ready.get('effective_options'),
                    'resources':ready.get('selected_resource_packs'),
                    'loaded_stack':ready.get('loaded_stack'),'gpu_renderer':ready.get('gpu_renderer'),
                    'gpu_driver':ready.get('gpu_driver'),
                    'native_graph':summary.get('native_graph_export'),
                    'arm_graphs':summary.get('audit_graph_exports',{})}
    row['gl_context']=summary.get('gl_context',{})
    # Separate actual contexts, stacks, options and scenes before pooling repeats.
    signature={k:row['context'][k] for k in ('native_viewport','dimension','preset','camera','day_time','rain_level','native_options','loaded_stack','gpu_renderer','gpu_driver','main_mod','render_distance_chunks')}
    signature['requested_gl_context']=row['gl_context'].get('requested')
    if row['shader']=='makeup' and row['goal'] in ('G04','G06'):
        # Use the actual bound source, not the planned variant name, to keep
        # the formerly unreachable Windows entry distinct from its repair.
        binding=json.loads((output/'build-binding.json').read_text())
        fragment='src/main/resources/assets/shaderlod/adapters/patches/adapters/makeup-exact/shaders/lib/g04/water_blocks_fragment.glsl'
        normalized={key.replace('\\','/'):value for key,value in binding.items()}
        row['implementation_revision']=normalized.get(fragment,'UNVERIFIED')
        signature['implementation_revision']=row['implementation_revision']
    row['cohort_id']=hashlib.sha256(json.dumps(signature,sort_keys=True).encode()).hexdigest()[:16]
    row['cohort_signature']=signature
    operation=json.loads((output/'operation.json').read_text())
    if operation.get('state') in ('complete','failed-restored') and operation.get('release_verified'):
        row['restored']=operation.get('configuration_restored',False)
    for directory in sorted((output/'windows').glob('*')):
        if not row['measurement_qualified']:continue
        if not directory.is_dir():continue
        label=directory.name
        try:metric=iteration.arm_metrics(directory,label)
        except (OSError,ValueError,KeyError,TypeError) as error:
            row.setdefault('invalid_windows',{})[label]=str(error);continue
        row['arms'][label]=metric
        raw_rows.append({'run':row['name'],'shader':row['shader'],'goal':row['goal'],'kind':row['kind'],
                         'phase':row['phase'],'case':row['case'],'order':summary.get('comparison_order') or ('AAA' if row['kind']=='baseline' else 'ABA'),
                         'run_validity':row['validity'],'scene':row['context']['scene'],'cohort_id':row['cohort_id'],
                         'requested_gl_context':row['gl_context'].get('requested'),'actual_gl_context':row['gl_context'].get('actual_version_string'),
                         'work_qualification':row['work_qualification'],
                         'pose':row['context']['saved_pose'],'dimension':row['context']['dimension'],
                         'preset':row['context']['preset'],'window':label,**metric})
    if row['validity']=='VALID' and row['kind'] in ('test','control'):
        if summary.get('comparison_order'):
            row['comparison_order']=summary['comparison_order'];grouped={}
            for role in ('native','candidate'):
                items=[m for label,m in row['arms'].items() if label.startswith(role)]
                grouped[role]={key:stats.mean(m[key] for m in items) for key in ('mean_total_frame_ms','sd_total_frame_ms','variance_total_frame_ms2','p95_total_frame_ms')}
            row['plot_arms']={'native':row['arms']['native_0'],'candidate':grouped['candidate'],'return':row['arms']['native_1']}
        else:row['plot_arms']=row['arms']
        row['within_process_window_mean_variation']={role:spread([m['mean_total_frame_ms'] for label,m in row['arms'].items()
                                                                  if label.startswith(role) or role=='native' and label=='return'])
                                                    for role in ('native','candidate')}
        a,b,c=[row['plot_arms'][key]['mean_total_frame_ms'] for key in ('native','candidate','return')]
        midpoint=(a+c)/2
        row.update(frame_time_reduction_pct=100*(midpoint-b)/midpoint,
                   control_drift_pct=100*(c-a)/a,candidate_below_both=b<min(a,c),
                   candidate_above_both=b>max(a,c))
        row['screen']='PROMISING_REPEAT' if b<min(a,c) and row['frame_time_reduction_pct']>max(2,abs(row['control_drift_pct'])) else 'REGRESSION' if b>max(a,c) and -row['frame_time_reduction_pct']>max(2,abs(row['control_drift_pct'])) else 'INCONCLUSIVE'
    runs.append(row)

matrix=json.loads((ROOT/'benchmarks/campaigns/rtx5080-v1.json').read_text())
for cell in matrix['cells']:
    matches=[r for r in runs if r['shader']==cell['shader'] and r['goal']==cell['goal']]
    cell['attempt_failures']=[{'name':r['name'],'case':r.get('case'),'phase':r.get('phase'),'error':r['error'],'validity':r['validity']} for r in matches if r['validity'] not in ('VALID','RUNNING')]
    valid=[r for r in matches if r['validity']=='VALID' and r['restored'] and r['kind']=='test' and r['measurement_qualified']]
    cell['runs']=[r['output'] for r in matches];cell['valid_runs']=len(valid)
    if cell['shader']=='makeup' and cell['goal'] in ('G04','G06'):
        current='6d828e22ccb7ab0a7c1268ffef42970ea04351c54325d8989f7e64b6536b68a4'
        cell['superseded_implementation_runs']=[r['output'] for r in valid if r.get('implementation_revision')!=current]
        valid=[r for r in valid if r.get('implementation_revision')==current]
        cell['valid_runs']=len(valid)
        cell['implementation_selection_note']='Current Windows entry only; older timings remain in every-attempt evidence and separate variance cohorts.'
    if valid:
        cases={r.get('case') or 'primary' for r in valid}
        cell['case_effects']={case:{phase:{work:spread([r['frame_time_reduction_pct'] for r in valid
                                                                  if (r.get('case') or 'primary')==case and (r.get('phase') or 'discovery')==phase and r['work_qualification']==work])
                                         for work in {r['work_qualification'] for r in valid if (r.get('case') or 'primary')==case and (r.get('phase') or 'discovery')==phase}}
                                  for phase in {r.get('phase') or 'discovery' for r in valid if (r.get('case') or 'primary')==case}}
                             for case in cases}
        preferred=cell.get('preferred_case','primary')
        primary=[r for r in valid if (r.get('case') or 'primary')==preferred]
        if not primary and cell.get('preferred_case'):
            cell['status']='PREFERRED_CASE_UNMEASURED'
            cell['preferred_case_note']='No valid timing in the declared primary case; other valid cases remain separate in case_effects and every-attempt charts.'
            continue
        if not primary:
            if len(cases)!=1:
                cell['status']='CASE_SELECTION_REQUIRED';continue
            primary=valid
        if any(r.get('ao_exact_window_work_proven') for r in primary):
            primary=[r for r in primary if r.get('ao_exact_window_work_proven')]
            cell['primary_selection_note']='AO primary plot requires exact-window producer/consumer progress; older request-only timing remains in every-attempt evidence.'
        if any(r.get('depth_reuse_exact_window_work_proven') for r in primary):
            primary=[r for r in primary if r.get('depth_reuse_exact_window_work_proven')]
        if any(r.get('reflection_exact_window_work_proven') for r in primary):
            primary=[r for r in primary if r.get('reflection_exact_window_work_proven')]
        cell['plotted_case']=primary[0].get('case') or 'primary'
        cohorts={r['cohort_id'] for r in primary}
        if len(cohorts)>1:
            cell['status']='COHORT_SELECTION_REQUIRED';cell['cohorts']=sorted(cohorts);continue
        confirmed=[r for r in primary if r.get('phase')=='confirmation']
        valid=confirmed or primary
        cell['plotted_phase']='confirmation' if confirmed else valid[0].get('phase','screening');cell['plotted_runs']=len(valid)
        cell['status']=cell.get('case_dispositions',{}).get(cell['plotted_case']) or cell.get('audit_disposition') or 'MEASURED';cell['effects_pct']=spread([r['frame_time_reduction_pct'] for r in valid]);cell['screens']=[r['screen'] for r in valid]
        cell['arms']={}
        for arm in ('native','candidate','return'):
            metrics=[r['plot_arms'][arm] for r in valid]
            cell['arms'][arm]={'window_means_ms':spread([r['mean_total_frame_ms'] for r in metrics]),
                               'mean_within_window_sd_ms':stats.mean(r['sd_total_frame_ms'] for r in metrics),
                               'mean_within_window_variance_ms2':stats.mean(r['variance_total_frame_ms2'] for r in metrics),
                               'mean_p95_ms':stats.mean(r['p95_total_frame_ms'] for r in metrics)}
    elif matches:
        cell['status']=cell.get('audit_disposition') or ('CURRENT_IMPLEMENTATION_UNMEASURED' if cell.get('superseded_implementation_runs') else 'RUNNING' if any(r['validity']=='RUNNING' for r in matches) else 'INVALID_OR_REFUSED')
        cell['errors']=[r['error'] for r in matches]

baselines={}
for shader in TARGETS:
    selected=[r for r in runs if r['shader']==shader and r['kind']=='baseline' and r['validity']=='VALID' and r['restored']]
    selected.sort(key=lambda r:r['name'])  # Stable r1/r2 labels, including the manual first Reimagined baseline.
    # Primary cards are native-context minimal-stack only. Other native-only
    # baselines remain visible in runs and separate cohort summaries below.
    selected=[r for r in selected if r['gl_context'].get('requested')=='native'
              and not any(k in r['context']['loaded_stack'] for k in ('modernfix','ferritecore','immediatelyfast'))]
    if len({r['cohort_id'] for r in selected})>1:
        raise ValueError('Primary baseline cohorts differ; explicit selection required: '+shader)
    baselines[shader]={'runs':selected}
    if selected:
        baselines[shader]['process_means_ms']=spread([stats.mean(m['mean_total_frame_ms'] for m in r['arms'].values()) for r in selected])

result={'scope':'Partial RTX5080 native Windows3840x2160 total-frame intervals in ms. Baseline cards omit the main mod; candidate A controls have ShaderLOD installed and MasterOff, except C02 uses MasterOn with VRS Off and empty public requests. Cross-process baseline/card differences are not a causal mod-overhead estimate. Functional failures stay separate from scored comparisons. A measured request is not automatically proven active or beneficial. Cells without valid measurements have no invented bars.',
        'statistics':'Frame sample SD/variance describes jitter within a window. Process/window-mean SD/variance describes repeated-window or repeated-process variation. Equal weighting of runs/windows; no independent-frame inference or confidence intervals.',
        'effect_definition':'Per process:100*(A-B)/A, A=(first native window mean + last native window mean)/2; B is the candidate window mean, or mean of both candidate-window means in ABBA/BAAB. Repeated effects are equally weighted process effects; SD is in percentage points.',
        'control_drift_definition':'Analysis drift is100*(last native mean-first native mean)/first native mean. Native windows are inner A windows in BAAB; no chronological native/candidate/native claim for balanced orders.',
        'variance_definition':'Within-window variance uses N-1 frame samples in ms squared; SD is its square root. Plot bars average within-window SD when combining windows/processes. Main chart between-process variance separates A0/B/A1; CSV native role averages both native windows within each process. n1 between-process variance is unavailable.',
        'runs':runs,'baselines':baselines,'cells':matrix['cells']}
result['control_cohorts']={}
for shader in TARGETS:
    controls=[r for r in runs if r['shader']==shader and r['kind']=='control' and r['validity']=='VALID' and r['restored']]
    result['control_cohorts'][shader]={case:{'effects_pct':spread([r['frame_time_reduction_pct'] for r in controls if r['case']==case]),
                                           'runs':[r['output'] for r in controls if r['case']==case]}
                                     for case in {r['case'] for r in controls}}
result['baseline_cohorts']={}
for run in runs:
    if run['kind']!='baseline' or run['validity']!='VALID' or not run['restored']:continue
    key=run['shader']+'/'+run['cohort_id']
    cohort=result['baseline_cohorts'].setdefault(key,{'signature':run['cohort_signature'],'runs':[]})
    cohort['runs'].append(run['name'])
for key,cohort in result['baseline_cohorts'].items():
    cohort['process_means_ms']=spread([stats.mean(m['mean_total_frame_ms'] for m in r['arms'].values())
                                      for r in runs if r['name'] in cohort['runs']])
progress={'scope':'RTX5080 native Windows baseline progress. Primary profiles use native3840x2160/AA, minimal stack and native context, main mod absent. Frame variance, window-mean variance and process variance are separate descriptive quantities; n1 process variance is unavailable.', 'primary':{shader:{'process_means_ms':data.get('process_means_ms'),'runs':[{'name':r['name'],'arms':r['arms']} for r in data['runs']]} for shader,data in baselines.items()},'separate_cohorts':result['baseline_cohorts']}
(HERE/'baseline-progress.json').write_text(json.dumps(progress,indent=2)+'\n')
(HERE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
if raw_rows:
    with (HERE/'frame-variance.csv').open('w') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(raw_rows[0]));writer.writeheader();writer.writerows(raw_rows)

between_rows=[]
group_keys=sorted({(r['shader'],r.get('goal') or '',r['kind'],r.get('case') or 'primary',r.get('phase') or 'discovery',r['work_qualification'],r['cohort_id'])
                   for r in runs if r['validity']=='VALID' and r['restored']})
for shader,goal,kind,case,phase,work,cohort_id in group_keys:
    selected=[r for r in runs if r['validity']=='VALID' and r['restored'] and
              (r['shader'],r.get('goal') or '',r['kind'],r.get('case') or 'primary',r.get('phase') or 'discovery',r['work_qualification'],r['cohort_id'])==(shader,goal,kind,case,phase,work,cohort_id)]
    for role in ('native','candidate') if kind!='baseline' else ('native',):
        process_means=[];within_variances=[]
        for run in selected:
            metrics=[m for label,m in run['arms'].items() if kind=='baseline' or label.startswith(role) or role=='native' and label=='return']
            if metrics:
                process_means.append(stats.mean(m['mean_total_frame_ms'] for m in metrics))
                within_variances.append(stats.mean(m['variance_total_frame_ms2'] for m in metrics))
        if process_means:
            variability=spread(process_means)
            between_rows.append({'shader':shader,'goal':goal,'kind':kind,'case':case,'phase':phase,'work_qualification':work,'cohort_id':cohort_id,'role':role,
                                 'processes':variability['n'],'mean_process_frametime_ms':variability['mean'],
                                 'between_process_sd_ms':variability['sd'],'between_process_variance_ms2':variability['variance'],
                                 'mean_within_window_variance_ms2':stats.mean(within_variances)})
if between_rows:
    with (HERE/'between-run-variance.csv').open('w') as stream:
        writer=csv.DictWriter(stream,fieldnames=list(between_rows[0]));writer.writeheader();writer.writerows(between_rows)
if '--data-only' in sys.argv:
    print(json.dumps({'runs':len(runs),'valid':sum(r['validity']=='VALID' for r in runs),'measured_cells':sum(bool(c.get('arms')) for c in matrix['cells'])}));sys.exit(0)

import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
plt.rcParams.update({'font.size':13,'axes.spines.top':False,'axes.spines.right':False})
fig,axes=plt.subplots(2,2,figsize=(14,14),layout='constrained')
for ax,shader in zip(axes.flat,TARGETS):
    for i,goal in enumerate(GOALS):
        cell=next(c for c in matrix['cells'] if c['shader']==shader and c['goal']==goal)
        if 'arms' not in cell:
            label='shared G01' if cell['status']=='CONSOLIDATED_WITH_G01' else cell['status'].lower().replace('_',' ')
            ax.text(.02,i,label,transform=ax.get_yaxis_transform(),va='center',color='#666666',fontsize=10)
            continue
        for arm,offset,color in [('native',-.2,'#888888'),('candidate',0,'#087e8b'),('return',.2,'#bd812c')]:
            metric=cell['arms'][arm]
            name={'native':'native A0','candidate':'candidate','return':'native A1'}[arm]
            ax.errorbar(metric['window_means_ms']['mean'],i+offset,xerr=metric['mean_within_window_sd_ms'],fmt='o',markersize=4,color=color,capsize=2,label=name if i==next(j for j,g in enumerate(GOALS) if next(c for c in matrix['cells'] if c['shader']==shader and c['goal']==g).get('arms')) else None)
    ax.set(yticks=range(16),yticklabels=GOALS,ylim=(15.7,-.7),xlabel='Total frame time (ms); bars = mean frame sample SD',title=TITLES[shader])
    ax.grid(axis='x',alpha=.2);ax.set_xlim(left=0)
    if ax.get_legend_handles_labels()[0]:ax.legend(loc='lower right',fontsize=11)
fig.suptitle('RTX 5080 candidate audit (in progress) — matched native controls and candidates\nDifferent rows may use different poses/settings. Independent panel scales. Bars are not confidence intervals.',fontsize=16)
fig.savefig(HERE/'candidate-frametimes.png',dpi=160);fig.savefig(HERE/'candidate-frametimes.svg');plt.close(fig)

fig,axes=plt.subplots(2,2,figsize=(12,7),layout='constrained')
for ax,shader in zip(axes.flat,TARGETS):
    labels=[];index=0
    for run in baselines[shader]['runs']:
        for arm,metric in sorted(run['arms'].items()):
            ax.errorbar(index,metric['mean_total_frame_ms'],yerr=metric['sd_total_frame_ms'],fmt='o',color='#087e8b',capsize=3)
            labels.append(run['name'].split('-')[-1]+'/'+arm.rsplit('_',1)[-1]);index+=1
    ax.set(title=TITLES[shader],ylabel='Frame time (ms), mean ± frame SD',xticks=range(index),xticklabels=labels)
    ax.grid(axis='y',alpha=.2)
fig.suptitle('Original native baselines — main mod absent; 120s conditioning, three 20s windows per process\nWithin-window jitter is shown here; between-process and window-mean variance is in results.json.',fontsize=11)
fig.savefig(HERE/'baseline-frametimes.png',dpi=160);fig.savefig(HERE/'baseline-frametimes.svg');plt.close(fig)

for shader in TARGETS:
    selected=[r for r in runs if r['shader']==shader and r['kind']=='test' and r['validity']=='VALID' and r['restored'] and r.get('plot_arms')]
    selected.sort(key=lambda r:(GOALS.index(r['goal']),r.get('case') or 'primary',r.get('phase') or 'discovery',r['name']))
    if not selected:continue
    fig,ax=plt.subplots(figsize=(17,max(5,len(selected)*.6+2)),layout='constrained')
    labels=[]
    for i,run in enumerate(selected):
        labels.append(run['goal']+' / '+(run.get('case') or 'primary')+' / '+(run.get('phase') or 'discovery')+' / '+run.get('comparison_order','ABA')+' / '+run['name'].rsplit('-',1)[-1])
        for arm,offset,color in [('native',-.18,'#888888'),('candidate',0,'#087e8b'),('return',.18,'#bd812c')]:
            m=run['plot_arms'][arm]
            ax.errorbar(m['mean_total_frame_ms'],i+offset,xerr=m['sd_total_frame_ms'],fmt='o',color=color,capsize=2,markersize=4,label={'native':'native A0','candidate':'candidate','return':'native A1'}[arm] if i==0 else None)
    ax.set(yticks=range(len(selected)),yticklabels=labels,ylim=(len(selected)-.4,-.6),xlabel='Total frame time (ms); bars = within-window sample SD',title=TITLES[shader]+' — every valid optimization timing\nCases, phases and orders stay separate. Older request-only AO timing is retained here; work-qualified AO is selected in the main plot.')
    ax.tick_params(axis='y',labelsize=10);ax.set_xlim(left=0);ax.grid(axis='x',alpha=.2);ax.legend(loc='lower right')
    fig.savefig(HERE/('attempt-frametimes-'+shader+'.png'),dpi=150);fig.savefig(HERE/('attempt-frametimes-'+shader+'.svg'));plt.close(fig)

svgs=[]
for name in ['baseline-frametimes.svg','candidate-frametimes.svg']:
    text=(HERE/name).read_text();svgs.append('<svg'+text.split('<svg',1)[1])
doc='<!doctype html><html><head><meta charset="utf-8"><style>svg{width:100%;height:auto}p{font-family:var(--font-sans);color:var(--foreground)}</style></head><body><p>Descriptive frame-time measurements. Missing or refused cases have no timing bar. See the linked JSON and CSV for all attempts, exact variances, native drift and between-run variation.</p>'+''.join(svgs)+'</body></html>'
(HERE/'charts.html').write_text(doc)
print(json.dumps({'runs':len(runs),'valid':sum(r['validity']=='VALID' for r in runs),'measured_cells':sum(bool(c.get('arms')) for c in matrix['cells'])}))
