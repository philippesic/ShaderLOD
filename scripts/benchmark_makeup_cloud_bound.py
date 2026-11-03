"""Owned native100 G08 bound, actual sampling proof and native-opacity/output audit."""
import json,sys
from pathlib import Path
import build_makeup_native_cloud_bound as producer
import experiment_lifecycle as life
import benchmark_private_off as private_off

ROOT=Path(__file__).resolve().parents[1]


def identity(pack,options,*,layout="loop"):
    native=producer.validate(pack,options)
    paths=[Path(producer.__file__),Path(producer.m.__file__)]
    return {'candidate':'g08-native-bound','original_sha256':producer.m.NATIVE,'native_options':native,
            'layout':layout,'generated':producer.LEAN,'work':producer.WORK,'producers':{str(p):life.digest(p) for p in paths},
            'scope':('Native100 nonpositive-density ALU guard; all native texture queries retained, private/defaultOff' if layout=='alu' else 'Finite native100 single-layer original10-sample cloud4-step-tail bound; defaultOff/private, original implicit queries and AA.')}


def stage(op,pack,options,identity):
    life.require(all(life.digest(p)==pin for p,pin in identity['producers'].items()),'Cloud bound producer changed')
    directory=op.output/'g08-bound-inputs';result=producer.build_pair(pack,options,directory,layout=identity["layout"])
    expected={name:row['expected'] for name,row in op.data['configuration'].items()}
    op.begin_mutation('stage-g08-native100-bound')
    try:
        for artifact in result['artifacts'].values():
            for suffix in ('','.txt'):
                p=directory/(artifact['name']+suffix);target=Path(op.data['profile'])/'shaderpacks'/p.name;name=str(target)
                life.require(name in expected and life.digest(target)==expected[name],'Cloud bound stage target changed')
                target.write_bytes(p.read_bytes());expected[name]=life.digest(target)
    finally:
        life.require(all(life.digest(name)==pin for name,pin in expected.items()),'Cloud bound stage ownership uncertain')
        op.acknowledge_mutation('stage-g08-native100-bound')
    op.pin(list(directory.iterdir()));return result


def activation(state,identity,*,active,expected_pack,debug=False):
    expected=dict(identity['native_options'])
    if active:expected|={producer.m.OPTION:'2',producer.m.DEBUG:'1' if debug else '0'}
    life.require(state.get('pack')==expected_pack and state.get('effective_options')==expected,'Native100 cloud pack/options differ')
    installed=state.get('installed',{})
    life.require(installed.get('master') is False and not any(v in (True,'true') for v in installed.get('flags',{}).values()),'Cloud private candidate requires Off/empty public requests')


def sampling(control,pin,identity):
    try:
        control.send('g08_bound_status',observe=True,sha256=pin)
        for _ in range(60):
            control.wait(.1);row=control.send('g08_bound_status')['g08_bound']
            life.require(not row.get('sampler_error'),'Cloud sampling proof refused: '+str(row.get('sampler_error')))
            if row.get('sampler_receipt'):break
        life.require(row.get('qualified') is True and row.get('draws',0)>0
                     and row.get('sampler_receipt',{}).get('state_restored') is True
                     and len(row['sampler_receipt'].get('images',[]))==(1 if identity['layout']=='alu' else 2),'Incomplete actual cloud/atlas sampling proof')
        return row
    finally:control.send('g08_bound_status',observe=False)


def diagnose(control,identity,artifact,*,original):
    pin=artifact['artifacts']['work']['sha256']
    try:
        control.send('diagnostic_pack',pack=identity['work'],sha256=pin);state=private_off.clear(control,identity['work'],pin);activation(state,identity,active=True,expected_pack=identity['work'],debug=True)
        control.send('g08_bound_status',observe=True,sha256=pin);control.wait(.2)
        control.send('g08_bound_queue',sha256=pin,phases=16)
        for _ in range(120):
            queued=control.send('g08_bound_queue')['g08_bound_queue']
            life.require(not queued.get('error'),'Native cloud bound capture refused: '+str(queued.get('error')))
            if not queued.get('active'):break
            control.wait(.25)
        rows=queued.get('captures',[])
        life.require(len(rows)==16 and {r.get('checkerboard_phase') for r in rows}==set(range(16))
                     and all(r.get('state_restored') is True and r.get('pixels',0)>0
                             and r.get('nonzero_native_opacity_pixels')==0
                             and r.get('output_difference_pixels')==0 and r.get('nonfinite_pixels')==0 for r in rows),
                     'Native opacity/numeric output equivalence or16-frame coverage refused')
        return {'captures':rows,('guarded_native_alu_iterations' if identity['layout']=='alu' else 'omitted_native_steps'):sum(r.get('guarded_native_alu_iterations' if identity['layout']=='alu' else 'omitted_native_steps',0) for r in rows),
                'scope':'Untimed16 Iris-counter residues; actual native cloud/atlas byte+sampler proof and omitted-position opacity/native numericRGB checks. Displayed diagnosticRGBnative; no whole-image/motion/physical-fetch/performance inference.'}
    finally:
        primary=sys.exc_info()[1];failures=[]
        for action,fields in [('g08_bound_status',{'observe':False}),('diagnostic_pack',{'pack':original,'sha256':identity['original_sha256']}),('diagnostic_empty_requests',{'pack':original,'sha256':identity['original_sha256']})]:
            try:control.send(action,**fields)
            except Exception as error:failures.append(str(error))
        try:activation(control.ready(),identity,active=False,expected_pack=original)
        except Exception as error:failures.append(str(error))
        if failures:
            if primary is not None:primary.add_note('Native cloud cleanup: '+'; '.join(failures))
            else:raise RuntimeError('Native cloud cleanup: '+'; '.join(failures))
