#!/usr/bin/env python3
"""Pinned BSL G09 exact reuse alternatives without the shared adapter runtime.

0 native;1 initial RGBA reuse;2 adds native fully-faded early return;
3 also reuses a precomputed shadow height only at equal finite sample coords;
4 shares validity checks outside the loop and reuses both pre-loop samples.
No traversal shortening, quality/AA/profile changes or new passes/targets.
"""
import argparse,hashlib,json,zipfile
from pathlib import Path,PurePosixPath
from build_makeup_unused_sky import archive_bytes,once,sha
NATIVE='36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5'
OPTION='SHADERLOD_G09_NATIVE_REUSE';SETTINGS='shaders/lib/settings.glsl';KERNEL='shaders/lib/surface/parallax.glsl';PROPS='shaders/shaders.properties';TERRAIN='shaders/program/gbuffers_terrain.glsl'
BASE='defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined OVERWORLD && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
def active(level):return f'{OPTION} >= {level} && {OPTION} <= 4 && {BASE}'
def read_archive(path):
 raw=Path(path).read_bytes()
 if sha(raw)!=NATIVE:raise ValueError('Exact native BSL10.1.8 required')
 with zipfile.ZipFile(path) as z:
  names=z.namelist()
  if len(names)!=len(set(names)) or any(PurePosixPath(n).is_absolute() or '..' in PurePosixPath(n).parts or '\\' in n for n in names):raise ValueError('Unsafe native archive')
  return {n:z.read(n) for n in names if not n.endswith('/')}
def patch(native,cue=False):
 files=dict(native);files[SETTINGS]+=f'\n// Private G09 native reuse; native quality and AA retained.\n#define {OPTION} 0 // [0 1 2 3 4]\n'.encode()
 s=native[KERNEL].decode().replace('\r\n','\n')
 first='    vec3 normalMap = ReadNormal(coord).xyz * 2.0 - 1.0;'
 s=once(s,first,f'''    #if {active(1)}
    vec4 shaderlodG09Initial = ReadNormal(coord);
    vec3 normalMap = shaderlodG09Initial.xyz * 2.0 - 1.0;
    #else
{first}
    #endif''')
 depth='    float depth = ReadNormal(coord).a;';s=once(s,depth,f'''    #if {active(1)}
    float depth = shaderlodG09Initial.a;
    #else
{depth}
    #endif''')
 early='''    if (viewVector != viewVector) {
        return texCoord;
    }
'''
 s=once(s,early,early+f'''    #if {active(2)}
    // Native always returns texCoord/surfaceDepth1 when fade reaches one.
    // This avoids the now-unconsumed first normal lookup and setup ALU.
    if (parallaxFade >= 1.0) {{ {'shaderlodG09Faded = 1.0;' if cue else ''} return texCoord; }}
    #endif
''')
 final='    if (parallaxFade >= 1.0 || normalCheck < -1.999) return texCoord;'
 s=once(s,final,f'''    #if {active(2)}
    if (normalCheck < -1.999) return texCoord;
    #else
{final}
    #endif''')
 loop='    for(int i = 0; i < SELF_SHADOW_QUALITY; i++) {'
 s=once(s,loop,f'''    #if {active(4)}
    // These native explicit gradients are unchanged throughout this loop.
    bool shaderlodG09FiniteGrad = all(lessThanEqual(abs(dcdx), vec2(16.0)))
        && all(lessThanEqual(abs(dcdy), vec2(16.0)));
    bool shaderlodG09CoordValid = shaderlodG09FiniteGrad &&
        all(lessThanEqual(abs(coord), vec2(1.0)));
    bool shaderlodG09OffsetValid = shaderlodG09FiniteGrad &&
        all(lessThanEqual(abs(ptexCoord), vec2(1.0)));
    #endif
{loop}''')
 fetch='        float offsetHeight = texture2DGradARB(normals, parallaxCoord, dcdx, dcdy).a;'
 s=once(s,fetch,f'''        #if {active(3)}
        // Reuse only an actually equal, finite in-atlas coordinate, with finite
        // explicit gradients. Other samples and unordered values stay native.
        #if {OPTION} == 4
        bool shaderlodG09ReuseBase = i == 0 && shaderlodG09CoordValid && all(equal(parallaxCoord, coord));
        bool shaderlodG09Reuse = i == 1 && shaderlodG09OffsetValid && all(equal(parallaxCoord, ptexCoord));
        #else
        bool shaderlodG09Reuse = i == 1 && all(equal(parallaxCoord, ptexCoord))
            && all(lessThanEqual(abs(ptexCoord), vec2(1.0)))
            && all(lessThanEqual(abs(dcdx), vec2(16.0)))
            && all(lessThanEqual(abs(dcdy), vec2(16.0)));
        #endif
        float offsetHeight;
        #if {OPTION} == 4
        if (shaderlodG09ReuseBase) {{ offsetHeight = texHeight; {'shaderlodG09ShadowReuse += 1.0;' if cue else ''} }}
        else
        #endif
        if (shaderlodG09Reuse) {{ offsetHeight = texHeightOffset; {'shaderlodG09ShadowReuse += 1.0;' if cue else ''} }}
        else offsetHeight = texture2DGradARB(normals, parallaxCoord, dcdx, dcdy).a;
        #else
{fetch}
        #endif''')
 if cue:
  s=f'#if {active(1)}\nfloat shaderlodG09Faded = 0.0;\nfloat shaderlodG09ShadowReuse = 0.0;\n#endif\n'+s
  terrain=native[TERRAIN].decode().replace('\r\n','\n');anchor='    gl_FragData[0] = albedo;'
  paint=f'''    #if defined PARALLAX && defined ADVANCED_MATERIALS && {active(1)}
    // Untimed: green fully-faded lookup omitted; cyan shadow sample reused;
    // magenta neither. Source-branch footprint, not hardware fetch count.
    albedo.rgb = shaderlodG09Faded > 0.5 ? vec3(0.0,1.0,0.0) :
        (shaderlodG09ShadowReuse > 0.0 ? vec3(0.0,1.0,1.0) : vec3(1.0,0.0,1.0));
    #endif
'''
  files[TERRAIN]=once(terrain,anchor,paint+anchor).encode()
 files[KERNEL]=s.encode();files[PROPS]=once(native[PROPS].decode(),'screen=','screen=[SHADERLOD_G09] ').encode()+f'\nscreen.SHADERLOD_G09={OPTION}\n'.encode();return files

