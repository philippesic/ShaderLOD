"""Bounded native-cave opportunity search, outside all performance windows."""
import time
import experiment_lifecycle as life


def find(control,census,identity):
    observations=[];best=None;deadline=min(time.monotonic()+240,control.end-300)
    try:
        for index in range(min(16,len(census['sites']))):
            for direction in range(4):
                if time.monotonic()>deadline:break
                control.send('c01_bounds',mode='off')
                site=control.send('c01_natural_site',index=index,direction=direction)['natural_site'];control.ready()
                for group in (32,16,8):
                    control.send('c01_bounds',mode='deferred_cpu',prefix=1,group=group,frames=16,delay=256)
                    for _ in range(50):
                        row=control.send('c01_bounds')['c01_bounds']
                        if row.get('remaining_frames')==0:break
                        control.wait(.1)
                    life.require(not row.get('refusal') and row.get('frames')==16 and row.get('remaining_frames')==0,
                                 'Incomplete native cave query opportunity probe')
                    native=row.get('native_indices',0);hidden=row.get('cpu_hidden_indices',0)
                    score=hidden/native if native else 0
                    observation={'index':index,'direction':direction,'group':group,'site':site,'hidden_index_fraction':score,'work':row}
                    observations.append(observation)
                    if row.get('proxy_queries',0)>0 and hidden>0 and (best is None or score>best['hidden_index_fraction']):best=observation
                    # Stop once real omitted submission work is substantial. No speed selection.
                    if best is not None and best['hidden_index_fraction']>=.5:break
                if best is not None and best['hidden_index_fraction']>=.5:break
            if time.monotonic()>deadline or best is not None and best['hidden_index_fraction']>=.5:break
        life.require(best is not None,'No residual admitted hidden-index opportunity in bounded prepared cave search')
        control.send('c01_bounds',mode='off');control.send('c01_natural_site',index=best['index'],direction=best['direction']);control.ready()
        identity['group']=best['group']
        return {'selected':{k:v for k,v in best.items() if k!='work'},'observations':observations,
                'scope':'Untimed16-frame CPU query probes choose positive hidden-index submission opportunity, not speed. Existing native chunks/terrain and native options/AA; no constructed occluders or history reuse.'}
    finally:control.send('c01_bounds',mode='off')


def visibility(control,identity):
    try:
        control.send('c01_bounds',mode='deferred_observe',prefix=identity['prefix'],group=identity['group'],frames=32,delay=identity['delay'])
        for _ in range(100):
            row=control.send('c01_bounds')['c01_bounds']
            if row.get('remaining_frames')==0:break
            control.wait(.1)
        records=row.get('records',[])
        life.require(not row.get('refusal') and row.get('frames')==32 and row.get('remaining_frames')==0
                     and row.get('proxy_queries',0)>0 and records
                     and row.get('false_hidden_observations')==0
                     and any(r.get('proxy_passed') is False and r.get('native_samples')==0 for r in records),
                     'Incomplete independent native-sample hidden-query observation')
        return {'work':row,'scope':'Untimed original native draws retained; actual native GL_SAMPLES_PASSED compared against current-frame proxy queries. No omitted native draw in this observer, no displayed-image/motion or historical-query permission.'}
    finally:control.send('c01_bounds',mode='off')
