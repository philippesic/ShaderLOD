#!/usr/bin/env python3
"""Untimed independent native cloud-function reference for G08, same inputs."""
import argparse,json
from pathlib import Path
import build_makeup_directional_cloud_bounds as m
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha

def build(original,options,output,error_bins=False,attribution=False,native_control=False,tail_steps=0,native_queries=False,relative_error=False,split_tail=False,masked_tail=False,zero_guard=False):
    original,options,output=map(Path,(original,options,output))
    if output.exists() or Path(str(output)+'.txt').exists():raise ValueError('Fresh output required')
    native=read_archive(original);files=m.patch(native,tail_steps,native_queries,split_tail,masked_tail,zero_guard)
    if attribution and relative_error:raise ValueError('Distinct attribution and relative-error encodings')
    if attribution or relative_error:error_bins=True
    if native_control:
        # Keep diagnostic plumbing/options/atlas, replace only candidate function
        # with its byte-identical native body to validate comparison machinery.
        prefix=files[m.CLOUD].decode().split('/* MakeUp - volumetric_clouds.glsl',1)[0]
        files[m.CLOUD]=(prefix+native[m.CLOUD].decode()).encode()
    guard=f'({m.GATE}) && {m.DEBUG} == 1'
    files[m.CLOUD]+=('\n#if '+guard+'\n'+native[m.CLOUD].decode().replace('vec3 get_cloud(','vec3 shaderlodG08ReferenceCloud(',1)+'\n#endif\n').encode()
    if error_bins:
        import re
        explicit=native[m.CLOUD].decode().replace('vec3 get_cloud(','vec3 shaderlodG08ExplicitReferenceCloud(',1)
        explicit,count=re.subn(r'texture2D\(gaux2, (.*)\)\.r',r'texture2DLod(gaux2, \1, 0.0).r',explicit)
        if count!=2:raise ValueError('Two native cloud sample expressions required')
        classify='''
float shaderlodG08ErrorBin(vec3 a,vec3 b) {
    if (!all(lessThan(abs(a),vec3(65536.0))) || !all(lessThan(abs(b),vec3(65536.0)))) return 15.0;
    vec3 delta=abs(a-b);float error=max(delta.x,max(delta.y,delta.z));
    if(error==0.0)return 0.0;
    if(error<=0.000000059604644775390625)return 1.0;
    if(error<=0.00000095367431640625)return 2.0;
    if(error<=0.0000152587890625)return 3.0;
    if(error<=0.000244140625)return 4.0;
    if(error<=0.00390625)return 5.0;
    if(error<=0.0625)return 6.0;
    if(error<=1.0)return 7.0;
    return 8.0;
}
'''
        if relative_error:
            classify+='''
float shaderlodG08RelativeErrorBin(vec3 a,vec3 b) {
    if (!all(lessThan(abs(a),vec3(65536.0))) || !all(lessThan(abs(b),vec3(65536.0)))) return 15.0;
    vec3 delta=abs(a-b);float error=max(delta.x,max(delta.y,delta.z));
    if(error==0.0)return 0.0;
    vec3 magnitude=max(abs(a),abs(b));
    float ratio=error/max(0.0000152587890625,max(magnitude.x,max(magnitude.y,magnitude.z)));
    if(ratio<=0.000000059604644775390625)return 1.0;
    if(ratio<=0.0000002384185791015625)return 2.0;
    if(ratio<=0.00000095367431640625)return 3.0;
    if(ratio<=0.000003814697265625)return 4.0;
    if(ratio<=0.0000152587890625)return 5.0;
    if(ratio<=0.00006103515625)return 6.0;
    if(ratio<=0.000244140625)return 7.0;
    if(ratio<=0.0009765625)return 8.0;
    if(ratio<=0.00390625)return 9.0;
    if(ratio<=0.015625)return 10.0;
    if(ratio<=0.0625)return 11.0;
    if(ratio<=0.25)return 12.0;
    if(ratio<=1.0)return 13.0;
    return 14.0;
}
'''
        files[m.CLOUD]+=('\n#if '+guard+'\n'+explicit+classify+'\n#endif\n').encode()
    caller='shaders/common/deferred_fragment.glsl';call='get_cloud(eyeDirection, blockColor.rgb, bright, dither, cameraPosition, CLOUD_STEPS_AVG, umbral, cloudColor, darkCloudColor)'
    anchor='                blockColor.rgb = '+call+';'
    replacement=f'''                #if {guard}
                vec3 g08Reference = {call.replace('get_cloud(','shaderlodG08ReferenceCloud(',1)};
G08_EXPLICIT_REFERENCE
                #endif
'''+anchor+f'''
                #if {guard}
                shaderlodG08Eligible = 1.0;
                shaderlodG08SavedSteps = any(notEqual(blockColor.rgb,g08Reference)) ? 256.0 : 0.0;
                #endif'''
    replacement=replacement.replace('G08_EXPLICIT_REFERENCE',('                vec3 g08Explicit = '+call.replace('get_cloud(','shaderlodG08ExplicitReferenceCloud(',1)+';') if error_bins else '')
    if error_bins:
        replacement=replacement.replace('any(notEqual(blockColor.rgb,g08Reference)) ? 256.0 : 0.0','shaderlodG08ErrorBin(blockColor.rgb,g08Reference) + 16.0*shaderlodG08ErrorBin(blockColor.rgb,g08Explicit)')
    if attribution:
        replacement=replacement.replace('                shaderlodG08Eligible = 1.0;', '                float g08Admitted = shaderlodG08Eligible;\n                float g08Skipped = shaderlodG08SavedSteps > 0.0 ? 1.0 : 0.0;\n                shaderlodG08Eligible = 1.0;')
        replacement=replacement.replace('shaderlodG08ErrorBin(blockColor.rgb,g08Reference) + 16.0*shaderlodG08ErrorBin(blockColor.rgb,g08Explicit)', 'shaderlodG08ErrorBin(blockColor.rgb,g08Reference) + 16.0*(g08Admitted + 2.0*g08Skipped + 4.0*(any(notEqual(blockColor.rgb,g08Explicit)) ? 1.0 : 0.0))')
    if relative_error:
        replacement=replacement.replace('shaderlodG08ErrorBin(blockColor.rgb,g08Reference) + 16.0*shaderlodG08ErrorBin(blockColor.rgb,g08Explicit)', 'shaderlodG08ErrorBin(blockColor.rgb,g08Reference) + 16.0*shaderlodG08RelativeErrorBin(blockColor.rgb,g08Reference)')
    files[caller]=once(files[caller].decode(),anchor,replacement).encode()
    meta={'diagnostic_only':True,'native':m.NATIVE,'parity_diagnostic':True,'tail_steps':tail_steps,'native_queries':native_queries,'split_tail':split_tail,'masked_tail':masked_tail,'zero_guard':zero_guard,'native':m.NATIVE,'scope':'Same-fragment native complete cloud function vs directional bound cloud function, identical caller inputs; exact FP32 returned RGB comparison. No whole image, timing, cross-tuple or mathematical-exhaustion claim.'}
    if error_bins:meta.update(error_bins=True,encoding='0 noncloud,1+low+16*high:low candidate vs native implicit;high candidate vs full native positions with explicitLOD0. Bin0 exact,1<=2^-24,2<=2^-20,3<=2^-16,4<=2^-12,5<=2^-8,6<=2^-4,7<=1,8>1,15 nonfinite/abs>=65536. Bounds are diagnostic categories, not acceptance thresholds.')
    if attribution:meta.update(attribution=True,native_control=native_control,encoding='0 noncloud,1+errorBin+16*flags: flags bit0 admitted,bit1 skipped,bit2 unequal to explicit LOD0 native reference. Error bins as above; diagnostic categories only.')
    if relative_error:meta.update(relative_error=True,native_control=native_control,encoding='0 noncloud,1+absoluteBin+16*relativeBin vs native implicit reference. Absolute bins as above. Relative max-channel error/max(max-channel magnitude of both RGB,2^-16):0exact,1<=2^-24,2<=2^-22,3<=2^-20,4<=2^-18,5<=2^-16,6<=2^-14,7<=2^-12,8<=2^-10,9<=2^-8,10<=2^-6,11<=2^-4,12<=1/4,13<=1,14>1,15 nonfinite/abs>=65536. Diagnostic bounds, not quality acceptance thresholds.')
    files['G08-parity.json']=(json.dumps(meta,indent=2)+'\n').encode();files['SHADERLOD_G08_DIRECTIONAL_NOTICE.txt']=(json.dumps(meta,indent=2)+'\n').encode();files['cloud-parity-source/build_makeup_cloud_parity.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{m.OPTION}=0\n{m.DEBUG}=0\n'.encode());return dict(meta,sha256=sha(output.read_bytes()))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--error-bins',action='store_true');p.add_argument('--attribution',action='store_true');p.add_argument('--relative-error',action='store_true');p.add_argument('--native-control',action='store_true');p.add_argument('--tail-steps',type=int,default=0,choices=[0,4,8]);p.add_argument('--native-queries',action='store_true');p.add_argument('--split-tail',action='store_true');p.add_argument('--masked-tail',action='store_true');p.add_argument('--zero-guard',action='store_true');print(json.dumps(build(**vars(p.parse_args())),indent=2))
