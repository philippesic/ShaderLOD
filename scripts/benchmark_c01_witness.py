"""Bounded untimed actual primitive counts for the existing current-frame modes."""
import experiment_lifecycle as life


def capture(control,identity):
    result={}
    try:
        for lean,mode in [('deferred','witness_deferred'),('deferred_wait','witness_deferred_wait'),('deferred_cpu','witness_deferred_cpu')]:
            control.send('c01_bounds',mode=mode,prefix=identity['prefix'],group=identity['group'],frames=32,delay=identity['delay'])
            for _ in range(100):
                row=control.send('c01_bounds')['c01_bounds'];witness=row.get('async_witness',{})
                if row.get('remaining_frames')==0 and witness.get('pending')==0:break
                control.wait(.1)
            records=witness.get('records',[])
            life.require(row.get('frames')==32 and row.get('remaining_frames')==0 and not row.get('refusal')
                         and row.get('proxy_queries',0)>0 and witness.get('pending')==0
                         and witness.get('dropped_frames')==0 and len(records)==32
                         and all(r.get('admitted') is True and r.get('count_consistent') is True for r in records),
                         'Incomplete C01 actual primitive witness: '+mode)
            life.require(row.get('cpu_result_reads',0)>0 if lean=='deferred_cpu' else row.get('cpu_result_reads')==0,
                         'C01 diagnostic decision mode differs')
            result[lean]={'work':row,'native_expected_primitives':sum(r['native_expected_primitives'] for r in records),
                          'suppressed_native_primitives':sum(r['suppressed_native_primitives'] for r in records)}
        return {'modes':result,'scope':'Untimed32-frame whole SOLID PRIMITIVES_GENERATED queries, each mode separately; no availability/result polling during finite submission. Source-native expected triangles + proxy triangles - actual total; diagnostic interval only, not timed-mode work counts or image/motion proof.'}
    finally:control.send('c01_bounds',mode='off')
