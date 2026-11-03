#!/usr/bin/env python3
"""Pinned, query-free PNG diagnostic. Never starts or modifies a client."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import tempfile
import zipfile
import build_photon_contribution_tail as tail

ROOT=tail.ROOT
MANIFEST=Path('patches/photon-experiments/contribution-tail-diagnostic.json')
SWITCH='SHADERLOD_CONTRIBUTION_TAIL_DIAGNOSTIC'
METADATA='shaderlod-contribution-tail-diagnostic.json'
PREFIX='shaderpacks/photon-contribution-tail-diagnostic/'
MEMBERS=(tail.SETTINGS,'shaders/program/d1_clouds.fsh','shaders/program/final.fsh',tail.CUMULUS,tail.HELPER)
ADDITION='shaders/include/shaderlod/tail_png_final.glsl'

def assemble(arm='disabled', root=ROOT):
    if arm not in ('disabled','enabled'): raise ValueError('Unknown diagnostic arm')
    m=json.loads((root/MANIFEST).read_text())
    if (m['schema']!=1 or m['experiment']!='photon-contribution-tail-png-diagnostic' or
        tuple(e['path'] for e in m['members'])!=MEMBERS or
        tuple(e['path'] for e in m['additions'])!=(ADDITION,) or
        m['protocol']['source_tag']!='c7a14d32'): raise ValueError('Unknown manifest')
    base=root/tail.BASE
    if tail.sha256(base.read_bytes())!=tail.BASE_SHA256: raise ValueError('Upstream archive changed')
    tm_path=root/tail.MANIFEST
    if tail.sha256(tm_path.read_bytes())!=m['normal_manifest_sha256']: raise ValueError('Normal prototype changed')
    normal=tail.patch_members(tail.archive_members(base),json.loads(tm_path.read_text()),'enabled',root)
    result=normal.copy()
    for entry in m['members']:
        name=entry['path']; data=normal[name]
        if tail.sha256(data)!=entry['source_sha256']: raise ValueError('Changed normal source '+name)
        s=data.decode()
        for r in entry['replacements']:
            if s.count(r['before'])!=1: raise ValueError('Ambiguous source anchor '+name)
            s=s.replace(r['before'],r['after'],1)
        data=s.encode()
        if tail.sha256(data)!=entry['patched_sha256']: raise ValueError('Changed diagnostic source '+name)
        if name==tail.SETTINGS and arm=='enabled': data=data.replace(('//#define '+SWITCH+'\n').encode(),('#define '+SWITCH+'\n').encode(),1)
        if tail.sha256(data)!=entry['enabled_sha256' if name==tail.SETTINGS and arm=='enabled' else 'patched_sha256']: raise ValueError('Changed enabled source')
        result[name]=data
    for e in m['additions']:
        data=(root/PREFIX/e['path']).read_bytes()
        if e['path'] in normal or tail.sha256(data)!=e['sha256']: raise ValueError('Changed diagnostic helper')
        result[e['path']]=data
    metadata={'schema':1,'experiment':m['experiment'],'arm':arm,'base_sha256':tail.BASE_SHA256,
      'normal_manifest_sha256':m['normal_manifest_sha256'],'manifest_sha256':tail.sha256((root/MANIFEST).read_bytes()),
      'member_sha256':{n:tail.sha256(v) for n,v in sorted(result.items())},
      'protocol':m['protocol'],'attribution':'Photon v1.3b by Benjamin Stott (SixthSurge); complete LICENSE preserved',
      'runtime':'unverified; diagnostic image destroys cloud output and is never quality/performance evidence',
      'units':'integer executed-order and call counts; optical-depth/distance units unchanged',
      'fallback':'diagnostic OFF recovers exact normal enabled prototype; normal private tail OFF recovers original',
      'transition':'normal 4/8-order eligibility unchanged; no quality acceptance'}
    result[METADATA]=(json.dumps(metadata,indent=2,sort_keys=True)+'\n').encode()
    return result,metadata

def build(output=None,arm='disabled',root=ROOT):
    output=(output or root/'build'/f'contribution-tail-diagnostic-{arm}.zip').resolve()
    if not output.is_relative_to((root/'build').resolve()) or not output.name.startswith('contribution-tail-diagnostic-') or output.suffix!='.zip': raise ValueError('Output must be build/contribution-tail-diagnostic-*.zip')
    files,metadata=assemble(arm,root); output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=output.parent,suffix='.zip',delete=False) as f: temp=Path(f.name)
    try:
        with zipfile.ZipFile(temp,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for n,data in sorted(files.items()):
                info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.create_system=3;info.external_attr=0o100644<<16
                z.writestr(info,data,compresslevel=9)
        temp.replace(output)
    finally:temp.unlink(missing_ok=True)
    return metadata

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--arm',choices=('disabled','enabled'),default='disabled');p.add_argument('--output',type=Path);a=p.parse_args()
    r=build(a.output,a.arm);print(json.dumps({k:v for k,v in r.items() if k!='member_sha256'},indent=2))
