#!/usr/bin/env python3
"""Untimed RT8 integer work images for pinned G08/G10 fullscreen variants."""
import argparse,json,zipfile
from pathlib import Path
from build_makeup_unused_sky import archive_bytes,once,sha
from build_makeup_directional_cloud_bounds import GATE as CLOUD_GATE
from build_makeup_bloom_pairing import GUARD as BLOOM_GATE
NATIVE='0036da17e0c7158e1c669593e6322d53fce3f5a42fb5124b935e9e21a5719db8'
FORMAT='''
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
*/
'''

def build(source,output,kind):
    source,output=Path(source),Path(output)
    if any(p.exists() for p in [output,Path(str(output)+'.txt')]):raise ValueError('Fresh output required')
    with zipfile.ZipFile(source) as z:
        if len(z.namelist())!=len(set(z.namelist())) or sum(e.file_size for e in z.infolist())>32*1024*1024:raise ValueError('Bounded unique input required')
        files={n:z.read(n) for n in z.namelist()}
    if kind=='G08-cloud':
        notice='SHADERLOD_G08_DIRECTIONAL_NOTICE.txt';caller='shaders/common/deferred_fragment.glsl';guard=f'({CLOUD_GATE}) && SHADERLOD_G08_DIRECTIONAL_DEBUG == 1'
        target='14';newtarget='148';index=2;value='shaderlodG08Eligible > 0.0 ? 1.0 + shaderlodG08SavedSteps : 0.0';units='Native cloud noise steps omitted; two native noise queries/step. Candidate pays two atlas queries per admitted checkpoint; unused bounds-only queries may be compiler-eliminated.'
        if b'"zero_guard": true' in files[notice]:units='Native zero-density geometry/thickness/opacity iteration bodies omitted. All native noise queries and position updates retained; no atlas queries. Not a texture fetch saving.'
    elif kind=='G10-bloom':
        notice='shaderlod-g10-build.json';caller='shaders/common/composite1_fragment.glsl';guard=f'defined BLOOM && ({BLOOM_GATE}) && (SHADERLOD_G10_BLOOM_PAIRING == 2 || SHADERLOD_G10_BLOOM_PAIRING == 4)'
        target='1';newtarget='18';index=1;value='1.0 + float(shaderlodG10Pairs) * (SHADERLOD_G10_BLOOM_PAIRING == 4 ? 3.0 : 2.0)';units='Native source texture query expressions omitted. Compiler may already CSE duplicate queries: not hardware transactions.'
    else:raise ValueError('Unsupported pinned diagnostic')
    if NATIVE.encode() not in files[notice]:raise ValueError('Pinned native ancestry required')
    text=files[caller].decode();text=once(text,'#include "/lib/config.glsl"','#include "/lib/config.glsl"\n#if '+guard+'\n'+FORMAT+'#endif\n')
    text=once(text,'/* DRAWBUFFERS:'+target+' */','#if '+guard+'\n    /* DRAWBUFFERS:'+newtarget+' */\n    #else\n    /* DRAWBUFFERS:'+target+' */\n    #endif')
    end='    gl_FragData['+str(index-1)+'] = blockColor;'
    text=once(text,end,end+'\n    #if '+guard+'\n    gl_FragData['+str(index)+'] = vec4('+value+');\n    #endif')
    files[caller]=text.encode();program='deferred' if kind=='G08-cloud' else 'composite1';files['shaders/shaders.properties']+=('\nblend.'+program+'.colortex8=off\n').encode()
    marker={'diagnostic_only':True,'native':NATIVE,'kind':kind,'source_sha256':sha(source.read_bytes()),'encoding':'0 background,1 eligible,1+omitted source units','units':units,'limits':'Separate extra R16F attachment and CPU readback excluded from timing/quality. Banks separate; use actual FBO/source/sampler joins.'}
    if kind=='G08-cloud' and 'G08-parity.json' in files:
        marker.update(encoding_mode='predicate-bits',encoding='0 noncloud pixel,1 exact same-fragment native/candidate cloud RGB equality,257 any unequal or NaN component. Not source omission units.')
        parity=json.loads(files['G08-parity.json'])
        if parity.get('error_bins'):marker['encoding']=parity['encoding']
    files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['fullscreen-work-source/build_makeup_fullscreen_work.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(str(source)+'.txt').read_bytes());return dict(marker,sha256=sha(output.read_bytes()),artifact=str(output))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--kind',required=True,choices=['G08-cloud','G10-bloom']);print(json.dumps(build(**vars(p.parse_args())),indent=2))
