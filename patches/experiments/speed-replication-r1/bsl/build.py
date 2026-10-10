#!/usr/bin/env python3
"""Private pinned BSL temporal PCF candidate. No launch or production mutation."""
import hashlib, io, json, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
STAGE=ROOT/'dev/logs/speed-replication-r1/bsl/final'
ORIGINAL=ROOT/'dev/upstream/other-pack-research/BSL_v10.1.8.zip'
OPTIONS=ROOT/'dev/logs/cross-shader-performance-r3/bsl-original-pbr-gpu-budget-r2/expected-native-pbr384.json'
KEY='SHADERLOD_BSL_SPEED_R1'
SET='shaders/lib/settings.glsl'
SHADOW='shaders/lib/lighting/shadows.glsl'
TERRAIN='shaders/program/gbuffers_terrain.glsl'
PROP='shaders/shaders.properties'
sha=lambda b:hashlib.sha256(b).hexdigest()
GUARD='''
#if SHADERLOD_BSL_SPEED_R1 > 0 && SHADERLOD_BSL_SPEED_R1 <= 3 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined OVERWORLD && defined FSH && defined TAA && TAA_MODE == 0 && !defined TAA_SELECTIVE && defined SHADOW && defined SHADOW_FILTER && !defined DISTANT_HORIZONS && !defined VOXY && !defined WORLD_CURVATURE && !defined RETRO_FILTER
#define SHADERLOD_BSL_SPEED_SUPPORTED
#endif
'''
POLICY='''#if defined SHADERLOD_BSL_SPEED_SUPPORTED && defined SHADERLOD_BSL_SPEED_CONSUMER
uniform int shaderlodBridgeVersion, shaderlodEnabled;
uniform int shaderlodPreset = -1;
uniform float shaderlodNear, shaderlodFar;
bool shaderlodBslSpeedReduced = false;
bool shaderlodBslSpeedUseReduced(vec3 worldPos) {
    if (shaderlodBridgeVersion != 2 || shaderlodEnabled != 1 || shaderlodPreset != 1) return false;
    if (!(shaderlodNear > 0.0 && shaderlodFar > shaderlodNear && shaderlodFar <= 4096.0)) return false;
    float receiverDistance = length(worldPos);
    if (!(receiverDistance > shaderlodNear && receiverDistance < 65536.0)) return false;
    float weight = smoothstep(shaderlodNear, shaderlodFar, receiverDistance);
    // Native Bayer8 coordinates; no added random seed/history. Never evaluate both kernels.
    return weight > Bayer8(gl_FragCoord.xy);
}
#endif
'''
def once(s,a,b):
    if s.count(a)!=1: raise ValueError('Ambiguous source anchor: '+a[:60])
    return s.replace(a,b)
def files(p):
    with zipfile.ZipFile(p) as z:
        if len(z.namelist())!=len(set(z.namelist())):raise ValueError('Duplicate archive member')
        return {n:z.read(n) for n in z.namelist()}
