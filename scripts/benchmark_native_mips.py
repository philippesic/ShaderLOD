"""Owned staging and admission for the finite native-derived G02 discriminator."""
import hashlib
from pathlib import Path

import build_bsl_native_mips as producer
import experiment_lifecycle as life


def identity(pack, options):
    native = producer.validate(pack, options)
    source = Path(producer.__file__).resolve()
    pin = life.digest(source)
    return {'candidate':'g02-native-mips', 'original_sha256':producer.ORIGINAL,
            'native_options':native, 'producers':{str(source):pin},
            'generated':'ShaderLOD-G02-native-mips-'+pin[:12]+'.zip',
            'scope':'Native-derived c6+c7 deletion only; no public adapter/bridge overhead.'}


def stage(op, pack, options, identity, *, build=None):
    life.require(all(life.digest(p)==v for p,v in identity['producers'].items()),
                 'G02 producer changed since preflight')
    directory=op.output/'g02-inputs';directory.mkdir()
    source=directory/identity['generated']
    receipt=(build or producer.build)(pack,options,source)
    outputs={Path(op.data['profile'])/'shaderpacks'/p.name:p
             for p in (source,Path(str(source)+'.txt'))}
    expected={name:row['expected'] for name,row in op.data['configuration'].items()}
    op.begin_mutation('stage-g02-native-mips')
    try:
        for target,payload in outputs.items():
            name=str(target)
            life.require(name in expected and life.digest(target)==expected[name],
                         'G02 stage target changed')
            target.write_bytes(payload.read_bytes());expected[name]=life.digest(payload)
    finally:
        life.require(all(life.digest(name)==pin for name,pin in expected.items()),
                     'G02 stage ownership uncertain; preserve files')
        op.acknowledge_mutation('stage-g02-native-mips')
    op.pin(list(directory.iterdir()))
    return receipt


def activation(state, identity, *, expected_pack):
    life.require(state.get('pack')==expected_pack,'G02 selected private/original pack differs')
    life.require(state.get('effective_options')==identity['native_options'],
                 'G02 exact native options differ')
    installed=state.get('installed',{})
    life.require(installed.get('master') is False
                 and not any(v in (True,'true') for v in installed.get('flags',{}).values()),
                 'G02 discriminator requires native Off and no public requests')


def witness(record,*,active):
    frames=record.get('frames',[])
    life.require(record.get('exactApi') is True and record.get('complete') is True and len(frames)==2
                 and all(f.get('candidate') is active and f.get('c6Candidate') is active
                         and len(f.get('generatedTextures',[]))==(0 if active else 2) for f in frames),
                 'Incomplete G02 native-only executed-mip witness')
