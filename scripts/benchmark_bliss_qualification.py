"""Maintained balanced qualification of frozen Bliss AO, including same-route nulls.

Each process is a block, not thousands of independent frame experiments.
No single-block metric is an optimization acceptance verdict.
"""
from pathlib import Path
import json
import math
import os
import shutil
import statistics
import time

import benchmark_bliss_exact_ao as ao
import benchmark_iteration as iteration
import experiment_lifecycle as life

ORDERS = ('ABBA', 'BAAB')
PROTOCOL_VARIANT = 'atomic-observed-reload-v3'


def require_off(state):
    installed = state.get('installed', {})
    life.require(installed.get('master') is False
                 and not any(v in (True, 'true') for v in installed.get('flags', {}).values()),
                 'Private qualification requires master Off and empty requests before reload')


def require_heap(state, expected_bytes):
    life.require(type(state.get('heap_max_bytes')) is int and state['heap_max_bytes'] == expected_bytes,
                 'Effective JVM heap maximum differs from declared owned budget')


def memory_observation(pid):
    """Read-only endpoint telemetry. It does not run inside the render loop."""
    life.require(type(pid) is int and pid > 0, 'Owned client PID required for memory telemetry')
    if os.name == 'nt':
        from benchmark_windows_telemetry import memory_observation as windows_memory
        return windows_memory(pid)
    started = time.monotonic_ns()
    proc = Path('/proc')/str(pid)
    fields = (proc/'stat').read_text().rpartition(') ')[2].split()
    status = dict(line.split(':',1) for line in (proc/'status').read_text().splitlines() if ':' in line)
    process = {'pid':pid,'start_ticks':int(fields[19]),'minor_faults':int(fields[7]),
               'major_faults':int(fields[9]),'user_ticks':int(fields[11]),'system_ticks':int(fields[12])}
    for key in ('VmRSS','VmSwap','VmSize','RssAnon','RssFile','RssShmem'):
        if key in status:process[key+'_kib'] = int(status[key].split()[0])
    memory = {}
    for line in Path('/proc/meminfo').read_text().splitlines():
        key,value = line.split(':',1)
        if key in ('MemTotal','MemAvailable','MemFree','Shmem','Unevictable','SwapTotal','SwapFree'):
            memory[key+'_kib'] = int(value.split()[0])
    pressure = {}
    for kind in ('cpu','memory','io'):
        pressure[kind] = {}
        for line in Path('/proc/pressure',kind).read_text().splitlines():
            category,*tokens = line.split()
            pressure[kind][category] = {key:int(value) if key == 'total' else float(value)
                                       for key,value in (token.split('=') for token in tokens)}
    vm = dict(line.split() for line in Path('/proc/vmstat').read_text().splitlines())
    cgroups = {}
    membership = next((line[3:] for line in (proc/'cgroup').read_text().splitlines()
                       if line.startswith('0::')), None)
    if membership is not None:
        root = Path('/sys/fs/cgroup')
        current = root / membership.lstrip('/')
        while current != root:
            try:
                cgroups[str(current.relative_to(root))] = {
                    name: (current/name).read_text().strip()
                    for name in ('memory.current', 'memory.high', 'memory.max', 'memory.events.local', 'memory.pressure')}
            except OSError as error:
                cgroups[str(current.relative_to(root))] = {'unavailable': str(error)}
            current = current.parent
    return {'started_ns':started,'finished_ns':time.monotonic_ns(),'process':process,'memory':memory,
            'pressure':pressure,'swap_pages':{key:int(vm[key]) for key in ('pswpin','pswpout')},
            'cgroups':cgroups,
            'page_bytes':os.sysconf('SC_PAGE_SIZE'),
            'scope':'External pre/post brackets include controller/export endpoints. PSI/swap are global host counters, not isolated client/GPU attribution.'}