def patch(f,mode):
    c=f.copy()
    s=f[SET].decode().replace('\r\n','\n')
    c[SET]=(f'#define {KEY} {mode} // [0 1 2 3]\n'+s+GUARD).replace('\n','\r\n').encode()
    t=f[TERRAIN].decode().replace('\r\n','\n')
    t=once(t,'#include "/lib/lighting/forwardLighting.glsl"','#define SHADERLOD_BSL_SPEED_CONSUMER\n#include "/lib/lighting/forwardLighting.glsl"')
    t=once(t,'    gl_FragData[0] = albedo;','''    #if defined SHADERLOD_BSL_SPEED_SUPPORTED && SHADERLOD_BSL_SPEED_R1 == 3
    if (shaderlodBslSpeedReduced) albedo.rgb = vec3(1.0, 0.0, 1.0);
    #endif
    gl_FragData[0] = albedo;''')
    c[TERRAIN]=t.replace('\n','\r\n').encode()
    s=f[SHADOW].decode().replace('\r\n','\n')
    start=s.index('vec3 SampleFilteredShadow(');end=s.index('\nvec3 GetShadow(',start)
    native=s[start:end]
    coarse=native.replace('vec3 SampleFilteredShadow(', 'vec3 shaderlodBslSpeedFiltered3(',1)
    coarse=coarse.replace('for (int i = 0; i < 9; i++) {','for (int visit = 0; visit < 3; visit++) {\n        int i = int(mod(float(frameCounter), 3.0)) + visit * 3;')
    assert coarse.count('int i = int(mod(float(frameCounter), 3.0)) + visit * 3;')==2
    coarse=coarse.replace('shadow0 /= 9.0;', 'shadow0 /= 3.0;').replace('shadowCol /= 9.0;', 'shadowCol /= 3.0;')
    s=s[:start]+POLICY+s[start:end]+'\n#if defined SHADERLOD_BSL_SPEED_SUPPORTED && defined SHADERLOD_BSL_SPEED_CONSUMER\n'+coarse+'\n#endif\n'+s[end:]
    a='''    vec3 shadow = SampleFilteredShadow(shadowPos, offset, subsurface);'''
    b='''    #if defined SHADERLOD_BSL_SPEED_SUPPORTED && defined SHADERLOD_BSL_SPEED_CONSUMER
    vec3 shadow;
    bool reduceKernel = shaderlodBslSpeedUseReduced(worldPos);
    #if SHADERLOD_BSL_SPEED_R1 == 2 || SHADERLOD_BSL_SPEED_R1 == 3
    if (reduceKernel) {
        shadow = shaderlodBslSpeedFiltered3(shadowPos, offset, subsurface);
        shaderlodBslSpeedReduced = true;
    } else
    #endif
    { shadow = SampleFilteredShadow(shadowPos, offset, subsurface); }
    #else
    vec3 shadow = SampleFilteredShadow(shadowPos, offset, subsurface);
    #endif'''
    s=once(s,a,b)
    c[SHADOW]=s.replace('\n','\r\n').encode()
    c[PROP]+=f'\r\nscreen.SHADERLOD_BSL_SPEED = {KEY}\r\n'.encode()
    c['SHADERLOD_PRIVATE_SPEED_R1.txt']=b'Private BSL10.1.8 derivative by Capt Tatsu https://capttatsu.com. All Rights Reserved ancestry retained. Local experiment; no redistribution grant. Temporal PCF approximation; not a measured gain.\n'
    return c

def main():
    if sha(ORIGINAL.read_bytes())!='36b0a50ff7918bf10e9422c401d93779f9d0088a26acea975b435243f96930b5':raise ValueError('Original pin mismatch')
    f=files(ORIGINAL);native=json.loads(OPTIONS.read_text());assert len(native)==384
    STAGE.mkdir(parents=True,exist_ok=True)
    manifest={'original_sha256':sha(ORIGINAL.read_bytes()),'original_members':{n:sha(v) for n,v in f.items()},'inputs':{str(p):sha(p.read_bytes()) for p in [ORIGINAL,OPTIONS,Path(__file__),ROOT/'src/main/java/com/pp/shaderlod/mixin/CommonUniformsMixin.java',ROOT/'src/main/java/com/pp/shaderlod/LodPreset.java']},'arms':{},'source_only':True,'runtime':'UNTESTED'}
    for arm,mode in [('original',None),('disabled',0),('control',1),('candidate',2),('cue',3)]:
        p=STAGE/f'BSL-speed-replication-r1-{arm}-private.zip'
        if p.exists():raise ValueError('Refusing artifact overwrite: '+str(p))
        c=f if mode is None else patch(f,mode)
        if mode is None:data=ORIGINAL.read_bytes()
        else:
            out=io.BytesIO()
            with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
                for n,v in sorted(c.items()):
                    info=zipfile.ZipInfo(n,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,v)
            data=out.getvalue()
        p.write_bytes(data)
        opts=native.copy()
        if mode is not None:opts[KEY]=str(mode)
        Path(str(p)+'.txt').write_text(''.join(f'{k}={v}\n' for k,v in sorted(opts.items())))
        (STAGE/f'{arm}-options.json').write_text(json.dumps(opts,indent=2,sort_keys=True)+'\n')
        manifest['arms'][arm]={'path':str(p),'sha256':sha(data),'sidecar_sha256':sha(Path(str(p)+'.txt').read_bytes()),'members':{n:sha(v) for n,v in c.items()},'changed_members':[n for n in f if f[n]!=c[n]],'native_options':len(native),'total_options':len(opts)}
    (STAGE/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({a:v['sha256'] for a,v in manifest['arms'].items()},indent=2))
if __name__=='__main__':main()
