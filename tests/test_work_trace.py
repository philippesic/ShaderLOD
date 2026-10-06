"""General work trace: actual helper executed on host and offline GLSL410 checks.

The temporary C++ shim substitutes only GLSL builtins/types and inout references.
It establishes source counter/codec semantics, not driver execution or GPU cost.
"""
import math
from pathlib import Path
import random
import re
import shutil
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / 'shaderpacks/common/shaders/include/shaderlod/work_trace.glsl'
MAX_EXACT = 16777215


def binary32(value):
    return struct.unpack('f', struct.pack('f', value))[0]


def decode_pages(pages):
    """Independent strict decoder fixture for the documented schema contract."""
    if len(pages) != 3 or any(len(page) != 4 for page in pages):
        raise ValueError('Missing page/channel')
    flat = [value for page in pages for value in page]
    if all(value == -1 for value in flat):
        return None
    if any(not math.isfinite(value) or value < 0 or value > MAX_EXACT for value in flat):
        raise ValueError('Invalid channel or partial sentinel')
    for index in (0, 1, 2, 3, 4, 7, 8, 9, 10, 11):
        if flat[index] != math.floor(flat[index]):
            raise ValueError('Noninteger counter or tag')
    if flat[7] > 3 or pages[2][0] != 1 or pages[2][2:] != [1, 0]:
        raise ValueError('Unsupported path/schema/valid/reserved')
    return dict(primary_iterations=int(flat[0]), density_evaluations=int(flat[1]),
                secondary_visits=int(flat[2]), texture_evaluations=int(flat[3]),
                original_work=int(flat[4]), requested_work=flat[5], selected_work=flat[6],
                selected_ceil=math.ceil(flat[6]), path_tag=int(flat[7]), reason_tag=int(flat[9]))


HOST_SHIM = r'''
#include <cmath>
#include <cstdint>
#include <iostream>
#include <limits>
#include <cstdlib>
using uint = std::uint32_t;
using std::isnan; using std::isinf;
struct vec4 {
 float x,y,z,w;
 explicit vec4(float a):x(a),y(a),z(a),w(a){}
 vec4(float a,float b,float c,float d):x(a),y(b),z(c),w(d){}
};
void require(bool ok,const char*name){if(!ok){std::cerr<<name<<"\n";std::exit(1);}}
bool sentinel(vec4 v){return v.x==-1.f&&v.y==-1.f&&v.z==-1.f&&v.w==-1.f;}
'''

