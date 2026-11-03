#!/usr/bin/env python3
"""Untimed G08 audit: execute every native sample at proposed omitted positions.

The candidate bound marks an omission suffix, but this diagnostic retains all
native queries, positions, opacity and color accumulation. RT8 records any
nonzero native opacity inside that suffix. Never use it for lean timing.
"""
import argparse,json
from pathlib import Path
import build_makeup_directional_cloud_bounds as m
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha

def build(original,options,output,tail_steps=4):
    original,options,output=map(Path,(original,options,output))
    if any(p.exists() for p in [output,Path(str(output)+'.txt')]):raise ValueError('Fresh output required')
    native=read_archive(original);files=m.patch(native,tail_steps);s=files[m.CLOUD].decode()
    s=once(s,'float shaderlodG08SavedSteps = 0.0;','float shaderlodG08SavedSteps = 0.0;\nbool shaderlodG08AuditOmitting = false;')
    s=once(s,'            shaderlodG08Eligible = 1.0;','            // Eligibility is set only when a suffix would actually be omitted.')
    old='''            if (intersection_pos.y > shaderlodG08Ceiling) {
                #if SHADERLOD_G08_DIRECTIONAL_DEBUG == 1
                shaderlodG08SavedSteps = float(samples - i);
                #endif
                break;
            }'''
    new='''            #if SHADERLOD_G08_DIRECTIONAL_DEBUG == 1
            if (intersection_pos.y > shaderlodG08Ceiling) {
                shaderlodG08AuditOmitting = true;
                shaderlodG08Eligible = 1.0;
            }
            #endif'''
    s=once(s,old,new)
    anchor='            if (current_opacity > 0.0) {'
    check=f'''            #if ({m.GATE}) && {m.DEBUG} == 1
            if (shaderlodG08AuditOmitting && !(current_opacity == 0.0))
                shaderlodG08SavedSteps += 1.0;
            #endif
'''
    s=once(s,anchor,check+anchor);files[m.CLOUD]=s.encode()
    meta={'diagnostic_only':True,'native':m.NATIVE,'zero_tail_audit':True,'tail_steps':tail_steps,'error_bins':True,
          'encoding':'0 no proposed omission,1 proposed omitted suffix has zero native opacity at every tested iteration,1+number of nonzero/NaN native opacities in proposed suffix. Full native trace is executed, not omitted work.',
          'scope':'Same-fragment source bound versus actual native sample opacity at all would-be omitted positions. Diagnostic compile only; not whole-image quality, hardware transactions or speed.'}
    files['G08-parity.json']=(json.dumps(meta,indent=2)+'\n').encode();files['SHADERLOD_G08_DIRECTIONAL_NOTICE.txt']=(json.dumps(meta,indent=2)+'\n').encode();files['tail-audit-source/build_makeup_cloud_tail_audit.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));Path(str(output)+'.txt').write_bytes(options.read_bytes()+f'\n{m.OPTION}=0\n{m.DEBUG}=0\n'.encode());return dict(meta,sha256=sha(output.read_bytes()))

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--tail-steps',type=int,default=4,choices=[0,4,8]);print(json.dumps(build(**vars(p.parse_args())),indent=2))
