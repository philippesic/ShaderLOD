"""Offline diagnostic cost maps. No launcher, shader edits or implicit zero filling."""
import argparse
import csv
import hashlib
import html
import json
import math
from pathlib import Path
import statistics

REVISION = 'cost-map-v1'
SOURCE_CATALOG=json.loads((Path(__file__).resolve().parents[1]/'docs/cost-map-catalog.json').read_text())
DH_ROWS = ('terrain', 'main', 'deferred', 'shadow', 'list', 'generation', 'conversion',
           'propagation', 'loading', 'upload', 'transfers', 'shared-consumers', 'residency')
SEEDS = {
 'reimagined-r5.9.3': {'deferred1':['AO','atmosphere','materials'], 'composite':['clouds','reflections','materials'], 'composite1':['volumetric-light','shadow-consumer','water-fog','refraction','reflection-blur'], 'composite4':['bloom-tiles'], 'composite5':['bloom-combine'], 'composite6':['TAA'], 'composite7':['conditional-FXAA']},
 'unbound-r5.9.3': {'deferred1':['AO','atmosphere','materials'], 'composite':['clouds','reflections','materials'], 'composite1':['volumetric-light','shadow-consumer','water-fog','refraction','reflection-blur'], 'composite4':['bloom-tiles'], 'composite5':['bloom-combine'], 'composite6':['TAA'], 'composite7':['conditional-FXAA']},
 'bsl-10.1.8': {'deferred':['AO-producer'], 'deferred1':['AO-consumer','atmosphere','clouds'], 'composite':['light-shafts','water-fog'], 'composite4':['bloom-tiles'], 'composite5':['bloom','post'], 'composite6':['FXAA'], 'composite7':['conditional-TAA']},
 'makeup-9.5g': {'deferred':['AO','volumetric-clouds'], 'composite':['volumetric-light','bloom-source'], 'composite1':['bloom','blur'], 'composite2':['TAA','motion-blur'], 'final':['tone-mapping','optional-final-effects']}}


def catalog(scope, manifest):
    # A family name alone never authorizes activation of source seeds.
    verified = manifest.get('verified_graph_programs', {})
    program = scope.get('program', '')
    tags = SEEDS.get(manifest.get('pack_revision'), {}).get(program, [])
    evidence = verified.get(program)
    pinned=next((p for p in SOURCE_CATALOG['packs'] if p['pack_revision']==manifest.get('pack_revision')),None)
    source=pinned['source_members'].get(evidence.get('source_member'),{}) if pinned and evidence else {}
    matched = bool(tags and evidence and pinned and manifest.get('archive_sha256')==pinned['archive_sha256']
                   and manifest.get('dimension')=='world0' and evidence.get('source_hash')==source.get('sha256') and source
                   and evidence.get('effective_options_hash') and evidence.get('effective_options_hash')==manifest.get('options_hash')
                   and evidence.get('graph_generation') == scope.get('graph_generation')
                   and evidence.get('dh_state') == manifest.get('dh_state'))
    return {'features': tags if matched else [], 'mapping': 'mixed' if matched else 'unmapped',
            'evidence': evidence if matched else 'source seed not activated: exact source/options/graph/DH evidence required',
            'dh_consumer': bool(matched and evidence.get('dh_consumer'))}


def coverage(manifest):
    state = manifest.get('dh_state', 'UNKNOWN')
    status = {'ABSENT':'NOT_APPLICABLE', 'INSTALLED_RENDERING_DISABLED':'DISABLED_BY_VERIFIED_CONFIGURATION',
              'FAILED':'INVALID','UNSUPPORTED':'UNSUPPORTED'}.get(state, 'UNKNOWN')
    rows=[]
    for name in DH_ROWS:
        worker = name in ('generation','conversion','propagation','loading')
        rows.append({'concern': 'DH/'+name, 'status': 'UNKNOWN' if worker and state=='INSTALLED_RENDERING_DISABLED' else status,
                     'duration_ns':None, 'physical':'uncovered', 'semantic':'unverified',
                     'workload':manifest.get('workload','unobserved'),
                     'reason': 'DH absent' if state=='ABSENT' else 'requires observed stage/world-shadow/opacity join; wrappers and consumers are nonadditive'})
    for name,count in manifest.get('unregistered_hook_entries',{}).items():
        rows.append({'concern':name,'status':'UNKNOWN','duration_ns':None,'physical':'uncovered','semantic':'unmapped',
                     'hook_entries':count,'reason':'observed hook absent from predeclared inventory; entries are not draw counts'})
    if manifest.get('unregistered_hook_overflow'):
        rows.append({'concern':'unregistered-hook-overflow','status':'DROPPED','duration_ns':None,'reason':'bounded unknown-name inventory full','entries':manifest['unregistered_hook_overflow']})
    return rows