HOST_CHECKS = r'''
int main() {
 SHADERLOD_TRACE_DECLARE(trace)
 SHADERLOD_TRACE_INIT(trace);
 require(trace.valid&&trace.path_tag==0u,"zero NONE initialization");
 require(shaderlod_trace_encode(trace,0u).x==0.f,"zero counts");
 SHADERLOD_TRACE_SELECT(trace,40u,30.f,30.f,SHADERLOD_TRACE_PATH_ADAPTED,0u);
 // Forty empty original cells and thirty candidate cells with two occupied hits.
 // A density call is reached at each primary and each of eight secondary visits.
 for(int i=0;i<30;++i){
  SHADERLOD_TRACE_PRIMARY(trace); SHADERLOD_TRACE_DENSITY(trace);
  if(i==0||i==1)for(int j=0;j<8;++j){
   SHADERLOD_TRACE_SECONDARY(trace); SHADERLOD_TRACE_DENSITY(trace);
   SHADERLOD_TRACE_TEXTURE(trace); SHADERLOD_TRACE_TEXTURE(trace);
  }
 }
 require(trace.primary_iterations==30u&&trace.density_evaluations==46u&&
  trace.secondary_visits==16u&&trace.texture_evaluations==32u,"independent reached operations");
 auto p0=shaderlod_trace_encode(trace,0u), p1=shaderlod_trace_encode(trace,1u), p2=shaderlod_trace_encode(trace,2u);
 require(p0.x==30.f&&p0.y==46.f&&p0.z==16.f&&p0.w==32.f,"count page");
 require(p1.x==40.f&&p1.y==30.f&&p1.z==30.f&&p1.w==2.f,"selection page");
 require(p2.x==1.f&&p2.y==0.f&&p2.z==1.f&&p2.w==0.f,"schema page");
 // Requested work never manufactures operations, including arbitrary fractional budgets.
 SHADERLOD_TRACE_SELECT(trace,64u,32.25f,32.25f,3u,8u);
 require(trace.primary_iterations==30u&&trace.density_evaluations==46u,"selection preserves counters");
 p1=shaderlod_trace_encode(trace,1u);require(p1.y==32.25f&&p1.z==32.25f,"fractional request exact");
 SHADERLOD_TRACE_INIT(trace);
 // Early exit immediately after one actual operation versus thirty empty cells.
 SHADERLOD_TRACE_SELECT(trace,40u,40.f,40.f,SHADERLOD_TRACE_PATH_ORIGINAL,0u);
 SHADERLOD_TRACE_PRIMARY(trace); SHADERLOD_TRACE_DENSITY(trace);
 require(trace.primary_iterations==1u&&trace.density_evaluations==1u&&trace.secondary_visits==0u,
  "no expected-budget reconstruction");
 // Exhaust the entire promised float integer range, not just representative values.
 for(uint i=0u;i<=SHADERLOD_TRACE_MAX_EXACT;++i)
  require(uint(float(i))==i,"exact binary32 integer roundtrip");
 for(int field=0;field<4;++field){
  SHADERLOD_TRACE_INIT(trace);
  uint& counter=field==0?trace.primary_iterations:field==1?trace.density_evaluations:
    field==2?trace.secondary_visits:trace.texture_evaluations;
  counter=SHADERLOD_TRACE_MAX_EXACT-1u;
  shaderlod_trace_increment(counter,trace.valid);
  require(counter==SHADERLOD_TRACE_MAX_EXACT&&trace.valid,"last exact count");
  for(uint page=0u;page<3u;++page)require(!sentinel(shaderlod_trace_encode(trace,page)),"max valid encoding");
  shaderlod_trace_increment(counter,trace.valid);
  require(counter==SHADERLOD_TRACE_MAX_EXACT&&!trace.valid,"overflow sticky without wrapping");
  for(uint page=0u;page<3u;++page)require(sentinel(shaderlod_trace_encode(trace,page)),"overflow invalid all pages");
  SHADERLOD_TRACE_SELECT(trace,1u,1.f,1.f,1u,0u);require(!trace.valid,"selection cannot repair overflow");
 }
 for(float bad:{-1.f,16777216.f,std::numeric_limits<float>::infinity(),
      -std::numeric_limits<float>::infinity(),std::numeric_limits<float>::quiet_NaN()}){
  for(int field=0;field<2;++field){
   SHADERLOD_TRACE_INIT(trace); SHADERLOD_TRACE_SELECT(trace,1u,field==0?bad:1.f,field==1?bad:1.f,1u,0u);
   for(uint page=0u;page<3u;++page)require(sentinel(shaderlod_trace_encode(trace,page)),"invalid budget sentinel");
  }
 }
 for(uint bad:{16777216u,4294967295u})for(int field=0;field<2;++field){
  SHADERLOD_TRACE_INIT(trace);SHADERLOD_TRACE_SELECT(trace,field==0?bad:1u,1.f,1.f,1u,field==1?bad:0u);
  require(sentinel(shaderlod_trace_encode(trace,0u)),"invalid integer metadata");
 }
 SHADERLOD_TRACE_INIT(trace);SHADERLOD_TRACE_SELECT(trace,1u,1.f,1.f,4u,0u);
 require(sentinel(shaderlod_trace_encode(trace,0u)),"unknown path rejected");
 SHADERLOD_TRACE_INIT(trace);require(sentinel(shaderlod_trace_encode(trace,3u)),"unknown page rejected");
 trace.density_evaluations=4294967295u;
 require(sentinel(shaderlod_trace_encode(trace,0u)),"corrupted out of range count rejected");
 shaderlod_trace_increment(trace.density_evaluations,trace.valid);
 require(trace.density_evaluations==4294967295u&&!trace.valid,"corruption never wraps to zero");
 SHADERLOD_TRACE_INIT(trace);SHADERLOD_TRACE_INVALIDATE(trace);
 require(sentinel(shaderlod_trace_encode(trace,0u)),"adapter explicit invalidation");
 std::cout<<"actual helper passed counters, all 16777216 exact integers, saturation, metadata and sentinel cases\n";
}
'''


