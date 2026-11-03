#!/usr/bin/env python3
"""Untimed native G04 actual translucent class census; native RGB unchanged."""
from pathlib import Path
import argparse,json
from build_makeup_unused_sky import NATIVE,read_archive,archive_bytes,once,sha
GUARD='defined IS_IRIS && IRIS_VERSION == 10812 && MC_VERSION == 12101 && defined MC_OS_MAC && defined GBUFFER_WATER && defined WATER_F && !defined NETHER && !defined THE_END && !defined UNKNOWN_DIM && !defined DISTANT_HORIZONS && !defined VOXY && !defined VOXY_PATCH'
def build(original,options,output):
 original,options,output=map(Path,[original,options,output]);assert sha(original.read_bytes())==NATIVE
 if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
 files=read_archive(original);name='shaders/common/water_blocks_fragment.glsl';s=files[name].decode();header=f'''\n#if {GUARD}
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
    gl_FragData[1]=vec4(blockType==0.0?1.0:(blockType==2.0?3.0:(blockType==3.0?4.0:257.0)));
    #endif'''.replace('GUARD',GUARD));files[name]=s.encode();files['shaders/shaders.properties']+=b'\nblend.gbuffers_water.colortex8=off\n'
 marker={'diagnostic_only':True,'kind':'G04-class','native':NATIVE,'encoding_mode':'predicate-bits','encoding':'0 untouched;1 native class0 ordinarytranslucent;3 native glassclass2;4 nativewaterclass3;257 unexpectedclass/unordered. Nativevertex classification and all nativefragmentRGB queries/ALU retained.','limits':'Visible last raster fragment after native depth test, not overdraw count, naturallyoccurring population outside the recorded savedscene, actualfastpath gain, physical transactions or timing. No option/nativequality/AA change.'}
 files['SHADERLOD_FRAGMENT_WORK_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['class-work-source/build_makeup_translucent_class_work.py']=Path(__file__).read_bytes();output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes());return {'artifact':str(output),'sha256':sha(output.read_bytes()),**marker}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__)
 for n in ['original','options','output']:p.add_argument('--'+n,required=True,type=Path)
 print(json.dumps(build(**vars(p.parse_args())),indent=2))
