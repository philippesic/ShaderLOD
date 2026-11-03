#!/usr/bin/env python3
"""Pinned private G01: one-pixel native R16F history and R32F current transfer.

A 1x1 producer computes the exact native exposure before native composite0,
writing its unrounded FP32 value and normal rounded R16F history. Composite0
reads the FP32 transfer for current color. No RT1 flip or mip-count changes.
A separate untimed build retains independent native RT6 history/arithmetic for
same-frame comparison. All runtime economics/correctness remain experimental.
"""
import argparse
import json
from pathlib import Path
from build_makeup_unused_sky import read_archive, archive_bytes, once, sha, NATIVE

SCALAR='SHADERLOD_G01_SCALAR_EXPOSURE'
COPIES='SHADERLOD_G01_COPY_FLIPS'
OPTIONS='shaders/lib/g01/options.glsl'
PROPS='shaders/shaders.properties'
BASE=f'defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
ACTIVE=f'{SCALAR} >= 1 && {SCALAR} <= 2 && {BASE}'
COPY_ACTIVE=f'{COPIES} >= 1 && {COPIES} <= 2 && {BASE}'

def route(native,on):
    # Each branch keeps its own native program macros before including config.
    # New options live in a separate header: native config contains GLSL consts
    # and cannot safely be included twice just to decide a route.
    def body(s):
        lines=s.decode().splitlines(keepends=True)
        return ''.join(l for l in lines if not l.startswith(('#version','#extension')))
    return ('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'
            '#include "/lib/g01/options.glsl"\n#if '+ACTIVE+'\n'+body(on)+
            '\n#else\n'+body(native)+'\n#endif\n').encode()