def distribution(values):
    if not values:return {'count':0, 'mean_ns':None,'median_ns':None,'p95_ns':None,'sd_ns':None,'variance_ns2':None}
    ordered=sorted(values)
    return {'count':len(values),'mean_ns':statistics.mean(values),'median_ns':statistics.median(values),
            'p95_ns':ordered[math.ceil(.95*len(values))-1], 'sd_ns':statistics.pstdev(values), 'variance_ns2':statistics.pvariance(values)}


def summarize(data):
    if data.get('schema_version') != 1:raise ValueError('unsupported capture schema')
    scopes={s['id']:s for s in data['scopes']}
    # Pending/terminal GPU transitions and CPU observations join by event and dimension.
    events={}
    for entry in data.get('events',[]):
        inv=entry['invocation']; key=inv['eventId']
        if inv['scopeId'] not in scopes:raise ValueError('unregistered scope')
        previous=events.setdefault(key, {'invocation':inv})
        if previous['invocation']!=inv:raise ValueError('event identity changed')
        for dimension in ('cpu','gpu'):
            payload=entry.get(dimension)
            if payload:
                if payload.get('invocation',inv)!=inv:raise ValueError('payload identity mismatch')
                if dimension in previous and previous[dimension].get('availability',{}).get('status')!='PENDING':
                    raise ValueError('duplicate terminal observation')
                previous[dimension]=payload
    rows=[];cpu_trace=[];gpu_trace=[];frame_groups={}
    for scope_id,scope in scopes.items():
        mapping=catalog(scope,data['manifest'])
        for metric in ('cpu_wall','cpu_thread','gpu_elapsed'):
            values=[];by_frame={};missing={}
            for e in events.values():
                inv=e['invocation']
                if inv['scopeId']!=scope_id:continue
                payload=e.get('gpu' if metric=='gpu_elapsed' else 'cpu',{})
                outcome=payload.get('availability' if metric=='gpu_elapsed' else 'sampling',{'status':'UNKNOWN'})
                status=outcome['status']
                if metric=='gpu_elapsed' and status!='MEASURED' and payload.get('elapsedNs') is not None:raise ValueError('missing GPU has a duration')
                value=None
                if status=='MEASURED':
                    if metric=='cpu_wall':value=payload['wallEndNs']-payload['wallStartNs']
                    elif metric=='cpu_thread':value=payload.get('threadCpuNs')
                    else:value=payload['elapsedNs']
                    if value is not None and (not isinstance(value,int) or value<0):raise ValueError('invalid integer duration')
                if value is None:
                    missing_key=status if status!='MEASURED' else 'UNKNOWN'
                    missing[missing_key]=missing.get(missing_key,0)+1
                    continue
                values.append(value)
                f=inv['frame'];fk=(f['epoch'],f['windowId'],f['frameId']);by_frame[fk]=by_frame.get(fk,0)+value
                frame_groups.setdefault(fk,set()).add(scope_id)
            rows.append({'scope_id':scope_id,'name':scope['name'],'metric':metric,**mapping,**distribution(values),
                         'per_observed_frame':distribution(list(by_frame.values())), 'missingness':missing,
                         'denominator':'observed invocations; frame sum conditional on observation; no sparse zero fill',
                         'accounting':'inclusive context; do not sum parents with children', 'uncertainty':'descriptive; no independent-repeat or overhead qualification'})
    for e in events.values():
        inv=e['invocation'];name=scopes[inv['scopeId']]['name']
        cpu=e.get('cpu',{})
        if cpu.get('sampling',{}).get('status')=='MEASURED':
            cpu_trace.append({'ph':'X','name':name,'pid':1,'tid':inv['threadId'],'ts':cpu['wallStartNs']/1000,
                              'dur':(cpu['wallEndNs']-cpu['wallStartNs'])/1000,'args':{'event_id':inv['eventId'],'parent_id':inv.get('parentEventId')}})
        gpu=e.get('gpu',{})
        if gpu.get('availability',{}).get('status')=='MEASURED' and gpu['backend']=='TIMESTAMP':
            gpu_trace.append({'ph':'X','name':name,'pid':2,'tid':gpu['clock']['domain']+' generation '+str(gpu['clock']['generation']),'ts':gpu['beginValue']/1000,
                              'dur':gpu['elapsedNs']/1000,'args':{'event_id':inv['eventId']}})
    frame_intervals=[];window_intervals={}
    for f in data.get('frames',[]):
        start=f.get('startNs',f.get('start_ns'));end=f.get('nextStartNs',f.get('next_start_ns'))
        if start is None or end is None or f.get('warmup') or f.get('transition') or f.get('partialStart') or f.get('partialEnd'):continue
        if end<start:raise ValueError('frame clock reset')
        frame_intervals.append(end-start)
        identity=f.get('id',{});window_intervals.setdefault((identity.get('epoch'),identity.get('windowId')),[]).append(end-start)
    omitted_detail={'cpu':max(0,len(cpu_trace)-4096),'gpu':max(0,len(gpu_trace)-4096),'hierarchy':max(0,len(events)-4096)}
    cpu_trace=cpu_trace[:4096];gpu_trace=gpu_trace[:4096]
    gpu_clocks={}
    for event in gpu_trace:gpu_clocks.setdefault(event['tid'],[]).append(event)
    physical_joins=[]
    for scope_id,scope in scopes.items():
        name=scope['name']
        if not name.startswith(('dh/','dh-total/','dh-render-list/','terrain/','terrain-setup/')):continue
        observed=[e['invocation'] for e in events.values() if e['invocation']['scopeId']==scope_id]
        contexts=sorted({(i['dhStage'],i['worldShadow'],i['opacity']) for i in observed})
        physical_joins.append({'name':name,'hook_invocations':len(observed),'contexts':[{'dh_stage':x,'world_shadow':y,'opacity':z} for x,y,z in contexts],
                               'meaning':'render-list CPU wrapper' if name.startswith('dh-render-list/') else 'main/deferred inclusive wrapper' if name.startswith('dh-total/') else 'terrain wrapper; not a draw or visibility count',
                               'semantic':'physical hook only; shader features remain separately evidence gated'})
    return {'schema_version':1,'catalog_revision':REVISION,'manifest':data['manifest'],'costs':rows,'physical_joins':physical_joins,
            'coverage':coverage(data['manifest'])+data.get('coverage',[]),'health':data.get('health',{}),
            'hierarchy':[e['invocation'] for e in list(events.values())[:4096]],'omitted_detail':omitted_detail,
            'cpu_trace':cpu_trace,'gpu_trace':gpu_trace if len(gpu_clocks)<=1 else [],'gpu_clock_traces':gpu_clocks,
            'frame_pacing':distribution(frame_intervals),'window_points':[{'epoch_window':list(k),**distribution(v)} for k,v in window_intervals.items()],
            'investigation_queue':[{'scope_id':r['scope_id'],'name':r['name'],'metric':r['metric'],'mean_ns':r['mean_ns'],'count':r['count'],'next_measurement':'verify mixed graph and repeat windows' if r['mapping']=='mixed' else 'verify physical/semantic coverage and independent repeats','status':'unresolved; no optimization recommendation'} for r in sorted(rows,key=lambda x:x['mean_ns'] or -1,reverse=True) if r['count']],
            'limits':'GPU clock uncorrelated; elapsed-only has no positions. Mixed tags are nonadditive. Frame sums are conditional. Calibration unknown.'}


