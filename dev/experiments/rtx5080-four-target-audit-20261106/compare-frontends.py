"""Compare actual attached original shader text; never transfer GPU conclusions."""
import difflib
import hashlib
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent
ASAHI=HERE.with_name('asahi-four-target-audit-20261104')
result={'scope':'Exact bytes of captured attached GL shader text and complete native option maps. Same text is frontend parity only: not GPU binary equivalence, physical resource lifetime, correctness or transferable performance.', 'targets':{}}
for shader in ('makeup','bsl','reimagined','unbound'):
    before=ASAHI/(shader+'-repaired-baseline-r1')
    after=HERE/(shader+'-baseline-native-r1')
    old=json.loads((before/'summary.json').read_text());new=json.loads((after/'summary.json').read_text())
    assert old['evaluation']['validity']==new['evaluation']['validity']=='VALID'
    assert old['ready']['main_mod'] is new['ready']['main_mod'] is False
    assert old['ready']['effective_options']==new['ready']['effective_options']
    olddir=next(before.glob('native-graph-*'));newdir=next(after.glob('native-graph-*'))
    a=json.loads((olddir/'manifest.json').read_text());b=json.loads((newdir/'manifest.json').read_text())
    def stages(manifest,directory):
        return {(p['name'],stage['type']):directory/stage['file'] for p in manifest['linked_programs'] for stage in p['stages']}
    left,right=stages(a,olddir),stages(b,newdir)
    rows=[]
    for key in sorted(left.keys()|right.keys()):
        row=dict(program=key[0],stage=key[1],asahi=str(left.get(key)),rtx5080=str(right.get(key)))
        if key not in left or key not in right:row['status']='MISSING_SIDE'
        else:
            x,y=left[key].read_bytes(),right[key].read_bytes()
            row.update(asahi_sha256=hashlib.sha256(x).hexdigest(),rtx5080_sha256=hashlib.sha256(y).hexdigest(),status='IDENTICAL' if x==y else 'DIFFERS')
            if x!=y:
                output=HERE/'frontend-differences'/(shader+'-'+key[0]+'-'+str(key[1])+'.diff')
                output.parent.mkdir(exist_ok=True)
                output.write_text(''.join(difflib.unified_diff(x.decode().splitlines(keepends=True),y.decode().splitlines(keepends=True),fromfile=str(left[key]),tofile=str(right[key]))))
                row['difference']=str(output)
        rows.append(row)
    result['targets'][shader]={'native_options_equal':True,'asahi_viewport':[old['ready']['width'],old['ready']['height']],'rtx5080_viewport':[new['ready']['width'],new['ready']['height']],'asahi_gl':old['ready']['gpu_driver'],'rtx5080_gl':new['ready']['gpu_driver'],'stages':rows}
(HERE/'frontend-parity.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({name:{state:sum(r['status']==state for r in data['stages']) for state in ('IDENTICAL','DIFFERS','MISSING_SIDE')} for name,data in result['targets'].items()}))