def memory_delta(before, after):
    life.require(before['process']['pid'] == after['process']['pid']
                 and before['process']['start_ticks'] == after['process']['start_ticks'], 'Memory telemetry process changed')
    elapsed = after['started_ns']-before['finished_ns']
    life.require(elapsed > 0, 'Memory endpoint chronology invalid')
    pressure = {}
    for kind in before['pressure']:
        pressure[kind] = {}
        for category in before['pressure'][kind]:
            delta = after['pressure'][kind][category]['total']-before['pressure'][kind][category]['total']
            life.require(delta >= 0, 'Host pressure counter reset')
            pressure[kind][category+'_stall_percent'] = 100*delta/(elapsed/1000)
    faults={key:after['process'][key]-before['process'][key]
            for key in ('minor_faults','major_faults','page_faults') if key in before['process']}
    swaps={key:(after['swap_pages'][key]-before['swap_pages'][key])*before['page_bytes'] for key in before['swap_pages']}
    cgroup_events = {}
    for name, first in before.get('cgroups', {}).items():
        last = after.get('cgroups', {}).get(name, {})
        if 'memory.events.local' not in first or 'memory.events.local' not in last:
            continue
        parse = lambda text: dict((k, int(v)) for k,v in (line.split() for line in text.splitlines()))
        old, new = parse(first['memory.events.local']), parse(last['memory.events.local'])
        cgroup_events[name] = {k:new[k]-v for k,v in old.items() if k in new}
        life.require(all(v >= 0 for v in cgroup_events[name].values()), 'Cgroup memory event counters reset')
    life.require(all(v >= 0 for v in [*faults.values(),*swaps.values()]), 'Fault/swap telemetry counters reset')
    return {'external_bracket_seconds':elapsed/1e9,'pressure':pressure,
            'client_fault_deltas':faults,'host_swap_bytes':swaps,
            'cgroup_memory_events':cgroup_events,
            'unavailable':before.get('unavailable', []),
            'scope':'External endpoint deltas; unavailable counters are omitted, not zero. Host pressure/swap are global; no causal attribution or frame filtering.'}


def validate_capture(directory, label, *, seconds, delay_ns=0, expected_pack=None, expected_heap_bytes=None):
    """Independently reconcile raw captures; a short positive vector is insufficient."""
    directory = Path(directory)
    metrics = iteration.arm_metrics(directory, label)
    guard = json.loads((directory/'guard.json').read_text())
    row = json.loads((directory/'result.json').read_text())['manifest']['windows'][0]
    vector = row['samples']
    count = metrics['completed_render_frames']
    life.require(len(vector) == count and guard.get('excluded_initial_unavailable_interval') is True,
                 'Qualification raw samples must match completed renders and one initial unavailable interval')
    life.require([s.get('frameId') for s in vector] == list(range(1,count+1)),
                 'Qualification frame IDs must be contiguous and unique')
    epochs = [s.get('epoch') for s in vector]
    life.require(all(type(e) is int and e > 0 for e in epochs) and len(set(epochs)) == 1,
                 'Qualification samples cross or omit a clock epoch')
    before = json.loads((directory/'before.json').read_text())
    after = json.loads((directory/'after.json').read_text())
    for key in ('world_frames','render_frames'):
        life.require(type(before.get(key)) is int and type(after.get(key)) is int
                     and after[key]-before[key] == count,
                     'Qualification world-entry/render completion reconciliation failed: '+key)
    ends = guard.get('completed_render_ends_ns')
    life.require(isinstance(ends,list) and len(ends) == count
                 and all(type(t) is int for t in ends)
                 and all(a < b for a,b in zip(ends,ends[1:])),
                 'Qualification returned-render timestamps missing, duplicated or unordered')
    life.require(all(guard['started_ns'] <= t <= guard['stopped_ns'] for t in ends),
                 'Returned render timestamp outside capture')
    acquired_ns = sum(s['totalFrameNs'] for s in vector[1:])
    life.require(type(guard.get('started_ns')) is int and type(guard.get('stopped_ns')) is int
                 and guard['wall_ns'] == guard['stopped_ns']-guard['started_ns'],
                 'Qualification wall duration and endpoints disagree')
    life.require(acquired_ns <= guard['wall_ns'], 'Scored start intervals exceed capture duration')
    life.require(all(type(s.get('renderWallNs')) is int and 0 <= s['renderWallNs'] <= guard['wall_ns'] for s in vector),
                 'Invalid render-scope duration')
    life.require(all(a['renderWallNs'] <= b['totalFrameNs'] for a,b in zip(vector,vector[1:])),
                 'Render scope exceeds its following start interval')
    expected_end_span = acquired_ns + vector[-1]['renderWallNs']-vector[0]['renderWallNs']
    # clock.end samples before CPU bookkeeping/guard/export timestamp. Only the
    # difference between first and last endpoint overhead remains in this join.
    life.require(abs((ends[-1]-ends[0])-expected_end_span) <= 5_000_000,
                 'Start/render/end timestamps disagree beyond fixed5ms endpoint allowance')
    acquired = acquired_ns/1e9
    life.require(acquired >= seconds-1 and metrics['wall_seconds'] >= seconds,
                 'Qualification window truncated')
    for state in (before, after):
        life.require(state.get('effective_options') == json.loads(ao.producer.PROFILE.read_text())
                     and state.get('dimension') == 'minecraft:overworld'
                     and state.get('width') == 2560 and state.get('height') == 1600
                     and state.get('vsync') is False and state.get('fps_limit') == 260,
                     'Actual timed native options/dimension/presentation differ')
        if expected_pack is not None:life.require(state.get('pack') == expected_pack,'Actual timed selected pack differs')
        if expected_heap_bytes is not None:
            require_heap(state,expected_heap_bytes)
        status = state.get('qualification_observers')
        life.require(isinstance(status,dict) and status and all(v is False for v in status.values()),
                     'Qualification timing contains enabled/armed instrumentation')
    life.require(isinstance(row.get('observerCost'),dict) and row['observerCost'].get('calibration_delay_ns') == str(delay_ns),
                 'Clock calibration declaration differs')
    spins = [w for w in row.get('work',[]) if w['signal'] == 'calibration_spin_iterations']
    life.require(len(spins) == 1 and type(spins[0].get('executed')) in (int,float)
                 and (spins[0]['executed'] > 0 if delay_ns else spins[0]['executed'] == 0),
                 'Clock calibration spin did not match arm')
    if delay_ns:
        life.require(all(type(s.get('renderWallNs')) is int and s['renderWallNs'] >= delay_ns for s in vector)
                     and all(s['totalFrameNs'] >= delay_ns for s in vector[1:]),
                     'Injected delay missing from render and start-interval clocks')
    return {'sample_count':count,'scored_intervals':count-1,'acquired_start_interval_seconds':acquired,
            'returned_render_span_seconds':(ends[-1]-ends[0])/1e9,
            'scope':'Total-frame start intervals; renderLevel entry reconciliation is not GPU completion.'}