def write_csv(path, rows):
    keys=list(dict.fromkeys(k for row in rows for k in row))
    with path.open('w',newline='') as out:
        writer=csv.DictWriter(out,fieldnames=keys);writer.writeheader()
        writer.writerows({k:json.dumps(v) if isinstance(v,(dict,list)) else v for k,v in r.items()} for r in rows)


def export(data, output):
    output=Path(output)
    output.mkdir(parents=True,exist_ok=False)
    result=summarize(data)
    (output/'capture.json').write_text(json.dumps(data,indent=2))
    (output/'report.json').write_text(json.dumps(result,indent=2))
    for dimension in ('cpu','gpu'):
        write_csv(output/(dimension+'.csv'),[{'event_id':e['invocation']['eventId'], 'invocation':e['invocation'], **e[dimension]} for e in data.get('events',[]) if e.get(dimension)])
    for name,rows in [('costs',result['costs']),('coverage',result['coverage']),('frames',data.get('frames',[])),('scopes',data['scopes']),('physical-joins',result['physical_joins'])]:write_csv(output/(name+'.csv'),rows)
    for lane in ('cpu','gpu'):
        (output/(lane+'-trace.json')).write_text(json.dumps({'traceEvents':result[lane+'_trace'],'clock': 'CPU monotonic' if lane=='cpu' else 'GPU ONLY: uncorrelated; elapsed records excluded'}))
    for index,(clock,events) in enumerate(result['gpu_clock_traces'].items()):
        (output/('gpu-clock-'+str(index)+'.json')).write_text(json.dumps({'traceEvents':events,'clock':clock,'correlation':'uncorrelated GPU only'}))
    # No external assets or executable capture strings.
    embedded=json.dumps(result).replace('<','\\u003c')
    page='''<!doctype html><meta charset="utf-8"><title>ShaderLOD cost map</title>
<style>body{font:16px system-ui;margin:2em}td,th{padding:.5em;text-align:left;border-bottom:1px solid #ccc}pre{white-space:pre-wrap}.bar{background:#518aaf;color:white;min-width:2px;padding:4px;margin:2px}summary{cursor:pointer}</style>
<h1>Diagnostic cost map</h1><p>Display: milliseconds. Inclusive parents and mixed tags are nonadditive. Overhead unqualified.</p>
<label>View <select id="view"><option>costs</option><option>coverage</option><option>DH</option><option>physical_joins</option><option>hierarchy</option><option>investigation_queue</option><option>CPU wall flame</option><option>frame pacing</option></select></label>
<input id="search" placeholder="Filter named scopes"><div id="content"></div><script>
const data=DATA;const root=document.querySelector('#content');
function pre(value){let p=document.createElement('pre');p.textContent=JSON.stringify(value,null,2);return p}
function show(){let view=document.querySelector('#view').value;root.replaceChildren();
if(view==='frame pacing'){root.append(pre({frame_ms:scale(data.frame_pacing),window_points:data.window_points.map(scale),limits:'Frame variance and window means are separate; one process is not independent process replication.'}));return}
if(view==='CPU wall flame'){let byId=new Map(data.cpu_trace.map(e=>[e.args.event_id,e]));let children=new Map();for(let e of data.cpu_trace){let parent=byId.has(e.args.parent_id)?e.args.parent_id:null;if(!children.has(parent))children.set(parent,[]);children.get(parent).push(e)}
function node(e){let d=document.createElement('details');d.open=true;let title=document.createElement('summary');title.textContent=e.name+' '+(e.dur/1000).toFixed(4)+' ms CPU wall (inclusive)';d.append(title);let bar=document.createElement('div');bar.className='bar';bar.style.width=Math.max(1,Math.min(95,e.dur/Math.max(...data.cpu_trace.map(x=>x.dur))*95))+'%';bar.textContent='event '+e.args.event_id;d.append(bar);for(let c of children.get(e.args.event_id)||[])d.append(node(c));return d}
for(let e of children.get(null)||[])root.append(node(e));return}
let rows=view==='DH'?data.coverage.filter(x=>x.concern?.startsWith('DH/')):data[view];rows=(rows||[]).filter(x=>JSON.stringify(x).toLowerCase().includes(document.querySelector('#search').value.toLowerCase()));
if(view==='costs'||view==='investigation_queue'){let table=document.createElement('table');let keys=['name','metric','mean_ns','median_ns','p95_ns','sd_ns','count','mapping','features'];let head=document.createElement('tr');for(let k of keys){let th=document.createElement('th');th.textContent=k.replace('_ns',' ms');head.append(th)}table.append(head);for(let row of rows){let tr=document.createElement('tr');for(let k of keys){let td=document.createElement('td');td.textContent=row[k]==null?'unknown':k.endsWith('_ns')?(row[k]/1e6).toFixed(5):JSON.stringify(row[k]);tr.append(td)}let details=document.createElement('td');details.append(pre(row));tr.append(details);table.append(tr)}root.append(table)}else for(let row of rows)root.append(pre(row))}
function scale(row){let out={};for(let [key,value] of Object.entries(row)){out[key.endsWith('_ns')?key.slice(0,-3)+'_ms':key]=key.endsWith('_ns')&&value!=null?value/1e6:value}return out}
document.querySelector('#view').onchange=show;document.querySelector('#search').oninput=show;show()</script>'''.replace('DATA',embedded)
    (output/'report.html').write_text(page)
    files={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in output.iterdir()}
    (output/'finalized.json').write_text(json.dumps({'state':'finalized','sha256':files},indent=2))
    return result


