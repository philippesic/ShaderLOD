#!/usr/bin/env python3
"""Untimed native12tap coordinates in one group; private Asahi admission only."""
from pathlib import Path
import argparse,json
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha,NATIVE
GUARD='defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined BLOOM && !defined DOF && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
def build(original,options,output,private_asahi=False):
 if not private_asahi: raise ValueError("Private Asahi census requires explicit admission")
 guard=GUARD.replace("defined MC_OS_MAC","defined MC_OS_LINUX")
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 f=read_archive(original);n='shaders/lib/bloom.glsl';s=f[n].decode();s=f'''#if {guard}
uniform int frameCounter;
vec2 shaderlodBloomCoordinates[12];
int shaderlodBloomGroup=0;
#endif
'''+s
 s=once(s,'    vec3 blurSample = vec3(0.0);',f'''    vec3 blurSample = vec3(0.0);
    #if {guard}
    shaderlodBloomGroup=0;
    for(int t=0;t<12;t++)shaderlodBloomCoordinates[t]=vec2(1234567.0);
    #endif''')
 anchor='''        blurSample += texture2DLod(image, coords + offset, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset, softLod).rgb;
        blurSample += texture2DLod(image, coords + offset2, softLod).rgb;
        blurSample += texture2DLod(image, coords - offset2, softLod).rgb;'''
 actual='''        vec2 censusA=coords+offset,censusB=coords-offset,censusC=coords+offset2,censusD=coords-offset2;
        blurSample += texture2DLod(image, censusA, softLod).rgb;
        blurSample += texture2DLod(image, censusB, softLod).rgb;
        blurSample += texture2DLod(image, censusC, softLod).rgb;
        blurSample += texture2DLod(image, censusD, softLod).rgb;
        for(int t=0;t<4;t++) {
            int local=i*4+t;
            if(local>=0 && local<12)shaderlodBloomCoordinates[local]=t==0?censusA:(t==1?censusB:(t==2?censusC:censusD));
        }'''
 s=once(s,anchor,f'        #if {guard}\n'+actual+'\n        #else\n'+anchor+'\n        #endif');f[n]=s.encode()
 n='shaders/common/composite1_fragment.glsl';s=f[n].decode();s=once(s,'#include "/lib/config.glsl"','#include "/lib/config.glsl"'+f'''\n#if {guard}
/*
'''+''.join(f'const int colortex{t}Format = RGBA32F;\nconst bool colortex{t}Clear = true;\nconst vec4 colortex{t}ClearColor=vec4(0.0,0.0,0.0,0.0);\n' for t in range(8,15))+'*/\n#endif\n')
 s=once(s,'    gl_FragData[0] = blockColor;','    gl_FragData[0] = blockColor;'+f'''\n    #if {guard}
    /* RENDERTARGETS: 1,8,9,10,11,12,13,14 */
    gl_FragData[1]=vec4(frameMod,ditherShift,softLod,float(frameCounter));
'''+''.join(f'    gl_FragData[{i+2}]=vec4(shaderlodBloomCoordinates[{i*2}],shaderlodBloomCoordinates[{i*2+1}]);\n' for i in range(6))+'    #endif\n');f[n]=s.encode();f['shaders/shaders.properties']+=''.join(f'\nblend.composite1.colortex{t}=off\n' for t in range(8,15)).encode()
 marker={'diagnostic_only':True,'kind':'G10-native12-coordinates','native':NATIVE,'encoding':'RT8 frameMod,ditherShift,softLod,frameCounter; RT9..14 all12 actual vec2 querycoords, group0. Exact before/after draw counter and sampler join required.','limits':'Native12query expressions and original12additions execute. Extra coordinate stores/MRT can change compilation. No output equivalence/performance or hardwaretransactions claim. Offline census only; no production per-pixel32way guard.'}
 f['SHADERLOD_BLOOM_COORDINATE_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();f['coordinate-source/build_makeup_bloom_coordinate_census.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(f));side=options.read_bytes();values=dict(line.split('=',1) for line in side.decode().splitlines() if line and not line.startswith('#'));assert len(values)==100 and values['BLOOM_SAMPLES']=='3.0' and values['BLOOM']=='true' and values['DOF']=='false' and values['AA_TYPE']=='2';Path(str(output)+'.txt').write_bytes(side);return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--private-asahi',action='store_true')
 print(json.dumps(build(**vars(p.parse_args())),indent=2))
