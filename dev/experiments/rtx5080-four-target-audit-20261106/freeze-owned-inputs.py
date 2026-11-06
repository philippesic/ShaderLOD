"""Preserve stopped native campaign inputs before replacing runtime source.

Artifact preservation only; no benchmark launch, configuration reset or deletion.
Run with the owned native Python environment so exact process proofs are available.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zipfile

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import dev_client as dev
import experiment_lifecycle as life
import run_storage as storage


def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--tag',required=True)
    p.add_argument('--report',nargs='+',required=True)
    a=p.parse_args()
    life.require(os.name=='nt','Use the actual native Windows allocation')
    life.require(re.fullmatch('[a-z0-9-]{4,100}',a.tag) is not None,'Safe unique archive tag required')
    reports=[]
    for name in a.report:
        life.require(re.fullmatch('[a-z0-9-]{4,120}',name) is not None,'Safe campaign report name required')
        reports.append(HERE/name)
    def stopped():
        table=dev.process_table()
        life.require(not dev.clients(table),'Client present; keep inputs frozen')
        for report in reports:
            op=json.loads((report/'operation.json').read_text())
            life.require(op['state'] in ('complete','failed-restored') and op.get('release_verified')
                         and op.get('configuration_restored') and op['iteration_profile'].get('reset_verified'),
                         'Operation is not fully released/restored: '+str(report))
            life.process_absent(op['processes'].get('controller'),table,dev.identity)
    stopped()
    binding=json.loads((reports[-1]/'build-binding.json').read_text())
    paths={storage.safe_path(ROOT/relative) for relative in binding}
    paths|={file for name in ('scripts','tests','benchmarks/campaigns')
            for file in (ROOT/name).rglob('*') if file.is_file() and '__pycache__' not in file.parts}
    paths|={file for file in HERE.iterdir() if file.is_file() and file.suffix in ('.py','.md','.ps1')}
    archive=storage.safe_path(ROOT.parent/'frozen'/(a.tag+'.zip'))
    life.require(not archive.exists(),'Fresh archive required; preserve previous snapshots')
    storage.admit(archive,sum(file.stat().st_size for file in paths)+4*1024**2)
    archive.parent.mkdir(exist_ok=True)
    manifest={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for file in sorted(paths):
            file=storage.safe_path(file)
            life.require(file.is_relative_to(ROOT) and file.is_file(),'Unexpected snapshot input')
            data=file.read_bytes();sha=hashlib.sha256(data).hexdigest();relative=str(file.relative_to(ROOT))
            life.require(relative not in binding or binding[relative]==sha,'Build-bound input differs: '+relative)
            name=file.relative_to(ROOT).as_posix();manifest[name]={'bytes':len(data),'sha256':sha}
            z.writestr(name,data)
        z.writestr('FROZEN-MANIFEST.json',json.dumps(manifest,indent=2))
    with zipfile.ZipFile(archive) as z:
        life.require(z.testzip() is None,'Archive CRC differs')
        for name,row in manifest.items():
            life.require(hashlib.sha256(z.read(name)).hexdigest()==row['sha256']
                         and digest(ROOT/name)==row['sha256'],'Snapshot/source changed: '+name)
    stopped()
    receipt=dict(archive=str(archive),bytes=archive.stat().st_size,sha256=digest(archive),
                 files=len(manifest),build_bindings=len(binding),verified=True,reports=a.report)
    life.atomic_json(HERE/(a.tag+'.json'),receipt)
    print(json.dumps(receipt))


if __name__=='__main__':main()
