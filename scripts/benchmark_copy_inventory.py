"""Untimed retained-copy inventory, with no permission to remove copies."""
import experiment_lifecycle as life


def validate(record):
    frames=record.get('finalPassesCompleted')
    life.require(type(frames) is int and frames>0 and record.get('finalPassesEntered')==frames,
                 'Incomplete final-copy inventory')
    life.require(record.get('apiPinned') is True and record.get('metadataStatus')=='OK'
                 and record.get('graphChanges')==1 and record.get('saturated') is False,
                 'Unpinned or changing final-copy graph')
    life.require(not any(record.get(k,0) for k in ('metadataFailures','streamMismatches',
                 'unknownByteCopies','invalidExtentCopies','executedFallbackCopies')),
                 'Final-copy metadata/execution mismatch')
    targets=record.get('targets')
    life.require(isinstance(targets,list),'Missing final-copy targets')
    life.require(record.get('executedSwapCopies')==record.get('plannedSwapCopies')==frames*len(targets)
                 and all(t.get('executedCopies')==frames for t in targets),
                 'Final-copy target execution is incomplete')
    return {'frames':frames,'targets':targets,'executed_copies_per_frame':len(targets),
            'scope':'Returned native copy submissions and current physical target inventory; not GPU completion, history liveness or deletion safety'}


def capture(control):
    try:
        control.send('frame_copy_observe',enabled=True,reset=True)
        control.ready();control.wait(2)
        record=control.send('status')['state']['installed']['frame_copy']
        return {'actual':record,'inventory':validate(record)}
    finally:
        control.send('frame_copy_observe',enabled=False,reset=False)
