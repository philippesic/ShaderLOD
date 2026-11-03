#!/usr/bin/env python3
"""Untimed all-length positive-coarse hierarchy opportunity upper bound."""
from pathlib import Path
import argparse,json
from build_makeup_unused_sky import NATIVE,read_archive,archive_bytes,once,sha
from build_makeup_depth_hierarchy_gate import GUARD,OPTION

def build(original,options,output):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 files=read_archive(original);name='shaders/lib/water.glsl';s=files[name].decode();a=s.index('vec3 fastRaymarch(');z=s.index('\n#if SUN_REFLECTION',a);k=s[a:z]
 prefix=f'''#if {GUARD}
uniform float viewWidth;
uniform float viewHeight;
float shaderlodHierarchyCalled=0.0;
vec3 shaderlodHierarchyMax=vec3(0.0);
#endif
'''
 setup=f'''    #if {GUARD}
    shaderlodHierarchyCalled=1.0;
    vec2 hierarchyUv[10];
    bool hierarchyValid[10];
    for(int t=0;t<10;t++){{hierarchyUv[t]=vec2(0.0);hierarchyValid[t]=false;}}
    #endif
'''
 k=once(k,'    // Ray marching',setup+'    // Ray marching')
 k=once(k,'        screenDepth = texture2D(depthtex1, screenMarchPos.xy).x;',f'''        #if {GUARD}
        hierarchyUv[i]=screenMarchPos.xy;
        hierarchyValid[i]=!searchFlag && all(lessThan(abs(screenMarchPos),vec3(1000000.0)));
        #endif
        screenDepth = texture2D(depthtex1, screenMarchPos.xy).x;''')
 anchor='        prevScreenDepth = screenDepth;';k=once(k,anchor,anchor+f'''\n        #if {GUARD}
        hierarchyValid[i]=hierarchyValid[i] && depthDifference>0.0;
        #endif''')
 # Compute after every native recurrence and query. No hypothetical skipping
 # affects native control flow or exact last depth. DP only chooses disjoint
 # potential groups and subtracts one bound plus one retained last query.
 anchor='    infinite = float(screenDepth > 0.9999);';pos=k.index(anchor)
 if k[:pos].count('return '):raise ValueError('Hierarchy witness must precede every native return')
 witness=f'''    #if {GUARD}
    for(int level=0;level<3;level++) {{
        float block=level==0?8.0:(level==1?32.0:128.0);
        float best[11];best[0]=0.0;
        for(int n=1;n<=10;n++) {{
            best[n]=best[n-1];
            vec2 lo=vec2(-1000000.0),hi=vec2(1000000.0);
            bool valid=true;
            for(int back=0;back<10;back++) {{
                int j=n-1-back;
                if(j<0)break;
                vec2 size=vec2(viewWidth,viewHeight);
                vec2 p=min(clamp(hierarchyUv[j],vec2(0.0),vec2(1.0))*size,size-vec2(1.0));
                lo=max(lo,floor((p-0.02)/block));hi=min(hi,floor((p+0.02)/block));
                valid=valid && hierarchyValid[j];
                if(valid && n-j>=3 && all(lessThanEqual(lo,hi)))best[n]=max(best[n],best[j]+float(n-j-2));
            }}
        }}
        shaderlodHierarchyMax[level]=best[10];
    }}
    #endif
'''
 k=k[:pos]+witness+k[pos:];files[name]=(s[:a]+prefix+k+s[z:]).encode();files['shaders/lib/config.glsl']+=f'\n#define {OPTION} 0 // [0 1]\n'.encode()
 caller='shaders/common/water_blocks_fragment.glsl';s=files[caller].decode();s=once(s,'#include "/lib/config.glsl"','#include "/lib/config.glsl"'+f'''\n#if {GUARD}
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0,0.0,0.0,0.0);
*/
#endif
''');s=once(s,'    #include "/src/writebuffers.glsl"','    #include "/src/writebuffers.glsl"'+f'''\n    #if {GUARD}
    /* DRAWBUFFERS:18 */
    gl_FragData[1]=vec4(shaderlodHierarchyCalled>0.0?1.0+dot(shaderlodHierarchyMax,vec3(1.0,9.0,81.0)):0.0);
    #endif
''');files[caller]=s.encode();files['shaders/shaders.properties']+=b'\nblend.gbuffers_water.colortex8=off\n'
 marker={'diagnostic_only':True,'kind':'G05-reuse','native':NATIVE,'encoding_mode':'predicate-bits','max_code':729,'encoding':'code0 not called; code-1 base9 digits maximal nonoverlapping removable sourcequeries for8/32/128square levels. All contiguous native coarse positive-miss groups length3..10 considered; atmost8queries removable/ray. Each group pays1boundquery+1exactlastdepthquery. Native10queries/recurrence/outputs retained.','limits':'Possibility apron0.02texel overestimates boundary overlap. No minmax contents/build/coverage certificate, physicaltransactions or speed claim. Native negative/refinement groups ineligible; architecture is conservative positive coarse groups. Levels4/16/64 upperbounded by8/32/128 results. Visible lastfragment only.'}
 files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['hierarchy-gate-source/build_makeup_depth_hierarchy_dp_gate.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{OPTION}=1\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 print(json.dumps(build(**vars(p.parse_args())),indent=2))
