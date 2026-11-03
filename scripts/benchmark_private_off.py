"""Existing exact-ZIP Off-only controller API; no normal pack/settings reload."""
import experiment_lifecycle as life


def clear(control,pack,pin):
    before=control.send('status')['state']
    life.require(before.get('pack')==pack and before.get('installed',{}).get('master') is False,
                 'Private request reset requires actual selected archive and master Off')
    control.send('diagnostic_empty_requests',pack=pack,sha256=pin)
    state=control.ready()
    life.require(state.get('pack')==pack and state.get('installed',{}).get('master') is False
                 and not any(v in (True,'true') for v in state.get('installed',{}).get('flags',{}).values()),
                 'Private Off request reset did not clear requests')
    life.require(all(state.get(k)==before.get(k) for k in ('pipeline_identity','pack_identity','effective_options')),
                 'Private request reset unexpectedly changed loaded shader inputs/pipeline')
    return state