def plan(args):
    rows=[]
    for group in range(args.scope_sets):
        count=max(0,math.floor(args.fps*args.seconds*args.windows*args.processes/args.cadence/args.scope_sets))
        rows.append({'scope_set':group+1,'expected_frames':count,'minimum':args.minimum,'feasible':count>=args.minimum})
    return {'formula':'floor(fps * seconds * windows * processes / cadence / scope_sets)',
            'assumptions':'regular execution, no losses; estimates are not measured counts', 'launched':False,'table':rows,'feasible':all(r['feasible'] for r in rows)}


def command(argv):
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=['profile-plan','profile','profile-report'])
    p.add_argument('--dh-transition-check',action='store_true');p.add_argument('--live',action='store_true');p.add_argument('--profile',type=Path);p.add_argument('--pack');p.add_argument('--dh',action='store_true');p.add_argument('--world',default='ShaderLOD-Benchmark-overworld-3-flat')
    p.add_argument('--input',type=Path);p.add_argument('--output',type=Path)
    p.add_argument('--fps',type=float,default=60);p.add_argument('--seconds',type=float,default=20)
    p.add_argument('--windows',type=int,default=3);p.add_argument('--processes',type=int,default=2)
    p.add_argument('--cadence',type=int,default=4);p.add_argument('--scope-sets',type=int,default=7);p.add_argument('--minimum',type=int,default=100)
    a=p.parse_args(argv)
    if any(getattr(a,k)<=0 for k in ('fps','seconds','windows','processes','cadence','scope_sets','minimum')):p.error('positive sampling inputs required')
    if a.mode=='profile-plan':
        result=plan(a);print(json.dumps(result,indent=2));return 0 if result['feasible'] else 1
    if a.live:
        if a.mode!='profile' or not a.profile or not a.output or a.input:p.error('live profile requires --profile and --output without --input')
        from cost_map_live import run
        return run(a)
    if not a.input or not a.output:p.error('--input and --output required')
    finalization=a.input.parent/'finalized.json'
    if finalization.exists():
        hashes=json.loads(finalization.read_text())['sha256']
        expected=hashes.get(a.input.name)
        if expected is None or hashlib.sha256(a.input.read_bytes()).hexdigest()!=expected:raise ValueError('finalized input checksum mismatch')
    data=json.loads(a.input.read_text())
    if 'schema_version' not in data:
        print(json.dumps({'state':'legacy-duration-only','report':data,'limits':'no endpoints or hierarchy; retained without reinterpretation'}));return 0
    export(data,a.output);print(json.dumps({'state':'finalized','report':str(a.output/'report.html'),'launched':False}));return 0

if __name__=='__main__':
    import sys
    raise SystemExit(command(sys.argv[1:]))
