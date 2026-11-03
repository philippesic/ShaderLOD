"""Finite untimed current-consumer admission cost, never a scored-window observer."""
import experiment_lifecycle as life


def validate(row):
    cost=row.get('cost',{})
    life.require(row.get('active') is False and not row.get('error') and row.get('completed_render_frames',0)>=128
                 and cost.get('enabled') is True and cost.get('observing') is False and not cost.get('error'),
                 'Incomplete CPU admission-cost observation')
    before=row.get('cache_before',{});after=row.get('cache_after',{})
    life.require(after.get('validations')==before.get('validations'),'Cost interval included cold admission validation')
    for name in ['compaction','scalar']:
        record=cost.get(name,{})
        life.require(record.get('calls',0)>0 and record.get('calls')==record.get('accepted')
                     and record.get('thread_cpu_ns',0)>0 and record.get('wall_ns',0)>0,
                     'Missing admitted current-consumer costs: '+name)
    for name in ['compactor_body','scalar_publication','scalar_lifecycle','native_fill_inclusive',
                 'maximum_query_inclusive','native_submission_delegate','clock_baseline']:
        record=cost.get(name,{})
        life.require(record.get('calls',0)>0 and record.get('calls')==record.get('accepted')
                     and record.get('thread_cpu_ns',0)>0 and record.get('wall_ns',0)>0,
                     'Missing completed current-body costs: '+name)
    life.require(cost['clock_baseline']['calls']==4096,'Bounded empty clock calibration missing')
    return row


def validate_transitions(row):
    transitions=row.get('dh_transitions',[])
    life.require(row.get('dh_installed') is False and row.get('properties_restored') is True and row.get('allowed_after_restore') is True
                 and [r.get('dh_opt_in') for r in transitions]==[False,True,False]
                 and all(r.get('allowed') is True for r in transitions),'No-DH mode transition context differs')
    for previous,record in zip(transitions,transitions[1:]):
        life.require(record['before']==previous['after'],'Mode transition counters are not contiguous')
        life.require(record['after']['validations']==record['before']['validations']+1,
                     'DH mode did not invalidate warm native graph metadata')
    ignored=row.get('irrelevant_modes',{})
    life.require(ignored.get('before')==transitions[-1]['after'],'Obsolete mode check is not in the same interval')
    life.require(ignored.get('allowed') is True and ignored['after']['validations']==ignored['before']['validations']
                 and ignored['after']['hits']==ignored['before']['hits']+1,'Obsolete mode flags changed current admission')
    return row


def capture(control):
    try:
        control.send('cpu_admission_cost',frames=128)
        row=None
        for _ in range(240):
            row=control.send('cpu_admission_cost')['cpu_admission_cost']
            if not row.get('active'):break
            control.wait(.25)
        result=validate(row or {})
        transitions=control.send('cpu_admission_cost',transitions=True)['cpu_mode_transitions']
        return {'actual_cost':result,'mode_transitions':validate_transitions(transitions),
                'scope':'Separate untimed actual consumer predicates, compactor/scalar bodies, inclusive native fill/query and submission delegate; nested costs must not be summed. Empty-pair clock overhead measured, not blindly subtracted. No scored observer, GPU completion or live-DH claim'}
    finally:control.send('cpu_admission_cost',stop=True)
