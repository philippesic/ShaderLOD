"""Owned, untimed same-input Photon cloud-reuse observation."""
import json
from pathlib import Path
import sys
import build_photon_reuse_probe as producer
import experiment_lifecycle as life
import dev_client as dev
import benchmark_private_off as private_off

ROOT=Path(__file__).resolve().parents[1]
NAME='ShaderLOD-G08-cloud-probe.zip'
BASE=ROOT/'dev/experiments/shortlist-resume-20261101/g08-photon-light-reuse-r3/selected-candidate.zip'

def identity(pack,options):
    expected=json.loads((ROOT/'src/main/resources/assets/shaderlod/photonquad/native366.json').read_text())
    life.require(life.digest(pack)==producer.ORIGINAL and dev.properties(Path(options).read_text())==expected,'Cloud probe requires original Photon1.3b/native366')
    life.require(life.digest(BASE)==producer.BASE,'Retained actual public archive changed')
    return {'native_options':expected,'generated':NAME,'base':str(BASE),'original_sha256':producer.ORIGINAL,'producer_sha256':life.digest(producer.__file__)}

def stage(op,options,identity):
    life.require(life.digest(producer.__file__)==identity['producer_sha256'],'Cloud producer changed')
    directory=op.output/'g08-inputs';directory.mkdir();archive=directory/NAME
    result=producer.build(identity['base'],archive);Path(str(archive)+'.txt').write_bytes(Path(options).read_bytes())
    expected={name:row['expected'] for name,row in op.data['configuration'].items()}
    op.begin_mutation('stage-g08-cloud-probe')
    try:
        for p in (archive,Path(str(archive)+'.txt')):
            target=Path(op.data['profile'])/'shaderpacks'/p.name;name=str(target)
            life.require(name in expected and life.digest(target)==expected[name],'Cloud stage target changed')
            target.write_bytes(p.read_bytes());expected[name]=life.digest(target)
    finally:
        life.require(all(life.digest(name)==pin for name,pin in expected.items()),'Cloud stage ownership uncertain')
        op.acknowledge_mutation('stage-g08-cloud-probe')
    op.pin(list(directory.iterdir()));return result

def capture(control,identity,artifact,*,original):
    try:
        control.send('diagnostic_pack',pack=NAME,sha256=artifact['sha256']);state=private_off.clear(control,NAME,artifact['sha256'])
        native={k:v for k,v in state['effective_options'].items() if k in identity['native_options']}
        extra=set(state['effective_options'])-set(identity['native_options'])
        life.require(all(k.startswith('SHADERLOD_') or k=='PROGRAM_GBUFFERS_TERRAIN_SOLID' and state['effective_options'][k]=='true' for k in extra),'Unexpected cloud option additions')
        life.require(native==identity['native_options'] and state['installed']['master'] is False
                     and not any(v in (True,'true') for v in state['installed'].get('flags',{}).values()),'Cloud probe native map/Off differs')
        control.send('g08_cloud_status',observe=True);control.wait(.25)
        phases=int(identity['native_options']['CLOUDS_TEMPORAL_UPSCALING'])**2
        life.require(1<=phases<=16,'Bounded native cloud phase count required')
        control.send('g08_cloud_queue',sha256=artifact['sha256'],phases=phases)
        for _ in range(120):
            queued=control.send('g08_cloud_queue')['g08_queue']
            life.require(not queued.get('error'),'Cloud phase capture refused: '+str(queued.get('error')))
            if not queued.get('active'):break
            control.wait(.25)
        rows=queued.get('captures',[])
        life.require(len(rows)==phases and {r.get('checkerboard_phase') for r in rows}==set(range(phases))
                     and all(r.get('state_restored') is True and r.get('pixels',0)>0 for r in rows),'Native checkerboard phase coverage incomplete')
        return {'captures':rows,'phase_count':phases,'scope':'Untimed same-fragment optical-depth and cloud float-output comparisons; no displayed image or hardware fetch proof.'}
    finally:
        primary=sys.exc_info()[1];failures=[]
        for action,fields in [('g08_cloud_status',{'observe':False}),('diagnostic_pack',{'pack':original,'sha256':producer.ORIGINAL}),('diagnostic_empty_requests',{'pack':original,'sha256':producer.ORIGINAL})]:
            try:control.send(action,**fields)
            except Exception as error:failures.append(str(error))
        try:control.ready()
        except Exception as error:failures.append(str(error))
        if failures:
            if primary is not None:primary.add_note('Cloud cleanup: '+'; '.join(failures))
            else:raise RuntimeError('Cloud cleanup: '+'; '.join(failures))
