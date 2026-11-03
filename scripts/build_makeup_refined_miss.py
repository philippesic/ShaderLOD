#!/usr/bin/env python3
"""G05 native refinement suffix inside a proven zero-border camera-space box.

After search begins, native increments halve and may change sign. All future
points stay in currentPoint +/- abs(previousIncrement). The certificate includes
FP slack, positive clip w, and glass's native x>99 fallback exclusion.
"""
import argparse,json
from pathlib import Path
import build_makeup_glass_miss as glass
import build_makeup_projected_miss as water
from build_makeup_unused_sky import read_archive,archive_bytes,once,sha

CERT='''
bool shaderlodG05RefinementBox(vec3 point, vec3 step, vec3 errorBound) {
    // Sum of every remaining halved increment is strictly less than abs(step).
    vec3 radius = abs(step) + errorBound;
    float px = gbufferProjection[0].x, py = gbufferProjection[1].y;
    if (!all(lessThan(radius,vec3(131072.0))) || !(point.z + radius.z < 0.0)) return false;
    G05_EDGE_TEST
}
'''
GLASS_EDGE='''vec4 edge = vec4(px*point.x,-px*point.x,py*point.y,-py*point.y) + point.z;
    vec4 bound = vec4(px*radius.x,px*radius.x,py*radius.y,py*radius.y) + radius.z;
    // Native glass substitutes hitPoint+direction*76 if returned UV.x>99.
    // Refuse unless *every* point in the box has UV.x<99, so no substitute
    // can invalidate the zero-border witness. 197 is2*99-1.
    bool noFallback = px*point.x + 197.0*point.z < -(px*radius.x + 197.0*radius.z);
    return noFallback && any(greaterThan(edge,bound));'''
WATER_EDGE='''// Water's negative-base pow remains outside this proof: top edge only.
    return py*point.y + point.z > py*radius.y + radius.z;'''

def patch(native,kind):
    if kind=='glass':
        files=glass.patch(native);helper=glass.HELPER;projection='shaderlodG05GlassProjection';saved='shaderlodG05GlassSavedSteps';debug=glass.DEBUG;edge=GLASS_EDGE
    elif kind=='water':
        files=water.transform(native);helper=water.HELPER;projection='shaderlodG05Projection';saved='shaderlodG05SavedSteps';debug='SHADERLOD_G05_DEBUG';edge=WATER_EDGE
    else:raise ValueError('Pinned glass or water kind required')
    text=files[helper].decode();index=text.index('vec3 shaderlodG05')
    text=text[:index]+CERT.replace('G05_EDGE_TEST',edge)+'\n'+text[index:]
    anchor='    for (int i = 0; i < RAYMARCH_STEPS; i++) {\n'
    text=once(text,anchor,anchor+f'''        // A new certificate for already-refining misses. No assumption about
        // direction.z or later depth comparisons; box contains every sign path.
        if (searchFlag && outOfEyeFlag) {{
            if (!shaderlodG05Checked) {{
                shaderlodG05Checked = true;
                shaderlodG05Eligible = {projection}() &&
                    gbufferProjection[0].x > 1.0e-4 &&
                    all(lessThan(abs(hitPoint),vec3(65536.0))) &&
                    all(lessThanEqual(abs(direction),vec3(1.0))) && dither >= 0.0 && dither <= 1.0;
                shaderlodG05Error = 0.0001 * (abs(hitPoint) + abs(direction)*2048.0 + vec3(1.0));
            }}
            if (shaderlodG05Eligible && shaderlodG05RefinementBox(currentMarchPoint,pathIncrement,shaderlodG05Error)) {{
                infinite = 1.0;
                #if {debug} == 1
                {saved} = float(RAYMARCH_STEPS-i);
                #endif
                return screenMarchPos;
            }}
        }}
''')
    files[helper]=text.encode();return files

def build(original,options,output,kind):
    original,options,output=map(Path,(original,options,output));raw=options.read_bytes()
    if any(p.exists() for p in [output,Path(str(output)+'.txt'),output.with_suffix('.json')]) or b'SHADERLOD_' in raw:raise ValueError('Fresh output/native-only sidecar required')
    native=read_archive(original);files=patch(native,kind)
    meta={'id':'G05','variant':kind+'-refinement-box-zero-border','native_sha256':water.NATIVE_SHA256,'default_off':True,
          'proof':'Remaining native halved increments bounded by previous absolute step plus FP envelope; all points have positive clipw and native zero border; glass fallback excluded by whole-box UVx<99. Coarse certificate remains a separate alternative.',
          'limits':'Conservative FP32 model and actual source/sampler/activity/quality/economics checks required. Never an approximate visible-ray budget.'}
    # The standard work-wrapper recognizes these exact pinned notice formats.
    notice='SHADERLOD_G05_GLASS_NOTICE.txt' if kind=='glass' else 'G05-build.json'
    files[notice]=(json.dumps(meta,indent=2)+'\n').encode();files['refinement-source/build_makeup_refined_miss.py']=Path(__file__).read_bytes()
    output.write_bytes(archive_bytes(files));option=glass.OPTION if kind=='glass' else 'SHADERLOD_G05_PROJECTED_MISS';debug=glass.DEBUG if kind=='glass' else 'SHADERLOD_G05_DEBUG'
    Path(str(output)+'.txt').write_bytes(raw+f'\n{option}=0\n{debug}=0\n'.encode());meta['sha256']=sha(output.read_bytes());output.with_suffix('.json').write_text(json.dumps(meta,indent=2)+'\n');return meta

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',required=True,type=Path);p.add_argument('--options',required=True,type=Path);p.add_argument('--output',required=True,type=Path);p.add_argument('--kind',choices=['glass','water'],required=True)
    print(json.dumps(build(**vars(p.parse_args())),indent=2))
