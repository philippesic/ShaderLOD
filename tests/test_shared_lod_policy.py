"""Shared contract fixtures: source-order binary32, Java oracle, pinned Iris grammar.

The restricted interpreter evaluates owned source statements, not a GL driver.
Actual GLSL arithmetic/contraction, consumers, quality and GPU cost remain unverified.
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
from types import SimpleNamespace

from test_effect_policy_glsl import F32, ScalarSourceFixture

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'shaderpacks/common/shaders/include/shaderlod'
WORK = BASE / 'work_policy.glsl'
RATE = BASE / 'shading_rate.glsl'
FIELDS = {
    'ShaderlodWorkPolicy': 'core_version effects_version minimum_effects_version global_enabled mode inherited_policy_resolved policy_enabled bands_blocks retained_ratios'.split(),
    'ShaderlodWorkDecision': 'upstream_work requested_work realized_work retained_ratio continuous_requested_work continuous_work medium_blend far_blend reason'.split(),
    'ShaderlodShadingRateDecision': 'requested_rate realized_rate requested_work realized_work requested_ratio realized_ratio reason'.split(),
}


def vec(*values):
    return SimpleNamespace(**dict(zip('xyzw', map(F32, values))))


def policy(**overrides):
    values = dict(core_version=2, effects_version=2, minimum_effects_version=1,
                  global_enabled=True, mode=2, inherited_policy_resolved=False,
                  policy_enabled=True, bands_blocks=vec(32, 96, 8), retained_ratios=vec(.5, .25))
    values.update(overrides)
    return SimpleNamespace(**values)


class SharedSourceFixture:
    """Limited GLSL statement interpreter; rejects unknown syntax rather than skipping it."""
    def __init__(self):
        old = ScalarSourceFixture()
        self.environment = dict(old.environment)
        self.environment.update(true=True, false=False)
        for name, fields in FIELDS.items():
            self.environment[name] = lambda *args, fields=fields: SimpleNamespace(**dict(zip(fields, args)))
        self.functions = {}
        self.source = WORK.read_text() + '\n' + RATE.read_text()
        clean = re.sub(r'//[^\n]*', '', self.source)
        for name, value in re.findall(r'const int (\w+) = (\d+);', clean):
            self.environment[name] = int(value)
        for match in re.finditer(r'(bool|Shaderlod\w+) (shaderlod_\w+)\((.*?)\)\s*\{', clean, re.S):
            kind, name, parameters = match.groups()
            end = self.close(clean, match.end() - 1, '{', '}')
            self.functions[name] = (kind, [p.split() for p in parameters.split(',')], clean[match.end():end])
            self.environment[name] = lambda *args, name=name: self.call(name, *args)
        if len(self.functions) != 4:
            raise AssertionError('Expected the four explicit contract functions')

    @staticmethod
    def close(source, start, left, right):
        depth = 0
        for index in range(start, len(source)):
            depth += source[index] == left
            depth -= source[index] == right
            if depth == 0:
                return index
        raise AssertionError('Unbalanced owned source')

    def expr(self, source, scope):
        source = source.replace('&&', ' and ').replace('||', ' or ')
        source = re.sub(r'!(?!=)', ' not ', source)
        return eval(' '.join(source.split()), {'__builtins__': {}}, scope)

    def run(self, body, scope):
        while body.strip():
            body = body.lstrip()
            if body.startswith('if'):
                start = body.index('('); end = self.close(body, start, '(', ')')
                condition = self.expr(body[start+1:end], scope)
                tail = body[end+1:].lstrip()
                if tail.startswith('{'):
                    close = self.close(tail, 0, '{', '}')
                    selected, body = tail[1:close], tail[close+1:]
                else:
                    close = tail.index(';')
                    selected, body = tail[:close+1], tail[close+1:]
                if condition:
                    returned, value = self.run(selected, scope)
                    if returned:
                        return True, value
                continue
            end = body.index(';'); statement, body = body[:end].strip(), body[end+1:]
            if statement.startswith('return '):
                return True, self.expr(statement[7:], scope)
            assignment = re.fullmatch(r'(?:(float|int|Shaderlod\w+)\s+)?(\w+(?:\.\w+)?)\s*=\s*(.*)', statement, re.S)
            if not assignment:
                raise AssertionError(f'Unsupported GLSL statement: {statement}')
            kind, name, expression = assignment.groups()
            value = self.expr(expression, scope)
            if kind == 'float': value = F32(value)
            if kind == 'int': value = int(value)
            if '.' in name:
                obj, attribute = name.split('.')
                setattr(scope[obj], attribute, value)
            else:
                scope[name] = value
        return False, None

    def call(self, name, *args):
        _, params, body = self.functions[name]
        if len(params) != len(args): raise AssertionError(name)
        scope = dict(self.environment)
        for (kind, key), value in zip(params, args):
            scope[key] = F32(value) if kind == 'float' else copy.deepcopy(value)
        returned, value = self.run(body, scope)
        if not returned: raise AssertionError(f'No return: {name}')
        return value


class SharedLodPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls): cls.fixture = SharedSourceFixture()

    def decide(self, distance=1000, upstream=64, minimum=1, p=None, flags=(True, True, False)):
        return self.fixture.call('shaderlod_work_decide', p or policy(), *flags, distance, upstream, minimum)

    def rate(self, ratio, members=16, mask=7, minimum=0, valid=True, protected=False):
        work = self.decide(upstream=members, minimum=minimum,
                           p=policy(retained_ratios=vec(ratio, ratio)))
        return self.fixture.call('shaderlod_shading_rate_decide', work, members, mask, valid, protected)

    def test_reuses_existing_policy_without_transport_or_sampling(self):
        source = self.fixture.source
        self.assertIn('#include "/include/shaderlod/effect_policy.glsl"', source)
        self.assertIn('shaderlod_effect_retained_ratio(', source)
        clean = re.sub(r'//[^\n]*', '', source)
        self.assertNotRegex(clean, r'\b(uniform|sampler\w*|texture\w*|texelFetch|dFdx|dFdy|fwidth|discard)\b')
        self.assertNotIn('Photon', clean)
        self.assertNotRegex(clean, r'\b(32\.0|96\.0|512\.0|4096\.0)\b')
        self.assertNotIn('umulExtended', clean)

    def test_java_distance_endpoints_and_continuous_volume_target(self):
        expected = [(0,1,64),(28,1,64),(30,.921875,59),(32,.75,48),
                    (36,.5,32),(92,.5,32),(96,.375,24),(100,.25,16)]
        for distance, ratio, count in expected:
            decision = self.decide(distance)
            self.assertEqual((ratio, count, 0), (decision.retained_ratio, decision.realized_work, decision.reason))
        decision = self.decide(96, upstream=9, minimum=1)
        self.assertEqual(3.375, decision.continuous_work)
        self.assertEqual(4, decision.realized_work)
        # A volume consumes continuous_work, retaining the last partial cell, not 4 equal cells.

    def test_protocol_modes_capability_and_quality_fail_full(self):
        for key, values in [('core_version',(-1,0,1,3,2147483647)),
                            ('effects_version',(-1,0,3)), ('minimum_effects_version',(0,3)),
                            ('mode',(-1,3,2147483647))]:
            for value in values:
                d=self.decide(p=policy(**{key:value}));self.assertEqual((64,1),(d.realized_work,d.reason))
        self.assertEqual(0,self.decide(p=policy(effects_version=1)).reason)
        self.assertEqual(1,self.decide(p=policy(effects_version=1,minimum_effects_version=2)).reason)
        for p, flags, reason in [(policy(global_enabled=False),(True,True,False),2),
                (policy(mode=1),(True,True,False),3),(policy(mode=0),(True,True,False),4),
                (policy(policy_enabled=False),(True,True,False),5),
                (policy(),(True,True,True),6),(policy(),(False,True,False),7),
                (policy(),(True,False,False),8)]:
            d=self.decide(p=p,flags=flags)
            self.assertEqual((64,64,1,64),(d.requested_work,d.realized_work,d.retained_ratio,d.continuous_work))
            self.assertEqual(reason,d.reason)
        self.assertEqual(16,self.decide(p=policy(mode=0,inherited_policy_resolved=True)).realized_work)

    def test_float32_invalid_boundaries_and_integer_floors(self):
        for distance in (-1,math.nan,math.inf,-math.inf,16777218):
            self.assertEqual(64,self.decide(distance).realized_work)
        for bands in ((0,96,8),(32,32,0),(32,96,-1),(32,96,65),(32,96,.0001),
                      (32,4097,8),(32,96,math.nan),(32,math.inf,8)):
            self.assertEqual(1,self.decide(p=policy(bands_blocks=vec(*bands))).reason)
        for ratios in ((-1,0),(1.1,1),(.25,.5),(1,math.nan)):
            self.assertEqual(1,self.decide(p=policy(retained_ratios=vec(*ratios))).reason)
        for upstream,minimum in ((-1,0),(16777217,0),(2147483647,1),(64,-1),(64,65)):
            self.assertEqual(upstream,self.decide(upstream=upstream,minimum=minimum).realized_work)
        for upstream,minimum,ratio,expected in ((9,4,.25,4),(9,0,0,0),(9,1,0,1),
                                               (0,0,0,0),(16777216,0,.25,4194304)):
            d=self.decide(upstream=upstream,minimum=minimum,p=policy(retained_ratios=vec(ratio,ratio)))
            self.assertEqual(expected,d.realized_work)
        for distance,expected in ((32-1/2048,1),(32,.75),(32+1/2048,.5)):
            self.assertEqual(expected,self.decide(distance,p=policy(bands_blocks=vec(32,96,1/1024))).retained_ratio)
        self.assertEqual(.5,self.decide(32,p=policy(bands_blocks=vec(32,96,0))).retained_ratio)
        self.assertEqual(.5,self.decide(64,p=policy(bands_blocks=vec(32,96,64))).retained_ratio)

    def test_legal_rate_negotiation_and_no_transition_snapping(self):
        for ratio,width,count in ((1,1,16),(.25,2,4),(.0625,4,1)):
            d=self.rate(ratio);self.assertEqual((width,width,count,0),(d.requested_rate,d.realized_rate,d.realized_work,d.reason))
        for ratio in (0,.05,.1,.249,.5,.75,.999):
            d=self.rate(ratio);self.assertEqual((0,1,16,7),(d.requested_rate,d.realized_rate,d.realized_work,d.reason))
        for args in (dict(ratio=.25,mask=5),dict(ratio=.0625,mask=3),dict(ratio=.0625,members=4),
                     dict(ratio=.25,minimum=5)):
            self.assertEqual((1,7),(self.rate(**args).realized_rate,self.rate(**args).reason))
        for mask in (-1,0,2,4,8):self.assertEqual(1,self.rate(.25,mask=mask).reason)
        self.assertEqual(8,self.rate(.25,valid=False).reason)
        self.assertEqual(6,self.rate(.25,protected=True).reason)
        for members in (-1,0,2,3,5,15,17):self.assertEqual(1,self.rate(.25,members=members).reason)
        self.assertEqual(4,self.rate(.25,mask=3).requested_work)
        self.assertEqual(16,self.rate(.25,mask=5).realized_work)

    def test_source_against_actual_dependency_free_java_oracle(self):
        rng=random.Random(1781)
        cases=[(d,u,0,32,96,8,.5,.25) for d in (0,28,30,32,36,92,96,100) for u in (0,1,9,64,65535,16777216)]
        cases += [(rng.uniform(0,128),rng.randrange(1,65536),0,32,96,8,.7,.2) for _ in range(120)]
        cases += [(1000,9,4,32,96,8,.5,.25),(32,64,1,32,96,0,.5,.25)]
        # Java evaluates the exact transported binary32 scalar values in double.
        cases=[tuple(float(F32(v)) if i in (0,3,4,5,6,7) else v for i,v in enumerate(case)) for case in cases]
        java='''import com.pp.shaderlod.lod.*; class SharedOracle {public static void main(String[] a) {'''
        for d,u,f,n,far,w,m,r in cases:
            java+=f'{{var b=new EffectPolicy(true,false,new DistanceBands({n},{far},{w}),{m},{r}).decide(true,Effect.VOLUMETRICS,{d},{u},{f});System.out.println(b.retentionRatio()+","+b.workLimit());}}'
        java+='}}'
        with tempfile.TemporaryDirectory(prefix='shaderlod-policy-oracle-') as temporary:
            folder=Path(temporary);src=folder/'SharedOracle.java';src.write_text(java)
            sources=[ROOT/'src/main/java/com/pp/shaderlod/lod'/f'{name}.java' for name in ('DistanceBands','EffectPolicy','Effect','WorkBudget')]
            compiled=subprocess.run(['javac','-d',str(folder),*map(str,sources),str(src)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,compiled.returncode,compiled.stderr)
            ran=subprocess.run(['java','-cp',str(folder),'SharedOracle'],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,ran.returncode,ran.stderr)
        self.assertEqual(len(cases),len(ran.stdout.splitlines()))
        for case,line in zip(cases,ran.stdout.splitlines()):
            d,u,f,n,far,w,m,r=case
            actual=self.decide(d,u,f,policy(bands_blocks=vec(n,far,w),retained_ratios=vec(m,r)))
            ratio,count=line.split(',')
            self.assertAlmostEqual(float(ratio),actual.retained_ratio,delta=2e-7)
            self.assertLessEqual(abs(int(count)-actual.realized_work),1)
            self.assertGreaterEqual(actual.realized_work,f);self.assertLessEqual(actual.realized_work,u)

    def test_actual_pinned_iris_preprocess_glsl410_and_no_options(self):
        iris=ROOT/'dependencies/runtime/iris-neoforge-1.8.12+mc1.21.1.jar'
        self.assertEqual('3e3f94d2d5cdcb8145c4385d521974aa2d8669b2cc212351f70cf06488182e83',hashlib.sha256(iris.read_bytes()).hexdigest())
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1';deps=[]
        for pattern in ('com.google.guava/guava/32.1.2-jre/*/guava-32.1.2-jre.jar',
                        'it.unimi.dsi/fastutil/8.5.12/*/fastutil-8.5.12.jar',
                        'org.slf4j/slf4j-api/2.0.9/*/slf4j-api-2.0.9.jar',
                        'org.antlr/antlr4-runtime/4.13.1/*/antlr4-runtime-4.13.1.jar'):
            matches=list(cache.glob(pattern));self.assertEqual(1,len(matches),pattern);deps.append(matches[0])
        self.assertEqual('54665d2838cc66458343468efc539e454fc95b46a8a04b13c6ac43fc9be63505',hashlib.sha256(deps[-1].read_bytes()).hexdigest())
        java=r'''
import java.nio.file.*;import java.util.*;
import net.irisshaders.iris.shaderpack.option.OptionAnnotatedSource;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.preprocessor.JcppProcessor;
import io.github.douira.glsl_transformer.parser.EnhancedParser;
import org.antlr.v4.runtime.misc.ParseCancellationException;
class SharedGrammarProof {
 static void parse(String s){var p=new EnhancedParser();p.setThrowParseErrors(true);p.parse(s);}
 public static void main(String[] a)throws Exception{
  String source=Files.readString(Path.of(a[0]));var annotated=new OptionAnnotatedSource(source);
  var refs=annotated.getBooleanDefineReferences().keySet();var options=annotated.getOptionSet(AbsolutePackPath.fromAbsolutePath("/policy.glsl"),refs);
  if(!refs.isEmpty()||!options.getBooleanOptions().isEmpty()||!options.getStringOptions().isEmpty())throw new AssertionError("helper option leak");
  for(int mode:new int[]{0,1,2})for(int version:new int[]{1,2})for(int width:new int[]{1,2,4}) {
   String main="\nvoid main(){ShaderlodWorkPolicy p=ShaderlodWorkPolicy(2,"+version+",1,true,"+mode+",true,true,vec3(32,96,8),vec2(.25,.0625));"+
    "ShaderlodWorkDecision w=shaderlod_work_decide(p,true,true,false,128.0,"+(width*width)+",0);"+
    "ShaderlodShadingRateDecision r=shaderlod_shading_rate_decide(w,"+(width*width)+",7,true,false);gl_FragColor=vec4(w.continuous_work,float(r.realized_rate),w.retained_ratio,1);}";
   String processed=JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+source+"\n"+source+main,List.of());
   if(processed.split("struct ShaderlodWorkPolicy",-1).length!=2)throw new AssertionError("include guard");parse(processed);
  }
  String broken=source.replace("int minimum_work) {","int minimum_work {");
  try{parse(JcppProcessor.glslPreprocessSource("#version 410 compatibility\n"+broken,List.of()));throw new AssertionError("broken syntax accepted");}catch(ParseCancellationException expected){}
  System.out.println("18 GLSL410 graphs parsed; no Iris options; guards and broken-syntax regression passed");
 }
}'''
        with tempfile.TemporaryDirectory(prefix='shaderlod-shared-grammar-') as temporary:
            folder=Path(temporary);embedded=[]
            with zipfile.ZipFile(iris) as archive:
                for name in ('glsl-transformer-2.0.1.jar','jcpp-1.4.14.jar'):
                    data=archive.read('META-INF/jarjar/'+name)
                    if name=='glsl-transformer-2.0.1.jar':
                        self.assertEqual('b8ca5e61b41ae18ddd5d4d5d6785cb9acd8fd7ac0c5c0b0689b872b039c11b9f',hashlib.sha256(data).hexdigest())
                        with zipfile.ZipFile(io.BytesIO(data)) as parser:
                            self.assertEqual('11133ca7cbfbc89777b15f06a31323b26c1ab6448b1822f4086e9503f3527814',hashlib.sha256(parser.read('io/github/douira/glsl_transformer/parser/EnhancedParser.class')).hexdigest())
                    path=folder/name;path.write_bytes(data);embedded.append(path)
            expanded=WORK.read_text().replace('#include "/include/shaderlod/effect_policy.glsl"',(BASE/'effect_policy.glsl').read_text())+'\n'+RATE.read_text()
            fixture=folder/'expanded.glsl';fixture.write_text(expanded)
            src=folder/'SharedGrammarProof.java';src.write_text(java)
            ran=subprocess.run(['java','--class-path',os.pathsep.join(map(str,[iris,*deps,*embedded])),str(src),str(fixture)],capture_output=True,text=True,timeout=30)
            self.assertEqual(0,ran.returncode,ran.stdout+ran.stderr)
            self.assertIn('18 GLSL410 graphs parsed',ran.stdout)


if __name__=='__main__':unittest.main()
