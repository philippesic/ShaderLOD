#!/usr/bin/env python3
"""Pinned nearest-depth hierarchy with native recurrence and exact predecessor repair."""
from pathlib import Path
import argparse,json,re
from build_makeup_unused_sky import NATIVE,read_archive,archive_bytes,once,sha
OPTION='SHADERLOD_G05_DEPTH_HIERARCHY'
GUARD=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'

def build(original,options,output,diagnostic=False,control='skip',private_asahi=False,fixed16=False):
 if fixed16 and not private_asahi:raise ValueError('Fixed16 requires explicit private Asahi')
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 guard=GUARD.replace('defined MC_OS_MAC','defined MC_OS_LINUX') if private_asahi else GUARD
 files=read_archive(original);n='shaders/lib/water.glsl';s=files[n].decode();a=s.index('vec3 fastRaymarch(');z=s.index('\n#if SUN_REFLECTION',a);native=s[a:z];candidate=native
 setup='''    bool shaderlodPreviousSkipped=false;
    vec2 shaderlodPreviousUv=vec2(0.0);
    vec2 shaderlodCachedCell=vec2(-12345.0);
    float shaderlodCachedLower=0.0;
'''
 candidate=once(candidate,'    // Ray marching',setup+'    // Ray marching')
 anchor='        screenDepth = texture2D(depthtex1, screenMarchPos.xy).x;'
 replacement='''        bool shaderlodCertified=false;
        if(shaderlodHierarchyPermission!=0 && !searchFlag && i<RAYMARCH_STEPS-1 &&
           all(greaterThanEqual(screenMarchPos,vec3(0.0))) && all(lessThanEqual(screenMarchPos,vec3(1.0)))) {
            vec2 pixel=screenMarchPos.xy*shaderlodHierarchySize;
            vec2 cell=floor(pixel*(1.0/128.0));
            // Two whole texels inside the same native128block; boundary cases stay native.
            vec2 low=cell*128.0,high=min(low+128.0,shaderlodHierarchySize);
            if(all(greaterThan(pixel,low+2.0)) && all(lessThan(pixel,high-2.0))) {
                if(any(notEqual(cell,shaderlodCachedCell))) {
                    shaderlodCachedLower=texture2DLod(shaderlodDepthHierarchy,(cell+0.5)/ceil(shaderlodHierarchySize*(1.0/128.0)),0.0).r;
                    shaderlodCachedCell=cell;shaderlodHierarchyBounds+=1.0;
                }
                shaderlodCertified=shaderlodCachedLower>screenMarchPos.z;
            }
        }
        if(shaderlodCertified) {
            // Only the sign participates on a certified positive coarse miss.
            // Exact previous depth is repaired before any later negative comparator.
            screenDepth=1.0;shaderlodHierarchySaved+=1.0;
        } else screenDepth=texture2D(depthtex1,screenMarchPos.xy).x;'''
 if fixed16:replacement=replacement.replace('128.0','16.0').replace('native128block','native16block')
 candidate=once(candidate,anchor,replacement)
 candidate=once(candidate,'        depthDifference = screenDepth - screenMarchPos.z;','''        depthDifference = screenDepth - screenMarchPos.z;
        if(!shaderlodCertified && depthDifference<0.0 && shaderlodPreviousSkipped) {
            prevScreenDepth=texture2D(depthtex1,shaderlodPreviousUv).x;
            shaderlodHierarchyRepairs+=1.0;
        }''')
 candidate=once(candidate,'        prevScreenDepth = screenDepth;','''        prevScreenDepth = screenDepth;
        shaderlodPreviousSkipped=shaderlodCertified;
        shaderlodPreviousUv=screenMarchPos.xy;''')
 header='''uniform sampler2D shaderlodDepthHierarchy;
uniform int shaderlodHierarchyPermission;
uniform vec2 shaderlodHierarchySize;
float shaderlodHierarchySaved=0.0,shaderlodHierarchyBounds=0.0,shaderlodHierarchyRepairs=0.0;
'''
 if diagnostic:
  ref=re.sub(r'\bfastRaymarch\b','shaderlodHierarchyNative',native)
  cand=re.sub(r'\bfastRaymarch\b','shaderlodHierarchyCandidate',candidate)
  if control=='clone':cand=re.sub(r'\bfastRaymarch\b','shaderlodHierarchyCandidate',native)
  wrapper='''float shaderlodHierarchyCalled=0.0,shaderlodHierarchyMismatch=0.0,shaderlodHierarchyFirst=-1.0;
vec4 shaderlodHierarchyReference=vec4(0.0),shaderlodHierarchyResult=vec4(0.0);
vec3 fastRaymarch(vec3 direction,vec3 hitPoint,inout float infinite,float dither) {
 shaderlodHierarchyCalled=1.0;float candidateInfinite=infinite;
 vec3 reference=shaderlodHierarchyNative(direction,hitPoint,infinite,dither);
 vec3 candidate=shaderlodHierarchyCandidate(direction,hitPoint,candidateInfinite,dither);
 shaderlodHierarchyReference=vec4(reference,infinite);shaderlodHierarchyResult=vec4(candidate,candidateInfinite);
 for(int t=0;t<4;t++)if(!(shaderlodHierarchyReference[t]==shaderlodHierarchyResult[t])){shaderlodHierarchyMismatch=1.0;if(shaderlodHierarchyFirst<0.0)shaderlodHierarchyFirst=float(t);}
 return reference;
}
'''
  active=header+ref+cand+wrapper
 else:active=header+candidate
 files[n]=(s[:a]+'\n#if '+guard+'\n'+active+'\n#else\n'+native+'\n#endif\n'+s[z:]).encode();files['shaders/lib/config.glsl']+=f'\n#define {OPTION} 0 // [0 1]\n'.encode()
 if diagnostic:
  n='shaders/common/water_blocks_fragment.glsl';s=files[n].decode();s=once(s,'#include "/lib/config.glsl"','#include "/lib/config.glsl"'+'\n#if '+guard+'\n/*\n'+''.join(f'const int colortex{t}Format = RGBA32F;\nconst bool colortex{t}Clear = true;\nconst vec4 colortex{t}ClearColor=vec4(0.0,0.0,0.0,0.0);\n' for t in range(8,12))+'*/\n#endif\n');s=once(s,'    #include "/src/writebuffers.glsl"','    #include "/src/writebuffers.glsl"'+'\n#if '+guard+'''
    /* RENDERTARGETS: 1,8,9,10,11 */
    gl_FragData[1]=vec4(shaderlodHierarchyCalled>0.0?(shaderlodHierarchyMismatch>0.0?2.0:1.0):0.0,shaderlodHierarchyFirst,shaderlodHierarchySaved,shaderlodHierarchyBounds);
    gl_FragData[2]=shaderlodHierarchyReference;
    gl_FragData[3]=shaderlodHierarchyResult;
    gl_FragData[4]=vec4(shaderlodHierarchyRepairs,float(shaderlodHierarchyPermission),shaderlodHierarchySaved-shaderlodHierarchyBounds-shaderlodHierarchyRepairs,0.0);
    #endif
''');files[n]=s.encode();files['shaders/shaders.properties']+=''.join(f'\nblend.gbuffers_water.colortex{t}=off\n' for t in range(8,12)).encode()
 marker=dict(fixed16=fixed16,cell_size=16 if fixed16 else 128,diagnostic_only=diagnostic,kind='G05',native=NATIVE,control=control,last_target=11,targets={'8':'flag0notcalled/1equal/2fail,firstfinalfield,savedNativeDepthQueries,boundQueries','9':'nativeScreenXYZ,infinite','10':'candidateScreenXYZ,infinite','11':'exactPredecessorRepairQueries,actualPermission,netQueryExpressionsSaved,0'},limits='Native points/recurrence/refinement retained. Only certifiedpositivecoarse depthdifference sign replaced; any negative comparator repairs exactpreviousquery, finaldepth alwaysnative. Two wholetexelblockinterior; currentcopieddepth only; actualNN/CLAMP sampler admission required. Minbounds can remainambiguous. Sourcecounts notphysicaltransactions. NativeRGBreference in diagnostic; witness can change compilation.')
 files['SHADERLOD_G05_HIERARCHY.json']=(json.dumps(marker,indent=2)+'\n').encode()
 if diagnostic:files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode()
 files['hierarchy-source/build_makeup_depth_hierarchy_consumer.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{OPTION}=1\n'.encode());return dict(marker,artifact=str(output),sha256=sha(output.read_bytes()))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--fixed16',action='store_true');p.add_argument('--private-asahi',action='store_true');p.add_argument('--diagnostic',action='store_true');p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='skip');print(json.dumps(build(**vars(p.parse_args())),indent=2))
