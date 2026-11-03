"""Binary32 region fixtures and actual pinned GLSL grammar; no driver or client.

The independent planar/edge geometry runs through a reviewed scalar reference.
It is not GLSL execution; contraction/rounding near thresholds still needs driver
acceptance. Shared primitives round each arithmetic operation to binary32.
"""
import copy
import hashlib
import io
import math
import os
from pathlib import Path
import random
import re
import subprocess
import tempfile
import unittest
import zipfile

from test_surface_continuity import DEFAULT, add, cross, dot, f32, length, mul, sub, vsub

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'shaderpacks/common/shaders/include/shaderlod/surface_region_continuity.glsl'
FROZEN_SHA256 = '497c38b723df0255e9b07b5b62e695d644f0d0eb19538f14db5f95846e9cc040'


def region(width=4, slope_x=0, slope_y=0, step=.5, distance=100):
    positions=[(x*step,y*step,-distance+slope_x*x*step+slope_y*y*step)
               for y in range(width) for x in range(width)]
    scale=math.sqrt(slope_x*slope_x+slope_y*slope_y+1)
    normal=(-slope_x/scale,-slope_y/scale,1/scale)
    return positions,[normal]*(width*width)


def eligible(width, positions, normals, materials=None, lightmaps=None, protected=None,
             near=32, enabled=True, spread=None, limits=None):
    """Direct reviewed source-order binary32 reference; inactive array suffix ignored."""
    if not enabled or width not in (2,4): return False
    count=width*width
    materials=[0]*count if materials is None else materials
    lightmaps=[(.5,1)]*count if lightmaps is None else lightmaps
    protected=[False]*count if protected is None else protected
    l=copy.deepcopy(DEFAULT if limits is None else limits)
    l={k:tuple(map(f32,v)) if isinstance(v,tuple) else f32(v) for k,v in l.items()}
    if any(not math.isfinite(x) for v in l.values() for x in (v if isinstance(v,tuple) else (v,))): return False
    if not (0<=l['plane_absolute_blocks']<=l['plane_max_blocks']<=2 and
            0<=l['plane_relative']<=.25 and 0<=l['minimum_normal_cosine']<=1 and
            0<=l['maximum_lightmap_delta']<=1 and 0<l['minimum_area_ratio']<=1 and
            all(0<=x<=1 for x in l['maximum_ao_shadow_spread'])): return False
    near=f32(near)
    if not math.isfinite(near) or near<0 or near>65536: return False
    if spread is not None and any(not math.isfinite(f32(s)) or f32(s)<0 or f32(s)>maximum
                                  for s,maximum in zip(spread,l['maximum_ao_shadow_spread'])): return False
    p,n,lm=([tuple(map(f32,v)) for v in values[:count]] for values in (positions,normals,lightmaps))
    for i in range(count):
        if (protected[i] or materials[i]<0 or materials[i]!=materials[0] or
            any(not math.isfinite(x) or abs(x)>65536 for x in p[i]) or
            any(not math.isfinite(x) for x in n[i]) or abs(sub(dot(n[i],n[i]),1))>f32(.001) or
            any(not math.isfinite(x) or x<0 or x>1 for x in lm[i]) or length(p[i])<=near): return False
    footprint=f32(1e20)
    for y in range(width-1):
        for x in range(width-1):
            a=y*width+x; b=a+1; c=a+width; d=c+1
            ab,ac,dc,db=vsub(p[b],p[a]),vsub(p[c],p[a]),vsub(p[c],p[d]),vsub(p[b],p[d])
            lab,lac,ldc,ldb=map(length,(ab,ac,dc,db))
            first,second=cross(ab,ac),cross(dc,db)
            smallest=min(lab,lac,ldc,ldb)
            if (smallest<=f32(.000001) or length(first)<=mul(mul(l['minimum_area_ratio'],lab),lac) or
                length(second)<=mul(mul(l['minimum_area_ratio'],ldc),ldb) or dot(first,second)<=0): return False
            footprint=min(footprint,smallest)
    tolerance=min(l['plane_max_blocks'],add(l['plane_absolute_blocks'],mul(l['plane_relative'],footprint)))
    for i in range(count):
        for j in range(i+1,count):
            delta=vsub(p[j],p[i])
            if (length(delta)<=f32(.000001) or dot(n[i],n[j])<l['minimum_normal_cosine'] or
                abs(dot(delta,n[i]))>tolerance or abs(dot(delta,n[j]))>tolerance or
                any(abs(sub(lm[j][k],lm[i][k]))>l['maximum_lightmap_delta'] for k in range(2))): return False
    return True