def build(original,options,output,cue=False):
 native=read_archive(original);files=patch(native,cue);side=Path(options).read_bytes()
 if b'SHADERLOD_' in side:raise ValueError('Native-only option sidecar required')
 output=Path(output);paths=[output,Path(str(output)+'.txt'),output.with_suffix('.json')]
 if any(p.exists() for p in paths):raise ValueError('Fresh output required')
 meta={'id':'G09','variant':'native-reuse-r2','base_sha256':NATIVE,'option':OPTION,'default_off':True,'cue':cue,'changed_members':[n for n in native if files[n]!=native[n]],'scope':'Private BSL10.1.8 root Mac MC1.21.1/Iris1.8.12 world0 native384/PBR options/TAA retained; DH/Voxy/otherdimension fallback. No installed normal Apply support claim.','mechanisms':['1 initial normal/depth share one native explicit-gradient RGBA fetch','2 fully-faded native return moved before now-unconsumed normal lookup/setup','3 preloop shadow sample reused only for actual equal finite normalized coordinate and finite explicit gradients at loop i1','4 both preloop samples reused only at actual equal i0/i1 coordinates; shared gradient validity outside loop'],'limits':'Branches/guards/divergence/all other passes count. Native compiler may already eliminate repeats. Cue is logical source activity only. Runtime untested.'}
 files['SHADERLOD_G09_NATIVE_REUSE_NOTICE.txt']=('Private BSL10.1.8 derivative. All original licenses and Capt Tatsu attribution retained; original BSL license governs derivative.\n'+json.dumps(meta,indent=2)+'\n').encode()
 data=archive_bytes(files);output.parent.mkdir(parents=True,exist_ok=True);output.write_bytes(data);paths[1].write_bytes(side+(b'' if side.endswith(b'\n') else b'\n')+f'{OPTION}=0\n'.encode());meta.update(sha256=sha(data),native_sidecar_sha256=sha(side));paths[2].write_text(json.dumps(meta,indent=2)+'\n');return meta
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--cue',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))