def patch(native,diagnostic=False,platform='mac'):
    if platform not in ('mac','linux'):
        raise ValueError('Private scalar target must be mac or linux')
    files=dict(native)
    files[OPTIONS]=(f'// Private G01 exact exposure scheduling and dead-copy controls.\n#define {SCALAR} 0 // [0 1 2]\n#define {COPIES} 0 // [0 1 2]\n').encode()
    def clone(name,text):
        out='shaders/lib/g01/'+name
        if out in files:raise ValueError('Private source collision')
        files[out]=text.encode();return '/lib/g01/'+name
    def wrapper(common,macros='#define COMPOSITE_SHADER'):
        return ('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+macros+'\n#include "'+common+'"\n').encode()
    # Before native composite0: preserve exact native arithmetic, same original
    # RT1 mip input and previous rounded history. Both outputs have one texel.
    producer_v=clone('history_vertex.glsl',native['shaders/common/composite_vertex.glsl'].decode().replace('gaux3','colortex8'))
    producer_f=clone('history_fragment.glsl','''#include "/lib/config.glsl"
/*
const int colortex8Format = R16F;
*/
/*
const int colortex9Format = R32F;
*/
const bool colortex8Clear = false;
const bool colortex9Clear = false;
const bool colortex8Nearest = true;
const bool colortex9Nearest = true;
const bool colortex1MipmapEnabled = true;
varying float exposure;
void main() {
    /* RENDERTARGETS: 8,9 */
    gl_FragData[0] = vec4(exposure, 0.0, 0.0, 0.0);
    gl_FragData[1] = vec4(exposure, 0.0, 0.0, 0.0);
}
''')
    for suffix,producer in [('vsh',producer_v),('fsh',producer_f)]:
        n='shaders/world0/composite.'+suffix;files[n]=route(native[n],wrapper(producer))
    v=native['shaders/common/composite_vertex.glsl'].decode()
    if not diagnostic:
        start=v.index('    // Exposure\n');end=v.index('    #if (VOL_LIGHT',start)
        v=v[:start]+'    // Unrounded FP32 transfer; the history remains native R16F.\n    exposure = texture2D(colortex9, vec2(0.5)).r;\n\n'+v[end:]
        v=v.replace('uniform sampler2D gaux3;','uniform sampler2D colortex9;')
    vertex=clone('composite_vertex.glsl',v)
    f=native['shaders/common/composite_fragment.glsl'].decode()
    f=once(f,'const bool colortex1MipmapEnabled = true;\n','') # producer already generated this exact preimage
    if diagnostic:
        # Independent native previous/current R16F history remains RT6. The
        # original native vertex expression is untouched; RT10 keeps unrounded
        # output for comparison with the earlier producer's actual FP32 store.
        f=once(f,'/* DRAWBUFFERS:016 */','/* RENDERTARGETS: 0,1,6,10 */')
        f=once(f,'/* DRAWBUFFERS:16 */','/* RENDERTARGETS: 1,6,10 */')
        f=once(f,'        gl_FragData[2] = vec4(exposure, 0.0, 0.0, 0.0);','        gl_FragData[2] = vec4(exposure, 0.0, 0.0, 0.0);\n        gl_FragData[3] = vec4(exposure,0.0,0.0,0.0);')
        f=once(f,'        gl_FragData[1] = vec4(exposure, 0.0, 0.0, 0.0);','        gl_FragData[1] = vec4(exposure, 0.0, 0.0, 0.0);\n        gl_FragData[2] = vec4(exposure,0.0,0.0,0.0);')
        f+='\n/*\nconst int colortex6Format = R16F;\n*/\n/*\nconst int colortex10Format = R32F;\n*/\nconst bool colortex10Clear = false;\n'
        files['SHADERLOD_G01_EXPOSURE_DIAGNOSTIC.json']=json.dumps({'native':NATIVE,'diagnostic_only':True,'rt6':'independent native full-size R16F history','rt8':'1x1 R16F history','rt9':'1x1 R32F unrounded transfer','rt10':'independent native unrounded exposure, full-size R32F'}).encode()
    else:
        f=once(f,'/* DRAWBUFFERS:016 */','/* DRAWBUFFERS:01 */')
        f=once(f,'/* DRAWBUFFERS:16 */','/* DRAWBUFFERS:1 */')
        f=once(f,'        gl_FragData[2] = vec4(exposure, 0.0, 0.0, 0.0);\n','')
        f=once(f,'        gl_FragData[1] = vec4(exposure, 0.0, 0.0, 0.0);\n','')
    fragment=clone('composite_fragment.glsl',f)
    for suffix,common in [('vsh',vertex),('fsh',fragment)]:
        n='shaders/world0/composite1.'+suffix;files[n]=route(native[n],wrapper(common))
        n2='shaders/world0/composite2.'+suffix;files[n2]=route(native[n2],native[n])
        n3='shaders/world0/composite3.'+suffix
        if n3 in files:raise ValueError('Native composite3 occupied')
        files[n3]=native[n2]
    for stem,stage in [('final','vertex'),('final','fragment'),('glint_blocks','vertex')]:
        old='/common/'+stem+'_'+stage+'.glsl'
        common=clone(stem+'_'+stage+'.glsl',native['shaders'+old].decode().replace('uniform sampler2D gaux3;','uniform sampler2D colortex8;').replace('texture2D(gaux3,','texture2D(colortex8,'))
        suffix='vsh' if stage=='vertex' else 'fsh'
        targets=[n for n in native if n.startswith('shaders/world0/') and n.endswith('.'+suffix) and ('"'+old+'"').encode() in native[n]]
        if len(targets)!=1:raise ValueError('Native exposure consumer census changed')
        n=targets[0];files[n]=route(native[n],native[n].replace(old.encode(),common.encode()))
    files[PROPS]+=f'''
# Private G01. Global properties receive dimension-local option macros too.
# Program activation is world0-only. Other dimensions retain original RT1 flips.
#if {ACTIVE}
program.world0/composite3.enabled=true
size.buffer.colortex8=1 1
size.buffer.colortex9=1 1
#if {SCALAR} == 2
# Non-history FP32 transfer is dead after the native composite consumer.
flip.composite1.colortex9=true
#endif
#if {COPY_ACTIVE}
flip.composite3.colortex0=true
flip.composite3.colortex7=true
#if {COPIES} == 2
# RT4 is overwritten by deferred before its only translucent consumers.
# The late flip follows all consumers and cancels its persistence copy.
flip.composite3.colortex4=true
#endif
#endif
#else
program.world0/composite3.enabled=false
#if {COPY_ACTIVE}
flip.composite2.colortex0=true
flip.composite2.colortex7=true
#if {COPIES} == 2
flip.composite2.colortex4=true
#endif
#endif
#endif
screen.SHADERLOD_G01 = {SCALAR} {COPIES}
'''.encode()
    files[PROPS]=once(files[PROPS].decode(),'screen = ','screen = [SHADERLOD_G01] ').encode()
    if platform == 'linux':
        # Retarget only this experiment's exact admission expression. Native
        # source and the maintained public recipe remain untouched.
        files = {name: data.replace(BASE.encode(), BASE.replace('MC_OS_MAC', 'MC_OS_LINUX').encode())
                 for name, data in files.items()}
    return files

def build(original,options,output,diagnostic=False,platform='mac'):
    native=read_archive(original);files=patch(native,diagnostic,platform);options=Path(options).read_bytes()
    if b'SHADERLOD_' in options:raise ValueError('Native sidecar required')
    output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
    if any(p.exists() for p in paths):raise ValueError('Fresh output required')
    meta={'id':'G01','variant':'scalar-exposure-fp32-transfer-r6-rt4','base_sha256':NATIVE,'default_off':True,'diagnostic_only':diagnostic,'changed_native_members':[n for n in native if files[n]!=native[n]],'added_members':[n for n in files if n not in native],'scope':'Private Mac MC1.21.1 Iris1.8.12 MakeUp9.5g world0, no DH/Voxy; runtime selected native100 AA2. Other dimensions route native.','mechanism':'Omit full-resolution RT6 MRT output and persistence copy; add 1x1 R16F history/R32F current-exposure producer before native composite0. RT1 flips and mip count stay native; original current color reads unrounded FP32, final/glint/history use R16F. Optional independent RT0/RT7 dead-copy flips, with mode2 also cancelling RT4 after its translucent consumers. Native RT3 TAA history stays unchanged.','unverified':['actual native RT1 preimage mip binding and same mip count','actual full-size global viewWidth/viewHeight in 1x1 producer','R16F cold-start/resize history and final/glint consumption','whole-frame net economics, motion, DH/PBR fallback, restoration']}
    meta['platform'] = platform
    if platform == 'linux':
        meta['scope'] = meta['scope'].replace('Private Mac', 'Private Linux')
    files['SHADERLOD_G01_SCALAR_NOTICE.txt']=('Private MakeUp Ultra Fast9.5g derivative, LGPL-3.0-or-later. Original licenses/ancestry retained. Additions LGPL-3.0-or-later.\n'+json.dumps(meta,indent=2)+'\n').encode()
    data=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(data);paths[1].write_bytes(options+(b'' if options.endswith(b'\n') else b'\n')+f'{SCALAR}=0\n{COPIES}=0\n'.encode());meta['sha256']=sha(data);meta['artifact']=str(output.resolve());paths[2].write_text(json.dumps(meta,indent=2)+'\n');return meta
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--diagnostic',action='store_true');p.add_argument('--platform',choices=('mac','linux'),default='mac');print(json.dumps(build(**vars(p.parse_args())),indent=2))
