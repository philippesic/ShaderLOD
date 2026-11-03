"""Finite, explicitly untimed C01 cost discriminator; inclusive scopes overlap."""
import experiment_lifecycle as life


def capture(control,*,group=32):
    try:
        row=control.send('c01_cost',observe=True,group=group)['c01_cost']
        for _ in range(120):
            work=row['work'];observation=row['observation']
            if (work.get('remaining_frames')==0 and observation.get('remaining_frames')==0
                    and observation.get('pending_gpu_queries')==0):break
            control.wait(.25)
            row=control.send('c01_cost')['c01_cost']
        work=row['work'];observation=row['observation'];cost=work.get('cost',{})
        life.require(work.get('frames')==128 and work.get('remaining_frames')==0
                     and work.get('cpu_result_reads',0)>0 and not work.get('refusal')
                     and observation.get('remaining_frames')==0
                     and observation.get('pending_gpu_queries')==0
                     and len(observation.get('frames',[]))==128,
                     'Incomplete C01 capture membership/query interval')
        rows=cost.get('rows',[])
        members={frame.get('iris_frame') for frame in observation.get('frames',[])}
        life.require(cost.get('enabled') is True and cost.get('observing') is True
                     and cost.get('dropped')==0 and rows
                     and len(members)==128 and all(type(value) is int and value>=0 for value in members)
                     and all(type(r.get('frame')) is int and 1<=r['frame']<=128
                             and r.get('iris_frame') in members
                             and r.get('thread_cpu_ns',-1)>=0 and r.get('wall_ns',-1)>=0 for r in rows),
                     'Invalid C01 CPU clock, membership or dropped records')
        row['scope']='Exclusive untimed128-frame diagnostic. Inclusive scopes overlap; wall minus CPU is not pure GPU wait. No normal-frame performance result.'
        return row
    finally:
        control.send('c01_cost',observe=False)