def sequence(order):
    life.require(order in ORDERS, 'Balanced qualification order required')
    return [(f'q{i}_{arm}', arm == 'B') for i, arm in enumerate(order)]


def identity(pack, options, mode):
    life.require(mode in ('null', 'ao', 'clock'), 'Explicit null, clock or AO qualification required')
    result = ao.identity(pack, options)
    result['qualification_mode'] = mode
    result['producers'][str(Path(__file__).resolve())] = life.digest(__file__)
    for name in ('scripts/run_benchmark.py','build.gradle',
                 'scripts/benchmark_mip_family.py',
                 'scripts/benchmark_bliss_unused_rt12.py',
                 'src/devClient/java/com/pp/shaderlod/dev/MipFamilyProbe.java',
                 'src/devClient/java/com/pp/shaderlod/dev/ObservedPackReload.java',
                 'src/devClient/java/com/pp/shaderlod/dev/BenchmarkControlProbe.java',
                 'src/benchmarkCore/java/com/pp/shaderlod/measurement/FrameMeasurement.java',
                 'src/devClient/java/com/pp/shaderlod/dev/mixin/GameRendererTimingMixin.java',
                 'src/devClient/java/com/pp/shaderlod/dev/mixin/NeutralBenchmarkFrameMixin.java'):
        path=ao.producer.ROOT/name
        result['producers'][str(path)] = life.digest(path)
    result['generated'] = result['generated'].replace('.zip', '-qualification-' + mode + '.zip')
    result['scope'] = 'Frozen native463 Bliss balanced qualification; same-route native-byte null when selected.'
    return result


def stage(op, pack, options, identity):
    def build(original, native_options, output):
        if identity['qualification_mode'] == 'ao':
            return ao.producer.build(original, native_options, output)
        ao.producer.validate(original, native_options)
        output = Path(output)
        life.require(not output.exists() and not Path(str(output)+'.txt').exists(), 'Fresh null alias required')
        shutil.copyfile(original, output)
        shutil.copyfile(native_options, str(output)+'.txt')
        life.require(life.digest(output) == identity['original_sha256'], 'Null alias must retain original ZIP bytes')
        return {'original_sha256': identity['original_sha256'],
                'candidate_sha256': life.digest(output), 'changed_members': [],
                'scope': 'Known null: byte-identical original ZIP under operation-owned alternate name.'}
    return ao.staging.stage(op, pack, options, identity, build=build)


def source_work(packet, *, active, mode):
    return ao.source_work(packet, active=active and mode == 'ao')


def assess(observations, *, order, mode):
    expected = sequence(order)
    life.require(list(observations) == [label for label, _ in expected], 'Qualification observation order differs')
    baseline = next(observations[label] for label, active in expected if not active)
    result = {}
    for label, active in expected:
        packet = observations[label]
        # Reuse whole attached-source and directive proof, with explicit actual roles.
        result[label] = ao.assess({'native': baseline, 'candidate' if active and mode == 'ao' else 'return': packet})
    return result