class SurfaceRegionContinuityTest(unittest.TestCase):
    def test_frozen_standalone_api_and_glsl41_fixed_bounds(self):
        source=SOURCE.read_text();self.assertEqual(FROZEN_SHA256,hashlib.sha256(SOURCE.read_bytes()).hexdigest())
        clean=re.sub(r'//[^\n]*','',source)
        self.assertNotRegex(clean,r'\b(uniform|sampler\w*|texture\w*|texelFetch|dFdx|dFdy|fwidth|discard)\b')
        self.assertNotRegex(clean,r'#ifn?def\b|#include\b')
        for parameter in ('vec3 positions_view[16]','vec3 normals_view[16]','int material_ids[16]',
                          'vec2 lightmaps[16]','bool protected_members[16]'):
            self.assertIn(parameter,clean)
        self.assertIn('(region_width != 2 && region_width != 4)',clean)
        self.assertIn('for (int i = 0; i < 16;',clean)
        self.assertIn('for (int j = i + 1; j < 16;',clean)
        self.assertIn('for (int y = 0; y < 3;',clean)
        self.assertIn('dot(first_area, second_area) <= 0.0',clean)

    def test_width_one_disabled_invalid_widths_and_smallest_footprint(self):
        p,n=region()
        for width in (-1,0,1,3,5,16,2147483647): self.assertFalse(eligible(width,p,n))
        self.assertFalse(eligible(4,p,n,enabled=False))
        for width in (2,4):
            p,n=region(width,step=.01);p[-1]=(p[-1][0],p[-1][1],p[-1][2]+.02)
            # Width4 must not get a larger tolerance just from region span.
            self.assertFalse(eligible(width,p,n))

    def test_slopes_with_unequal_z_and_seeded_planar_properties(self):
        rng=random.Random(7091)
        for width in (2,4):
            for slope in ((0,0),(.5,.2),(4,-3),(-7,1),(30,15)):
                p,n=region(width,*slope)
                self.assertTrue(eligible(width,p,n),(width,slope))
            for _ in range(120):
                p,n=region(width,rng.uniform(-8,8),rng.uniform(-8,8),rng.uniform(.02,2),rng.uniform(300,2000))
                translation=(rng.uniform(-100,100),rng.uniform(-100,100),0)
                p=[tuple(x+y for x,y in zip(point,translation)) for point in p]
                self.assertTrue(eligible(width,p,n))

    def test_every_receiver_including_interior_is_guarded(self):
        p,n=region();corners=(0,3,12,15)
        for member in range(16):
            positions=copy.deepcopy(p);positions[member]=(p[member][0],p[member][1],p[member][2]+.1)
            if member not in corners:self.assertEqual([p[i] for i in corners],[positions[i] for i in corners])
            self.assertFalse(eligible(4,positions,n),('depth',member))
            materials=[0]*16;materials[member]=1
            self.assertFalse(eligible(4,p,n,materials=materials),('material',member))
            lm=[(.5,1)]*16;lm[member]=(.51,1)
            self.assertFalse(eligible(4,p,n,lightmaps=lm),('lightmap',member))
            nn=list(n);nn[member]=(.2,0,math.sqrt(.96))
            self.assertFalse(eligible(4,p,nn),('normal',member))
            protection=[False]*16;protection[member]=True
            self.assertFalse(eligible(4,p,n,protected=protection),('protected',member))
            near_positions=list(p);near_positions[member]=(0,0,-32)
            self.assertFalse(eligible(4,near_positions,n),('near',member))

    def test_all_pair_normal_agreement_not_only_representative(self):
        p,n=region(step=.01)
        n[5]=(math.sin(.07),0,math.cos(.07));n[6]=(-math.sin(.07),0,math.cos(.07))
        self.assertGreater(dot(n[0],n[5]),f32(.995));self.assertGreater(dot(n[0],n[6]),f32(.995))
        self.assertLess(dot(n[5],n[6]),f32(.995))
        self.assertFalse(eligible(4,p,n))

    def test_all_cells_degenerate_folded_and_coincident_fallback(self):
        p,n=region()
        self.assertFalse(eligible(4,[(0,0,-100)]*16,n))
        self.assertFalse(eligible(4,[(i,0,-100) for i in range(16)],n))
        q=list(p);q[5]=(.5,.5,-100);q[6]=(1,.5,-100);q[9]=(1.5,.5,-100)
        self.assertEqual(0,length(cross(vsub(q[6],q[5]),vsub(q[9],q[5]))))
        self.assertFalse(eligible(4,q,n))
        p2,n2=region(2);p2[3]=(-.1,-.1,-100)
        self.assertFalse(eligible(2,p2,n2)) # Two triangles have opposite winding.
        q=list(p);q[10]=q[5]
        self.assertFalse(eligible(4,q,n))
        p,n=region(step=.0001)
        p=[(x+10000,y,z) for x,y,z in p]
        self.assertFalse(eligible(4,p,n)) # Binary32 horizontal positions collapse.

    def test_every_nonfinite_member_and_invalid_policy_falls_back(self):
        p,n=region()
        for bad in (math.nan,math.inf,-math.inf):
            for member in range(16):
                for axis in range(3):
                    q=list(p);q[member]=tuple(bad if i==axis else v for i,v in enumerate(q[member]))
                    self.assertFalse(eligible(4,q,n))
                    nn=list(n);nn[member]=tuple(bad if i==axis else v for i,v in enumerate(nn[member]))
                    self.assertFalse(eligible(4,p,nn))
                lm=[(.5,1)]*16;lm[member]=(bad,1)
                self.assertFalse(eligible(4,p,n,lightmaps=lm))
            self.assertFalse(eligible(4,p,n,near=bad))
            self.assertFalse(eligible(4,p,n,spread=(bad,0)))
            for name in DEFAULT:
                limits=copy.deepcopy(DEFAULT);limits[name]=(bad,0) if name=='maximum_ao_shadow_spread' else bad
                self.assertFalse(eligible(4,p,n,limits=limits))
        for near in (-1,65537):self.assertFalse(eligible(4,p,n,near=near))
        q=list(p);q[10]=(65537,0,-100);self.assertFalse(eligible(4,q,n))
        nn=list(n);nn[10]=(0,0,2);self.assertFalse(eligible(4,p,nn))
        materials=[0]*16;materials[10]=-1;self.assertFalse(eligible(4,p,n,materials=materials))

    def test_optional_spread_heuristic_thresholds_and_active_prefix(self):
        p,n=region()
        self.assertTrue(eligible(4,p,n,spread=(f32(.10),f32(.025))))
        for spread in ((.10001,0),(0,.02501),(-.01,0),(0,1.01)):
            self.assertFalse(eligible(4,p,n,spread=spread))
        self.assertTrue(eligible(4,p,n,spread=None)) # No cast-shadow certificate.
        p,n=region(2)
        p.extend([(math.nan,math.inf,0)]*12);n.extend([(0,0,0)]*12)
        self.assertTrue(eligible(2,p,n)) # Only first four packed members are active.
        p,n=region()
        tight=copy.deepcopy(DEFAULT);tight['plane_absolute_blocks']=0;tight['plane_relative']=0
        p[5]=(p[5][0],p[5][1],p[5][2]+.005)
        self.assertTrue(eligible(4,p,n));self.assertFalse(eligible(4,p,n,limits=tight))

    def test_actual_iris_guard_and_pinned_glsl41_grammar(self):
        iris=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',hashlib.sha256(iris.read_bytes()).hexdigest())
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';dependencies=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern);dependencies.append(matches[0])
        self.assertEqual('54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505',hashlib.sha256(dependencies[-1].read_bytes()).hexdigest())
        java=r'''
import java.nio.file.*;import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class RegionGrammarProof {
 static void parse(String source) {var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(source);}
 public static void main(String[] a)throws Exception {
  String helper=Files.readString(Path.of(a[0]));var annotated=new OptionAnnotatedSource(helper);
  var refs=annotated.getBooleanDefineReferences().keySet();
  if(!refs.isEmpty())throw new AssertionError("helper references leaked: "+refs);
  if(!annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/region.glsl"),refs).getBooleanOptions().isEmpty())throw new AssertionError("helper options leaked");
  for(int width:new int[]{1,2,4}) {
   String main="\nvoid main(){vec3 p[16];vec3 n[16];int m[16];vec2 l[16];bool b[16];"+
    "for(int i=0;i<16;++i){p[i]=vec3(float(i%4),float(i/4),-100.0);n[i]=vec3(0,0,1);m[i]=0;l[i]=vec2(.5,1);b[i]=false;}"+
    "bool ok=shaderlod_surface_region_eligible(true,"+width+",p,n,m,l,b,32.0,false,vec2(0.0),shaderlod_region_default_limits());gl_FragColor=vec4(float(ok));}";
   String selected=JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+helper+"\n"+helper+main,List.of());
   if(selected.split("struct ShaderlodRegionLimits",-1).length!=2)throw new AssertionError("include guard failed");
   parse(selected);
  }
  String corrupted=helper.replace("ShaderlodRegionLimits limits) {","ShaderlodRegionLimits limits {");
  try {parse(JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+corrupted,List.of()));throw new AssertionError("broken condition parsed");}
  catch(ParseCancellationException expected) {}
  System.out.println("3 GLSL410 width cases parsed; repeated include guard and no Iris option leak; malformed parameter rejected");
 }
}
'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-region-grammar-') as temporary:
            folder=Path(temporary);embedded=[]
            with zipfile.ZipFile(iris) as archive:
                for name in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+name)
                    if name=='glsl-transformer-2.0.1.jar':
                        self.assertEqual('b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f',hashlib.sha256(data).hexdigest())
                        with zipfile.ZipFile(io.BytesIO(data)) as parser:
                            self.assertEqual('11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814',hashlib.sha256(parser.read('io/github/douira/glsl_transformer/parser/EnhancedParser.class')).hexdigest())
                    path=folder/name;path.write_bytes(data);embedded.append(path)
            source=folder/'RegionGrammarProof.java';source.write_text(java)
            result=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*dependencies,*embedded])),str(source),str(SOURCE)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,result.returncode,result.stdout+result.stderr)
            self.assertIn('3 GLSL410 width cases parsed',result.stdout)


if __name__=='__main__':unittest.main()
