#!/usr/bin/env python3
"""Untimed per-ray contiguous4 cloud group proof, native queries/order/positions."""
from pathlib import Path
import argparse,json
import build_makeup_directional_cloud_bounds as m
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha,NATIVE
OPTION='SHADERLOD_G08_FOUR_GROUPS';GATE=m.GATE.replace(m.OPTION+' > 0',OPTION+' == 1')
def build(original,options,output,control='skip'):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 native=read_archive(original);f=m.patch(native,tail_steps=4,native_queries=True)
 # Reuse only pinned immutable atlas construction/binding. Restore native cloud
 # and option inventory so no previous suffix mechanism participates.
 f[m.CLOUD]=native[m.CLOUD];f['shaders/lib/config.glsl']=native['shaders/lib/config.glsl']+f'\n#define {OPTION} 0 // [0 1]\n'.encode()
 f[m.PROPS if hasattr(m,'PROPS') else 'shaders/shaders.properties']=native['shaders/shaders.properties']+f'\n#if {OPTION} == 1\ntexture.deferred.noisetex = textures/shaderlod_g08_directional.png\n#endif\nuniform.int.shaderlodG08GroupSkip={1 if control=="skip" else 0}\nblend.deferred.colortex8=off\n'.encode()
 header=f'''#if {GATE}
uniform int shaderlodG08GroupSkip;
float shaderlodG08GroupCalled=0.0;
float shaderlodG08GroupAttempts=0.0,shaderlodG08GroupPotential=0.0,shaderlodG08GroupBadSteps=0.0,shaderlodG08GroupMismatch=0.0;
#endif
''';s=native[m.CLOUD].decode();a=s.index('        for (int i = 0; i < samples; i++) {');brace=s.index('{',a);depth=1;z=brace+1
 while depth:
  if s[z]=='{':depth+=1
  elif s[z]=='}':depth-=1
  z+=1
 original_loop=s[a:z];body=s[brace+1:z-1];update='            intersection_pos += increment;';body=once(body,update,'')
 audit='''            if(shaderlodG08GroupEmpty && !(current_opacity==0.0))shaderlodG08GroupBadSteps+=1.0;
''';body=once(body,'            if (current_opacity > 0.0) {',audit+'            if (current_opacity > 0.0) {')
 checkpoint='''            if(i==0 || i==4 || i==8 || i==12) {
                shaderlodG08GroupEmpty=false;
                vec2 travel=increment.xz*4.0*0.0002777777777777778;
                bool allowed=i+4<=samples && samples>=7 && samples<=16 && umbral>=0.0 && umbral<=0.9 &&
                    all(lessThan(abs(travel),vec2(0.03125))) && increment.y>0.0 && increment.y<256.0 &&
                    dither>=0.0 && dither<=1.0 && abs(frameTimeCounter)<8192.0 &&
                    all(lessThan(abs(intersection_pos),vec3(32768.0))) && all(lessThan(abs(intersection_pos_sup),vec3(32768.0)));
                if(allowed) {
                    shaderlodG08GroupAttempts+=1.0;
                    vec2 uvHi=intersection_pos.xz*0.0002777777777777778+frameTimeCounter*CLOUD_HI_FACTOR;
                    vec2 uvLow=intersection_pos.zx*0.0002777777777777778+frameTimeCounter*CLOUD_LOW_FACTOR;
                    float upper=smoothstep(0.05,0.95,0.5*(shaderlodG08DirectionalBound(uvHi,travel)+shaderlodG08DirectionalBound(uvLow,travel.yx)));
                    upper=max((upper-umbral)/(1.0-umbral),0.0);
                    float ceiling=CLOUD_PLANE_CENTER+upper*dif_sup+dist_aux_coeff_blur+0.01;
                    shaderlodG08GroupEmpty=intersection_pos.y>ceiling;
                    if(shaderlodG08GroupEmpty)shaderlodG08GroupPotential+=1.0;
                }
            }
'''
 loop='''        bool shaderlodG08GroupEmpty=false;
        for (int i = 0; i < samples; i++) {
'''+checkpoint+'            if(shaderlodG08GroupSkip==0 || !shaderlodG08GroupEmpty) {\n'+body+'            }\n'+update+'\n        }'
 candidate=s if control=='clone' else s[:a]+f'#if {GATE}\n'+loop+'\n#else\n'+original_loop+'\n#endif\n'+s[z:]
 start=candidate.index('{',candidate.index('vec3 get_cloud('))+1
 candidate=candidate[:start]+f'\n#if {GATE}\nshaderlodG08GroupCalled=1.0;\n#endif\n'+candidate[start:]
 ref=s.replace('vec3 get_cloud(','vec3 shaderlodG08GroupReferenceCloud(',1)
 f[m.CLOUD]=(header+f'#if {GATE}\n'+m.HELPER+'\n#endif\n'+candidate+f'\n#if {GATE}\n'+ref+'\n#endif\n').encode()
 caller='shaders/common/deferred_fragment.glsl';s=f[caller].decode();s=once(s,'#include "/lib/config.glsl"','#include "/lib/config.glsl"'+f'''\n#if {GATE}
/*
const int colortex8Format = R16F;
const bool colortex8Clear = true;
const vec4 colortex8ClearColor=vec4(0.0,0.0,0.0,0.0);
*/
#endif
''')
 call='get_cloud(eyeDirection, blockColor.rgb, bright, dither, cameraPosition, CLOUD_STEPS_AVG, umbral, cloudColor, darkCloudColor)';anchor='                blockColor.rgb = '+call+';';s=once(s,anchor,f'''                #if {GATE}
                vec3 shaderlodGroupNative={call.replace('get_cloud(','shaderlodG08GroupReferenceCloud(',1)};
                vec3 shaderlodGroupCandidate={call};
                shaderlodG08GroupMismatch=any(notEqual(shaderlodGroupNative,shaderlodGroupCandidate))?1.0:0.0;
                blockColor.rgb=shaderlodGroupNative;
                #else
'''+anchor+'\n                #endif')
 s=once(s,'/* DRAWBUFFERS:14 */',f'#if {GATE}\n    /* DRAWBUFFERS:148 */\n    #else\n    /* DRAWBUFFERS:14 */\n    #endif');s=once(s,'    gl_FragData[1] = blockColor;','    gl_FragData[1] = blockColor;'+f'''\n    #if {GATE}
    gl_FragData[2]=vec4(shaderlodG08GroupCalled>0.0?1.0+shaderlodG08GroupAttempts+5.0*shaderlodG08GroupPotential+25.0*shaderlodG08GroupBadSteps+425.0*shaderlodG08GroupMismatch:0.0);
    #endif''');f[caller]=s.encode()
 marker={'diagnostic_only':True,'kind':'G08-cloud','native':NATIVE,'control':control,'encoding_mode':'predicate-bits','max_code':850,'encoding':'code-1: base5 attemptedgroups,base5 certifiedemptygroups,base17 native nonzero/NaNopacitystepsinsidecertificate,base2 exactRGBmismatch. Fouroriginalsequentialsteps/group; each attemptedgroup pays2boundqueries; each skippedfullgroup removes8nativequeryexpressions. Lastpartialgroup unchanged. Countsperray, neverhardwarelane/subgroup votes.','coverage':'Inherited signed4/8/12/16texel bins/tile16/3texelapron/+1byte maximum; full4steptravel and increasingY make currentY>ceiling covercontiguous4. Original native position additions retained evenwhenbody skipped; native implicitcloudlookups retained. Actualsampler/mipmapjoin needed.','limits':'Reference RGB/fullnative work retained. No physicaltransaction/speed inference; allatlas/build/setup/guard costs wouldcount. Forcedfalse auditsactualnativeopacityatwouldbeskips; any RGBmismatch rejectsquality independently of certificate. No approximate filter.'}
 f['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();f['fourgroups-source/build_makeup_cloud_four_groups.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(f));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{OPTION}=1\n'.encode());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='skip');print(json.dumps(build(**vars(p.parse_args())),indent=2))