def assess_graph(observations, *, order):
    """Reuse the native allocation/binding proof; neither treatment changes this graph."""
    import benchmark_bliss_unused_rt12 as native_graph
    result = {label:native_graph.source_work(observations[label],active=False) for label,_ in sequence(order)}
    reference = next(iter(result.values()))
    for label, rows in result.items():
        life.require(rows['frames'] == reference['frames'], 'Qualification physical bindings/raster/mips changed: '+label)
    return result


def evaluate(output, *, order, mode, execution_completed, restored):
    result = {'execution': 'COMPLETE' if execution_completed else 'FAILED',
              'completion': 'INCOMPLETE', 'validity': 'INVALID', 'improvement': 'UNRESOLVED',
              'review': 'REQUIRED', 'closure': False, 'arms': {}, 'order': order, 'mode': mode,
              'limits': 'One balanced process block. Windows have equal weight; frames are not independent trials. '
                        'Requires prespecified repeated-block calibration/acceptance; no standalone win verdict.'}
    try:
        life.require(execution_completed and restored, 'Execution and restoration must complete')
        for label, _ in sequence(order):
            result['arms'][label] = iteration.arm_metrics(Path(output)/'windows'/label, label)
        means = {arm: [row['mean_total_frame_ms'] for label, row in result['arms'].items()
                       if label.endswith('_'+arm)] for arm in ('A', 'B')}
        p95 = {arm: [row['p95_total_frame_ms'] for label, row in result['arms'].items()
                     if label.endswith('_'+arm)] for arm in ('A', 'B')}
        a, b = (sum(means[arm])/2 for arm in ('A', 'B'))
        result.update(completion='COMPLETE', validity='VALID',
                      improvement='KNOWN_NULL_CONTROL' if mode == 'null' else 'KNOWN_CLOCK_CALIBRATION' if mode == 'clock' else 'REPEATED_BLOCK_DECISION_REQUIRED',
                      mean_A_ms=a, mean_B_ms=b, reduction_percent=100*(a-b)/a,
                      same_arm_spread_percent={arm: 100*(max(v)-min(v))/(sum(v)/2) for arm,v in means.items()},
                      p95_A_ms=sum(p95['A'])/2, p95_B_ms=sum(p95['B'])/2)
    except (OSError, ValueError, KeyError, TypeError) as error:
        result['error'] = str(error)
    return result


def interval(values):
    """Prespecified six-process approximate t interval, never per-frame inference."""
    life.require(len(values) == 6 and all(type(v) in (float,int) and math.isfinite(v) for v in values),
                 'Exactly six finite process-block estimates required')
    mean = statistics.mean(values)
    margin = 2.570581835636314*statistics.stdev(values)/math.sqrt(6)
    return {'mean':mean,'lower':mean-margin,'upper':mean+margin,'n_process_blocks':6,
            'method':'Two-sided95% Studentt df5; approximate with fresh-process blocks, not frames.'}