class WorkTraceTest(unittest.TestCase):
    def test_actual_counter_and_codec_source_on_host(self):
        compiler = shutil.which('clang++') or shutil.which('g++')
        self.assertIsNotNone(compiler, 'A host C++ compiler is required; do not skip this gate')
        source = HELPER.read_text()
        source = re.sub(r'\binout\s+(uint|bool|ShaderlodWorkTrace)\s+(\w+)', r'\1& \2', source)
        with tempfile.TemporaryDirectory(prefix='shaderlod-work-trace-') as directory:
            path = Path(directory)
            (path / 'trace.cpp').write_text(HOST_SHIM + '\n#define SHADERLOD_WORK_TRACE\n' + source + HOST_CHECKS)
            result = subprocess.run([compiler, '-std=c++17', '-O2', '-Wall', '-Werror',
                                     str(path / 'trace.cpp'), '-o', str(path / 'trace')], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            result = subprocess.run([str(path / 'trace')], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('all 16777216 exact integers', result.stdout)

    def test_decoder_boundaries_and_malformed_records(self):
        pages = [[0, 1, MAX_EXACT, 46], [40, binary32(29.7), 30.25, 2], [1, 8, 1, 0]]
        record = decode_pages(pages)
        self.assertEqual(31, record['selected_ceil'])
        self.assertEqual(MAX_EXACT, record['secondary_visits'])
        self.assertIsNone(decode_pages([[-1] * 4 for _ in range(3)]))
        for page in range(3):
            for channel in range(4):
                for bad in (-1, math.nan, math.inf, -math.inf, MAX_EXACT + 1):
                    altered = [list(values) for values in pages]
                    altered[page][channel] = bad
                    with self.assertRaises(ValueError): decode_pages(altered)
        for index in (0, 1, 2, 3, 4, 7, 8, 9, 10, 11):
            altered = [list(values) for values in pages]
            altered[index // 4][index % 4] = .5
            with self.assertRaises(ValueError): decode_pages(altered)
        for index, bad in ((7, 4), (8, 2), (10, 0), (11, 1)):
            altered = [list(values) for values in pages]
            altered[index // 4][index % 4] = bad
            with self.assertRaises(ValueError): decode_pages(altered)
        for missing in ([], pages[:1], pages[:2], [pages[0], pages[1], [1, 8, 1]]):
            with self.assertRaises(ValueError): decode_pages(missing)

    def test_binary32_random_counter_roundtrips(self):
        rng = random.Random(171)
        for _ in range(4096):
            counts = [rng.randrange(MAX_EXACT + 1) for _ in range(4)]
            pages = [list(map(binary32, counts)), [MAX_EXACT, binary32(.1), binary32(32.25), 3], [1, MAX_EXACT, 1, 0]]
            decoded = decode_pages(pages)
            self.assertEqual(counts, [decoded[name] for name in
                ('primary_iterations', 'density_evaluations', 'secondary_visits', 'texture_evaluations')])

    def test_production_preprocessor_erases_all_storage_and_arguments(self):
        preprocessor = shutil.which('cpp')
        self.assertIsNotNone(preprocessor, 'cpp required for production erasure check')
        # Deliberately undeclared trace and invalid side-effect expressions must disappear.
        caller = '''
SHADERLOD_TRACE_DECLARE(nonexistent_trace)
void caller() {
 SHADERLOD_TRACE_INIT(nonexistent_trace);
 SHADERLOD_TRACE_SELECT(nonexistent_trace, forbidden_side_effect(), forbidden_side_effect(),
    forbidden_side_effect(), SHADERLOD_TRACE_PATH_ADAPTED, forbidden_side_effect());
 SHADERLOD_TRACE_PRIMARY(nonexistent_trace);
 SHADERLOD_TRACE_DENSITY(nonexistent_trace);
 SHADERLOD_TRACE_SECONDARY(nonexistent_trace);
 SHADERLOD_TRACE_TEXTURE(nonexistent_trace);
 SHADERLOD_TRACE_INVALIDATE(nonexistent_trace);
}
'''
        result = subprocess.run([preprocessor, '-P', '-'], input=HELPER.read_text()+caller,
                                capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotRegex(result.stdout, r'trace|shaderlod|SHADERLOD|forbidden|uint|counter|struct')
        stripped = re.sub(r'//[^\n]*', '', result.stdout)
        self.assertEqual('voidcaller(){}', re.sub(r'[\s;]', '', stripped))

    def test_complete_offline_glsl410_graphs_and_no_transport_leaks(self):
        validator = shutil.which('glslangValidator')
        self.assertIsNotNone(validator, 'Offline GLSL compiler is required; do not skip this gate')
        helper = HELPER.read_text()
        code = re.sub(r'//[^\n]*', '', helper)
        self.assertNotRegex(code, r'\b(uniform|sampler\w*|dFdx|dFdy|fwidth|buffer|atomic\w*)\b')
        self.assertNotRegex(code, r'\btexture\w*\s*\(')
        self.assertNotRegex(code, re.compile(r'^\s*#define SHADERLOD_WORK_TRACE\b', re.M))
        self.assertNotIn('Photon', helper)
        for page in (None, 0, 1, 2, 3):
            declaration = '#define SHADERLOD_WORK_TRACE\n' if page is not None else ''
            body = '''
SHADERLOD_TRACE_DECLARE(trace)
layout(location=0) out vec4 diagnostic_out;
void main(){
 SHADERLOD_TRACE_INIT(trace);
 SHADERLOD_TRACE_SELECT(trace,40u,30.25,30.25,2u,0u);
 SHADERLOD_TRACE_PRIMARY(trace);SHADERLOD_TRACE_DENSITY(trace);
 SHADERLOD_TRACE_SECONDARY(trace);SHADERLOD_TRACE_TEXTURE(trace);
 diagnostic_out = %s;
}
''' % (f'shaderlod_trace_encode(trace,{page}u)' if page is not None else 'vec4(1.0)')
            with tempfile.TemporaryDirectory(prefix='shaderlod-work-trace-glsl-') as directory:
                source = Path(directory) / 'trace.frag'
                source.write_text('#version 410 core\n'+declaration+helper+body)
                result = subprocess.run([validator, '-S', 'frag', str(source)], capture_output=True, text=True)
                self.assertEqual(0, result.returncode, result.stdout+result.stderr)


if __name__ == '__main__':
    unittest.main()
