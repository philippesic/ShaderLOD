"""Finite private G05 fixed16 hierarchy: unchanged originals and owned staging."""
import json
from pathlib import Path
import shutil
import sys

import build_makeup_depth_hierarchy_consumer as producer
import dev_client as dev
import experiment_lifecycle as life

ROOT=Path(__file__).resolve().parents[1]
ORIGINAL='0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
LEAN='545bec198f2f79a2337bb6853a4dbd5447a1380153c2a78fdffa18792d82b375'
WORK='ee6d3d2c47457b3011200c5837d69a2f2f892a7d8acc980fe855eb8dae33224a'


def identity(pack,options):
    expected=json.loads((ROOT/'src/main/resources/assets/shaderlod/makeupquad/native100.json').read_text())
    life.require(life.digest(pack)==ORIGINAL and dev.properties(Path(options).read_text())==expected,
                 'Fixed16 requires original MakeUp9.5g/native100')
    lean=ROOT/'dependencies/shortlist-test-inputs/g05-fixed16/lean-active.zip'
    life.require(life.digest(lean)==LEAN,'Retained accepted fixed16 lean input differs')
    files=[lean,Path(producer.__file__),ROOT/'src/devClient/java/com/pp/shaderlod/experiment/hierarchy/DepthHierarchyR50.java']
    return {'candidate':'g05-fixed16','original_sha256':ORIGINAL,'native_options':expected,
            'generated':'ShaderLOD-G05-fixed16-lean.zip','work':'ShaderLOD-G05-fixed16-work.zip',
            'lean':str(lean),'producers':{str(p):life.digest(p) for p in files},
            'scope':'Private pinned16-cell conservative hierarchy, two factor4 reductions; native options/AA unchanged.'}


def stage(op,pack,options,identity):
    life.require(all(life.digest(p)==pin for p,pin in identity['producers'].items()),'Fixed16 producer/input changed')
    directory=op.output/'g05-inputs';directory.mkdir()
    lean=directory/identity['generated'];work=directory/identity['work']
    shutil.copy2(identity['lean'],lean)
    sidecar=Path(options).read_bytes()+b'\nSHADERLOD_G05_DEPTH_HIERARCHY=1\n'
    Path(str(lean)+'.txt').write_bytes(sidecar)
    producer.build(pack,options,work,diagnostic=True,control='skip',private_asahi=True,fixed16=True)
    life.require(life.digest(work)==WORK,'Fixed16 diagnostic no longer matches accepted consumer source pin')
    outputs={Path(op.data['profile'])/'shaderpacks'/p.name:p
             for p in (lean,Path(str(lean)+'.txt'),work,Path(str(work)+'.txt'))}
    expected={name:row['expected'] for name,row in op.data['configuration'].items()}
    op.begin_mutation('stage-g05-fixed16')
    try:
        for target,payload in outputs.items():
            name=str(target);life.require(name in expected and life.digest(target)==expected[name],'Fixed16 stage target changed')
            target.write_bytes(payload.read_bytes());expected[name]=life.digest(payload)
    finally:
        life.require(all(life.digest(name)==pin for name,pin in expected.items()),'Fixed16 stage ownership uncertain; preserve files')
        op.acknowledge_mutation('stage-g05-fixed16')
    op.pin(list(directory.iterdir()))
    return {'lean_sha256':LEAN,'work_sha256':WORK}


def activation(state,identity,*,active,expected_pack):
    values=dict(identity['native_options'])
    if active:values['SHADERLOD_G05_DEPTH_HIERARCHY']='1'
    life.require(state.get('pack')==expected_pack and state.get('effective_options')==values,'Fixed16 operative native options/pack differ')
    installed=state.get('installed',{})
    life.require(installed.get('master') is False and not any(v in (True,'true') for v in installed.get('flags',{}).values()),
                 'Fixed16 private candidate requires native Off and empty public requests')


def progress(record,*,active,before=None):
    life.require(record.get('fixed16') is True and record.get('cell_size')==16 and not record.get('failure')
                 and record.get('cost_opt_in') is True and record.get('producer_enabled') is active
                 and record.get('binding_active') is False and record.get('idle_last_program_permission')==0,
                 'Fixed16 current ownership/revocation status refused')
    if active:
        life.require(record.get('control')=='skip' and record.get('builds',0)>0
                     and record.get('positive_permissions',0)>0,'No admitted fixed16 production/consumer')
    else:life.require(record.get('image')==0 and record.get('program')==0,'Native fixed16 control retains producer resources')
    if before is not None:
        for key in ('builds','positive_permissions'):
            delta=record.get(key,0)-before.get(key,0)
            life.require(delta>0 if active else delta==0,'Fixed16 progress differs: '+key)


def trace(control,pin):
    packet=control.send('causal_fragment',sha256=pin);row=packet['causal']
    banks=row.get('banks',[])
    life.require(row.get('state_restored') is True and len(banks)==2
                 and sum(b.get('eligible_pixels',0) for b in banks)>0
                 and all(b.get('mismatch_pixels')==0 and b.get('trace_mismatch_pixels')==0 for b in banks),
                 'Fixed16 same-fragment native trace equivalence refused')
    return packet


def diagnose(control,identity,*,original):
    try:
        control.send('depth_hierarchy_cost',enabled=False)
        control.send('diagnostic_pack',pack=identity['work'],sha256=WORK)
        control.send('configure',master=False,features=[]);state=control.ready()
        activation(state,identity,active=True,expected_pack=identity['work'])
        control.send('depth_hierarchy_cost',enabled=True);control.wait(1)
        verified=control.send('depth_hierarchy_verify')['depth_hierarchy']
        life.require(verified.get('verified_tiles')==16000 and verified.get('bit_mismatched_tiles')==0
                     and verified.get('state_restored') is True,'Fixed16 GPU lower-bound tiles refused')
        still=trace(control,WORK)
        camera=still['state']['camera'];start={k:camera[k] for k in ('x','y','z','yaw','pitch')}
        middle=start|{'x':start['x']+.5,'z':start['z']+.5,'yaw':start['yaw']+6,'pitch':start['pitch']+3}
        path={'schema':1,'label':'g05_fixed16_motion','waypoints':[start|{'seconds':0},middle|{'seconds':10},start|{'seconds':20}]}
        control.send('motion_start',path=path);motion=[]
        for _ in range(3):control.wait(3);motion.append(trace(control,WORK))
        return {'tiles':verified,'still':still,'motion':motion,'scope':'Untimed actual tile/readback and native ray trace; not physical fetch counts or performance. Diagnostic retains native RGB.'}
    finally:
        primary=sys.exc_info()[1];failures=[]
        for action,fields in [('motion_stop',{}),('depth_hierarchy_cost',{'enabled':False}),
                              ('diagnostic_pack',{'pack':original,'sha256':ORIGINAL}),
                              ('configure',{'master':False,'features':[]})]:
            try:control.send(action,**fields)
            except Exception as error:failures.append(str(error))
        try:activation(control.ready(),identity,active=False,expected_pack=original)
        except Exception as error:failures.append(str(error))
        if failures:
            if primary is not None:primary.add_note('Fixed16 diagnostic cleanup: '+'; '.join(failures))
            else:raise RuntimeError('Fixed16 diagnostic cleanup: '+'; '.join(failures))