def cohort(runs, *, mode, calibration=None):
    """All attempted runs belong in the report. Invalid runs never become speed data."""
    life.require(mode in ('null','ao'), 'Cohort must be null or AO')
    life.require(len({Path(r).resolve() for r in runs}) == len(runs), 'A process block cannot be counted twice')
    result={'mode':mode,'decision':'INCOMPLETE','scenes':{},'attempts':[],
            'limits':'One pinned machine/session/pack/options. Few process blocks and possible time dependence limit inference; no public/default/DH/PBR or visual qualification.'}
    groups={0:[],7:[]}
    invalid={0:0,7:0}
    processes=set()
    signature=None
    for run in runs:
        path=Path(run);summary=json.loads((path/'summary.json').read_text())
        contract=summary.get('qualification',{})
        life.require(contract.get('mode') == mode,'Cohort contains another treatment mode')
        life.require(all(contract.get(k) == v for k,v in [('seconds',60),('warmup',20),('conditioning',180)]),
                     'Cohort timing protocol differs from preregistration')
        pose=contract.get('saved_pose')
        life.require(type(pose) is int and pose in groups,'Cohort scene identity missing')
        evaluation=summary.get('evaluation',{})
        valid=evaluation.get('validity') == 'VALID' and summary.get('configuration_restored') is True and summary.get('clients_remaining') == 0
        result['attempts'].append({'run':str(path),'scene':pose,'valid':valid,'error':summary.get('error')})
        if not valid:invalid[pose]+=1;continue
        state=next(iter(summary.get('public_states',{}).values()),{})
        current_signature={'protocol_variant':contract.get('protocol_variant','balanced-v1'),
                           'requested_heap':contract.get('client_heap_requested'),
                           'effective_heap_bytes':state.get('heap_max_bytes'),
                           'heap_and_cpu_arguments':state.get('heap_launch_arguments'),
                           'capture_source_pins':summary.get('private_identity',{}).get('producers'),
                           'gpu_renderer':state.get('gpu_renderer'),'gpu_driver':state.get('gpu_driver'),
                           'loaded_stack':state.get('loaded_stack')}
        if current_signature['protocol_variant'] in ('memory-budget-v2', PROTOCOL_VARIANT):
            requested=current_signature['requested_heap']
            life.require(requested in ('1536m','2304m')
                         and current_signature['effective_heap_bytes'] == int(requested[:-1])*1024*1024
                         and bool(current_signature['capture_source_pins']), 'Effective memory budget/source receipt missing')
        if signature is None:signature=current_signature
        life.require(current_signature == signature, 'Cohort mixes runtime budgets, variants or graphics/mod stacks')
        result['runtime_signature']=signature
        process=(state.get('client_pid'),state.get('client_started_at'))
        life.require(type(process[0]) is int and process[1] and process not in processes,
                     'Repeated or missing fresh-process block identity')
        processes.add(process)
        life.require(evaluation.get('mode') == mode and evaluation.get('order') in ORDERS,'Block evaluation roles differ')
        groups[pose].append(evaluation|{'run':str(path)})
    if mode == 'ao':
        life.require(calibration and calibration.get('mode') == 'null' and calibration.get('decision') == 'CALIBRATED',
                     'AO decision requires both scenes calibrated first')
        life.require(calibration.get('runtime_signature') == signature, 'AO runtime differs from passing calibration')
    for pose,blocks in groups.items():
        row={'blocks':blocks,'invalid_attempts':invalid[pose],'decision':'INCOMPLETE'}
        result['scenes'][str(pose)]=row
        if len(blocks)!=6 or invalid[pose]>2:continue
        life.require(all(sum(b['order']==order for b in blocks)==3 for order in ORDERS),
                     'Six blocks must balance three of each order')
        values=[b['reduction_percent'] for b in blocks]
        effect=interval(values);row['effect']=effect
        if mode=='null':
            checks={'mean_inside_one_percent':abs(effect['mean'])<=1,
                    'interval_inside_two_percent':effect['lower']>=-2 and effect['upper']<=2,
                    'all_blocks_within_three_percent':max(map(abs,values))<=3,
                    'same_treatment_spread_within_five_percent':all(max(b['same_arm_spread_percent'].values())<=5 for b in blocks)}
            row['max_absolute_block_effect']=max(map(abs,values))
            row['checks']=checks;row['decision']='CALIBRATED' if all(checks.values()) else 'MEASUREMENT_NOT_CALIBRATED'
        else:
            p95=interval([100*(b['p95_B_ms']-b['p95_A_ms'])/b['p95_A_ms'] for b in blocks])
            row['p95_regression']=p95
            noise=calibration['scenes'][str(pose)]['max_absolute_block_effect']
            checks={'mean_benefit_at_least_three_percent':effect['mean']>=3,
                    'positive_in_at_least_five_blocks':sum(v>0 for v in values)>=5,
                    'both_order_means_positive':all(statistics.mean(b['reduction_percent'] for b in blocks if b['order']==order)>0 for order in ORDERS),
                    'lower_bound_exceeds_null_extreme':effect['lower']>noise,
                    'mean_p95_regression_at_most_three_percent':p95['mean']<=3,
                    'p95_regression_upper_bound_at_most_five_percent':p95['upper']<=5}
            row['checks']=checks;row['decision']='PERFORMANCE_QUALIFIED_SCOPED' if all(checks.values()) else 'NO_QUALIFIED_BENEFIT'
    decisions=[s['decision'] for s in result['scenes'].values()]
    if 'INCOMPLETE' not in decisions:
        result['decision']=('CALIBRATED' if all(d=='CALIBRATED' for d in decisions) else 'MEASUREMENT_NOT_CALIBRATED') if mode=='null' else 'PER_SCENE_DECISIONS_COMPLETE'
    return result


if __name__ == '__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode',choices=('null','ao'),required=True)
    parser.add_argument('--runs',nargs='+',type=Path,required=True)
    parser.add_argument('--calibration',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    life.require(not args.output.exists(),'Fresh cohort report required')
    report=cohort(args.runs,mode=args.mode,calibration=json.loads(args.calibration.read_text()) if args.calibration else None)
    life.atomic_json(args.output,report)
    print(json.dumps({'decision':report['decision'],'scenes':{k:v['decision'] for k,v in report['scenes'].items()},'report':str(args.output)}))
