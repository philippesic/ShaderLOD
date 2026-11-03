#!/usr/bin/env python3
"""Untimed G05 retained four-step footprint gate, all native work retained.
No hierarchy or skip. It is a feasibility upper bound for one conservative level.
"""
from pathlib import Path
import argparse,json
from build_makeup_unused_sky import NATIVE,read_archive,archive_bytes,once,sha
OPTION='SHADERLOD_G05_HIERARCHY_GATE'
GUARD=f'{OPTION} == 1 && defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined GBUFFER_WATER && defined WATER_F && REFLECTION == 1 && SSR_TYPE == 1 && RAYMARCH_STEPS == 10 && AA_TYPE == 2 && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
def build(original,options,output):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 files=read_archive(original);name='shaders/lib/water.glsl';s=files[name].decode();a=s.index('vec3 fastRaymarch(');z=s.index('\n#if SUN_REFLECTION',a);kernel=s[a:z]
 prefix=f'''#if {GUARD}
uniform float viewWidth;
uniform float viewHeight;
float shaderlodG05HierarchyCalled=0.0;
vec4 shaderlodG05HierarchyGroups=vec4(0.0);
#endif
'''
 setup=f'''    #if {GUARD}
    shaderlodG05HierarchyCalled=1.0;
    vec2 gLo1,gHi1,gLo2,gHi2,gLo4,gHi4,gLo8,gHi8;
    bool gCoarse=true,gMiss=true;
    #endif
'''
 kernel=once(kernel,'    // Ray marching',setup+'    // Ray marching')
 kernel=once(kernel,'    for (int i = 0; i < RAYMARCH_STEPS; i++) {','''    for (int i = 0; i < RAYMARCH_STEPS; i++) {
    #if GUARD
        if(i==0 || i==4 || i==8) {
            gLo1=vec2(-1000000.0); gHi1=vec2(1000000.0);
            gLo2=gLo1;gHi2=gHi1;gLo4=gLo1;gHi4=gHi1;gLo8=gLo1;gHi8=gHi1;
            gCoarse=true;gMiss=true;
        }
        gCoarse=gCoarse && !searchFlag;
    #endif'''.replace('GUARD',GUARD))
 anchor='        screenDepth = texture2D(depthtex1, screenMarchPos.xy).x;'
 witness='''        #if GUARD
        // Keep every actual native query/recurrence unchanged. A 0.02texel
        // possibility apron overestimates retained footprint near boundaries;
        // it is not an admission certificate for a future skipping shader.
        vec2 gSize=vec2(viewWidth,viewHeight);
        vec2 gPos=min(clamp(screenMarchPos.xy,vec2(0.0),vec2(1.0))*gSize,gSize-vec2(1.0));
        bool gFinite=all(lessThan(abs(screenMarchPos),vec3(1000000.0))) && all(greaterThan(gSize,vec2(0.0))) && all(lessThanEqual(gSize,vec2(4096.0)));
        gMiss=gMiss && gFinite;
        gLo1=max(gLo1,floor(gPos-0.02));gHi1=min(gHi1,floor(gPos+0.02));
        gLo2=max(gLo2,floor((gPos-0.02)*0.5));gHi2=min(gHi2,floor((gPos+0.02)*0.5));
        gLo4=max(gLo4,floor((gPos-0.02)*0.25));gHi4=min(gHi4,floor((gPos+0.02)*0.25));
        gLo8=max(gLo8,floor((gPos-0.02)*0.125));gHi8=min(gHi8,floor((gPos+0.02)*0.125));
        #endif
'''.replace('GUARD',GUARD)
 kernel=once(kernel,anchor,witness+anchor)
 anchor='        prevScreenDepth = screenDepth;'
 witness='''        #if GUARD
        gMiss=gMiss && depthDifference>0.0;
        if((i==3 || i==7) && gCoarse && gMiss) {
            shaderlodG05HierarchyGroups+=vec4(all(lessThanEqual(gLo1,gHi1))?1.0:0.0,
                all(lessThanEqual(gLo2,gHi2))?1.0:0.0,
                all(lessThanEqual(gLo4,gHi4))?1.0:0.0,
                all(lessThanEqual(gLo8,gHi8))?1.0:0.0);
        }
        #endif
'''.replace('GUARD',GUARD)
 kernel=once(kernel,anchor,anchor+'\n'+witness);files[name]=(s[:a]+prefix+kernel+s[z:]).encode();files['shaders/lib/config.glsl']+=f'\n#define {OPTION} 0 // [0 1]\n'.encode()
 caller='shaders/common/water_blocks_fragment.glsl';s=files[caller].decode();header=f'''\n#if {GUARD}
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor = vec4(0.0,0.0,0.0,0.0);
*/
#endif
''';s=once(s,'#include "/lib/config.glsl"','#include "/lib/config.glsl"'+header)
 s=once(s,'    #include "/src/writebuffers.glsl"','''    #include "/src/writebuffers.glsl"
    #if GUARD
    /* DRAWBUFFERS:18 */
    gl_FragData[1]=vec4(shaderlodG05HierarchyCalled>0.0 ? 1.0+dot(shaderlodG05HierarchyGroups,vec4(1.0,4.0,16.0,64.0)) : 0.0);
    #endif'''.replace('GUARD',GUARD));files[caller]=s.encode();files['shaders/shaders.properties']+=f'\nuniform.float.viewWidth=viewWidth\nuniform.float.viewHeight=viewHeight\nblend.gbuffers_water.colortex8=off\n'.encode()
 # viewWidth/viewHeight are standard Iris uniforms; no custom recursive definition.
 files['shaders/shaders.properties']=files['shaders/shaders.properties'].replace(b'uniform.float.viewWidth=viewWidth\nuniform.float.viewHeight=viewHeight\n',b'')
 marker={'diagnostic_only':True,'kind':'G05-reuse','native':NATIVE,'encoding_mode':'predicate-bits','encoding':'0 ray not called; code-1 base4 digits count complete4step coarse nativepositive-miss groups with possible common1/2/4/8texel square respectively (at most2each). All10nativequeries and nativeposition/refinement/hidden/lastdepthrecurrence execute unchanged.','mechanism_bound':'An allpositive4step group can at best replace4nativequeries by1minmaxbound+1exactlastdepth query: at most2sourcequeries saved. NativeprevScreenDepth and final infinite/return require exactlastdepth. This bound precedes min/max build and coverage proof.','build_tax_floor':'A complete one-level block min/max build reads every originaldepth texel (~W*Hsourcequeries) and writesRG32F(W/B)*(H/B) pixels. Sourcecall counts/nominalbytes are not physical transactions or measuredcost. Actual nativecopy timing/lifecycle join remains required.','limits':'Footprint gate with conservative0.02source-texel possibility apron, native100AA2/SSR10, no skip/output/parity/speed claim. Visiblelastfragment only, no overdraw totals. Counts1 versus larger levels expose overlap with alreadytried exactdepth reuse.'}
 files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['hierarchy-gate-source/build_makeup_depth_hierarchy_gate.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{OPTION}=1\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 print(json.dumps(build(**vars(p.parse_args())),indent=2))
