#!/usr/bin/env python3
"""Reject confounded ABBA receipts; this is not visual/active-path certification."""
import argparse
import json
from pathlib import Path
import statistics


def evaluate(receipts, threshold=5):
    if len(receipts) != 4 or any(not r['window']['valid'] or
            not 19 <= r['window']['wall_ns']/1e9 <= 23 for r in receipts):
        raise ValueError('Four valid chronological A,B,B,A receipt files required')
    states = [r['state'] for r in receipts]
    if any(state.get('installed',{}).get('master') is not enabled
           for state,enabled in zip(states,(False,True,True,False))):
        raise ValueError('This within-JVM gate requires verified masterOff/On/On/Off')
    keys = ('client_pid','client_started_at','camera','width','height','distance',
            'day_time','rain_level','selected_resource_packs','loaded_stack','vsync','fps_limit','fullscreen')
    for key in keys:
        if any(state[key] != states[0][key] for state in states):
            raise ValueError('Unmatched workload: ' + key)
    if states[0]['vsync'] or states[0]['fps_limit'] != 260:
        raise ValueError('VSync off/cap260 required')
    if not all(states[i]['at_ns'] < states[i+1]['at_ns'] for i in range(3)):
        raise ValueError('Chronological windows required')
    for a,b in ((0,3),(1,2)):
        if states[a]['pack'] != states[b]['pack'] or states[a]['effective_options'] != states[b]['effective_options']:
            raise ValueError('A return or B repeat changed pack/options')
    native={k:v for k,v in states[0]['effective_options'].items() if not k.startswith('SHADERLOD_')}
    if any(any(state['effective_options'].get(k) != v for k,v in native.items()) for state in states):
        raise ValueError('Native options differ across arms')
    values=[r['window']['statistics']['medianMs'] for r in receipts]
    baseline_drift=abs(values[3]/values[0]-1)*100
    candidate_spread=abs(values[2]/values[1]-1)*100
    gain=(1-statistics.mean(values[1:3])/statistics.mean((values[0],values[3])))*100
    nonbinding=all(v>1000/260*1.1 and r['window']['count_throughput_fps']<260*.9 for v,r in zip(values,receipts))
    noise=max(baseline_drift,candidate_spread,threshold)
    return dict(baseline_return_drift_percent=baseline_drift,candidate_repeat_spread_percent=candidate_spread,
                median_gain_percent=gain,chosen_noise_floor_percent=noise,cap_nonbinding=nonbinding,
                distinguishable=nonbinding and baseline_drift<=threshold and candidate_spread<=threshold and abs(gain)>noise,
                limits='Within-session median sanity gate only. Requires separate stability, correctness, active graph and frozen-build evidence; no Mac parity or thermal inference.')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('receipts',type=Path,nargs=4,help='A1 B1 B2 A2 terminal neutral control receipts')
    p.add_argument('--threshold',type=float,default=5)
    a=p.parse_args()
    print(json.dumps(evaluate([json.loads(x.read_text()) for x in a.receipts],a.threshold),indent=2))


if __name__=='__main__':main()
