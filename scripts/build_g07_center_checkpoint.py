#!/usr/bin/env python3
"""Private Asahi G07 center-query/linearization discriminator, native RGB.

No tap-loop stores or arrays. Untimed development only; not public admission.
"""
import argparse,json,tempfile,zipfile
from pathlib import Path
import build_g07_causal_inputs as thin
from build_makeup_unused_sky import once,archive_bytes,sha

def build(original,options,output,control='forcedfalse'):
    output=Path(output)
    if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
    with tempfile.TemporaryDirectory() as td:
        base=Path(td)/'base.zip';thin.build(original,options,base,control)
        with zipfile.ZipFile(base) as z:files={n:z.read(n) for n in z.namelist()}
    kernel=thin.parity.tap.KERNEL;s=files[kernel].decode();idx=s.index('float shaderlodAoReferenceKernel(')
    def checkpoint(text,tag):
        anchor='    float handCheck = linearDepth < 0.56 ? 1024.0 : 1.0;'
        text=once(text,anchor,anchor+f'\n    shaderlodCenter{tag}.x = linearDepth;')
        anchor='    linearDepth = ld(linearDepth);'
        return once(text,anchor,anchor+f'\n    shaderlodCenter{tag}.yzw = vec3(linearDepth, dither, handCheck);')
    files[kernel]=('vec4 shaderlodCenterN=vec4(-12345.0),shaderlodCenterC=vec4(-12345.0);\n'+checkpoint(s[:idx],'C')+checkpoint(s[idx:],'N')).encode()
    caller='shaders/common/deferred_fragment.glsl';s=files[caller].decode()
    s=once(s,'gl_FragData[2] = vec4(shaderlodAoMismatch>0.0?2.0:1.0,-1.0,shaderlodAoOmitted,-1.0);','''float shaderlodCenterFirst=-1.0;
    for(int field=0;field<4;field++)if(!(shaderlodCenterN[field]==shaderlodCenterC[field])&&shaderlodCenterFirst<0.0)shaderlodCenterFirst=float(field);
    gl_FragData[2] = vec4(shaderlodAoMismatch>0.0?2.0:1.0,shaderlodCenterFirst,shaderlodAoOmitted,-1.0);''')
    s=once(s,'gl_FragData[5] = vec4(near,far,aspectRatioInverse,fovYInverse);','gl_FragData[5] = shaderlodCenterN;')
    s=once(s,'gl_FragData[6] = vec4(fogDensityCoeff,rainStrength,float(frameCounter),float(AOSTEPS));','gl_FragData[6] = shaderlodCenterC;')
    files[caller]=s.encode()
    # Private diagnostic only: explicit Linux branch, never alter product capability.
    for n in [caller]:files[n]=files[n].replace(b'defined MC_OS_MAC',b'defined MC_OS_LINUX')
    marker=json.loads(files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']);marker.update(discriminator='center-query-and-native-ld-before-tap-loop',private_asahi_development=True)
    marker['targets'].update({'8':'finalFlag,firstCenterField(-1equal),actualOmittedExpressions,-1','11':'nativeCenterRawDepth,nativeCenterLD,nativeDither,nativeHandCheck','12':'candidateCenterRawDepth,candidateCenterLD,candidateDither,candidateHandCheck'})
    marker['limits']='No arrays or tap-loop stores. Center stores may change compiler. First observed center difference is a location, not instruction causality. Equal centers with final mismatch localize after center. Native RGB/all reference queries retained; no performance or public support proof.'
    files['SHADERLOD_CAUSAL_DIAGNOSTIC.json']=(json.dumps(marker,indent=2)+'\n').encode();files['causal-source/build_g07_center_checkpoint.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(Path(options).read_bytes()+f'\n{thin.parity.tap.OPTION}=1\n'.encode())
    return dict(marker,artifact=str(output.resolve()),sha256=sha(output.read_bytes()))
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for n in ['original','options','output']:p.add_argument('--'+n,type=Path,required=True)
    p.add_argument('--control',choices=['clone','forcedfalse','skip'],default='forcedfalse');print(json.dumps(build(**vars(p.parse_args())),indent=2))
