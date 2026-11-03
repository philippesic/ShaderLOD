"""Finite untimed actual source/pass/image observations for existing mip candidates."""
import experiment_lifecycle as life

def capture(control, *, settled=False, selection=None):
    try:
        # Selection is one owned render-thread transaction: validate, drain old
        # work, arm actual-source hooks, then reload the selected ZIP once.
        control.send('mip_family_observe',observe=True,reload=True,**(selection or {}));control.ready()
        row=None
        for _ in range(30):
            row=control.send('mip_family_observe')['mip_family']
            life.require(not row.get('error'),'Mip family observation refused: '+str(row.get('error')))
            if row.get('stopped'):break
            control.wait(.1)
        life.require(row and row.get('stopped') is True and row.get('completed_frames')==2
                     and row.get('sources') and row.get('events'),'Incomplete mip family observation')
        initial=None
        if settled:
            initial=row
            control.wait(5)
            control.ready()
            control.send('mip_family_observe',rearm_settled=True)
            for _ in range(30):
                row=control.send('mip_family_observe')['mip_family']
                life.require(not row.get('error'),'Settled mip observation refused: '+str(row.get('error')))
                if row.get('stopped'):break
                control.wait(.1)
            life.require(row and row.get('stopped') is True and row.get('completed_frames')==2
                         and row.get('settled_rearm') is True and row.get('settled_screenshot'),
                         'Incomplete settled AO observation')
        # Parsed selected roots remain separate from actual linked sources and events.
        graph=control.send('graph_dump')['graph_export']
        result={'actual':row,'parsed_graph_export':graph,'scope':'Untimed actual source/link and physical GL events; not standalone liveness, compute/terrain-consumer or pixel proof.'}
        if initial is not None:result['initial_after_reload_actual']=initial
        return result
    finally:control.send('mip_family_observe',observe=False)
