#!/usr/bin/env python3
"""Private G08 final-only discriminator; literal native function/name first."""
import argparse,json,tempfile,zipfile
from pathlib import Path
import build_makeup_cloud_four_groups as group
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha

def build(original,options,output,control='forcedfalse',audit=False):
    output=Path(output)
    if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
    with tempfile.TemporaryDirectory() as t:
        p=Path(t)/'base.zip';group.build(original,options,p,control)
        with zipfile.ZipFile(p) as z:f={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    native=read_archive(original);cloud=group.m.CLOUD
    s=f[cloud].decode();start=s.index(native[cloud].decode().replace('vec3 get_cloud(','vec3 shaderlodG08GroupReferenceCloud(',1))
    candidate=s[:start];candidate=candidate.replace('vec3 get_cloud(','vec3 shaderlodG08CandidateCloud(',1)
    # The reference is the literal original source, including function name.
    candidate=candidate.replace('defined MC_OS_MAC','defined MC_OS_LINUX')
    if not audit:
        for line in ['shaderlodG08GroupCalled=1.0;',
                     '                    shaderlodG08GroupAttempts+=1.0;',
                     '                    if(shaderlodG08GroupEmpty)shaderlodG08GroupPotential+=1.0;',
                     '            if(shaderlodG08GroupEmpty && !(current_opacity==0.0))shaderlodG08GroupBadSteps+=1.0;']:
            
            if control!='clone' or line=='shaderlodG08GroupCalled=1.0;':candidate=once(candidate,line,'')
        candidate=once(candidate,'float shaderlodG08GroupAttempts=0.0,shaderlodG08GroupPotential=0.0,shaderlodG08GroupBadSteps=0.0,shaderlodG08GroupMismatch=0.0;', 'float shaderlodG08GroupMismatch=0.0;')
    # Group source leaves a #if open immediately before the old reference.
    gate=group.GATE.replace('defined MC_OS_MAC','defined MC_OS_LINUX')
    assert candidate.endswith('\n#if '+gate+'\n');candidate=candidate[:-len('\n#if '+gate+'\n')]
    # Define native before candidate: no native control-flow/loop edits.
    f[cloud]=(native[cloud].decode()+'\n'+candidate).encode()
    caller='shaders/common/deferred_fragment.glsl';s=f[caller].decode().replace('defined MC_OS_MAC','defined MC_OS_LINUX')
    s=s.replace('shaderlodG08GroupReferenceCloud(','get_cloud(')
    s=once(s,'vec3 shaderlodGroupCandidate=get_cloud(','vec3 shaderlodGroupCandidate=shaderlodG08CandidateCloud(')
    if not audit:
        s=once(s,'shaderlodG08GroupMismatch=any(notEqual(shaderlodGroupNative,shaderlodGroupCandidate))?1.0:0.0;', 'shaderlodG08GroupCalled=1.0;\n                shaderlodG08GroupMismatch=any(notEqual(shaderlodGroupNative,shaderlodGroupCandidate))?1.0:0.0;')
        expr='shaderlodG08GroupCalled>0.0?1.0+shaderlodG08GroupAttempts+5.0*shaderlodG08GroupPotential+25.0*shaderlodG08GroupBadSteps+425.0*shaderlodG08GroupMismatch:0.0'
        s=once(s,expr,'shaderlodG08GroupCalled>0.0?1.0+256.0*shaderlodG08GroupMismatch:0.0')
    f[caller]=s.encode()
    f['shaders/shaders.properties']=f['shaders/shaders.properties'].replace(b'defined MC_OS_MAC',b'defined MC_OS_LINUX')
    marker=json.loads(f['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']);marker.update(final_only=not audit,native_name_layout=True,observation_boundary='Two complete cloud kernels, native RGB feeds renderer, one extra R16F target. No lean output qualification; final-only has no candidate loop counter/stores. Audit is separate compiler layout.',encoding=marker['encoding'] if audit else '0 no cloud call;1 exact returned RGB;257 unequal/unordered returned RGB')
    f['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode()
    f['native-layout-source/build_g08_native_layout.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(f));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{group.OPTION}=1\n'.encode())
    return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()),audit=audit)

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
    p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='forcedfalse');p.add_argument('--audit',action='store_true')
    print(json.dumps(build(**vars(p.parse_args())),indent=2))
