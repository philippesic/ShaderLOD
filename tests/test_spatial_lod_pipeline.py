"""Shared spatial kernel: host-executed source plus complete GLSL410 fake adapters.

Host vector shims use float arithmetic, not a driver. GLSLang establishes offline
semantic compilation only; these fixtures do not establish Iris/GL execution.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'shaderpacks/common/shaders/include/shaderlod'
PIPELINE = BASE / 'spatial_pipeline.glsl'

# The host compiles the actual pipeline AND existing region guard without a
# second handwritten algorithm. Only GLSL vectors/builtins and adapter are shims.
HOST_VECTORS = r'''
#include <cmath>
#include <algorithm>
#include <iostream>
#include <limits>
#include <cassert>
template<class T,int N> struct V {
 T x{},y{},z{},w{};
 V()=default;
 explicit V(T a):x(a),y(a),z(a),w(a){}
 V(T a,T b):x(a),y(b){}
 V(T a,T b,T c):x(a),y(b),z(c){}
 V(T a,T b,T c,T d):x(a),y(b),z(c),w(d){}
 template<class U> explicit V(V<U,N> a):x(T(a.x)),y(T(a.y)),z(T(a.z)),w(T(a.w)){}
 T &operator[](int i){return i==0?x:i==1?y:i==2?z:w;}
 T operator[](int i)const{return i==0?x:i==1?y:i==2?z:w;}
};
using vec2=V<float,2>;using vec3=V<float,3>;using vec4=V<float,4>;
using ivec2=V<int,2>;using bvec2=V<bool,2>;
#define BIN(OP) template<class T,int N> V<T,N> operator OP(V<T,N>a,V<T,N>b){V<T,N>r;for(int i=0;i<N;++i)r[i]=a[i] OP b[i];return r;} \
 template<class T,int N> V<T,N> operator OP(V<T,N>a,T b){return a OP V<T,N>(b);}
BIN(+) BIN(-) BIN(*) BIN(/)
ivec2 operator%(ivec2 a,int b){return ivec2(a.x%b,a.y%b);}
#define CMP(NAME,OP) template<class T,int N> V<bool,N> NAME(V<T,N>a,V<T,N>b){V<bool,N>r;for(int i=0;i<N;++i)r[i]=a[i] OP b[i];return r;}
CMP(equal,==) CMP(notEqual,!=) CMP(greaterThan,>) CMP(greaterThanEqual,>=)
CMP(lessThan,<) CMP(lessThanEqual,<=)
template<int N> bool any(V<bool,N>a){for(int i=0;i<N;++i)if(a[i])return true;return false;}
template<int N> bool all(V<bool,N>a){for(int i=0;i<N;++i)if(!a[i])return false;return true;}
float min(float a,float b){return std::min(a,b);}
float dot(vec3 a,vec3 b){return a.x*b.x+a.y*b.y+a.z*b.z;}
float length(vec3 a){return std::sqrt(dot(a,a));}
vec3 cross(vec3 a,vec3 b){return vec3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x);}
template<int N> V<float,N> abs(V<float,N>a){for(int i=0;i<N;++i)a[i]=abs(a[i]);return a;}
using std::isnan;using std::isinf;
template<int N> V<bool,N> isnan(V<float,N>a){V<bool,N>r;for(int i=0;i<N;++i)r[i]=std::isnan(a[i]);return r;}
template<int N> V<bool,N> isinf(V<float,N>a){V<bool,N>r;for(int i=0;i<N;++i)r[i]=std::isinf(a[i]);return r;}
const int SHADERLOD_WORK_POLICY=0, SHADERLOD_WORK_QUALITY_FALLBACK=8;
struct ShaderlodSpatialPlan {vec3 weights;float requested_ratio;float reconstruction_ratio;float evaluation_ratio;
 bool evaluate_full;bool evaluate_medium;bool evaluate_far;int reason;};
'''

HOST_ADAPTER = r'''
ShaderlodSpatialPlan active_plan;
ivec2 faulty_pixel(-1), last_representative(-1), last_cache_coordinate(-1);
int last_cache_side=0;
int surface_reads=0, evaluations=0, cache_reads=0, fault_kind=0;
float last_plan_distance=0;
ShaderlodSpatialCache cache2,cache4;
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 p){
 ++surface_reads;
 ShaderlodSpatialSurface s(vec3(float(p.x)*.5f,float(p.y)*.5f,-100.f),vec3(0.f,0.f,1.f),0,vec2(.5f,1.f),false);
 if(all(equal(p,faulty_pixel))){
  if(fault_kind==1)s.factorization_class=1;
  if(fault_kind==2)s.position_view_blocks=vec3(0.f,0.f,-32.f);
  if(fault_kind==3)s.protected_receiver=true;
  if(fault_kind==4)s.position_view_blocks.z+=1.f;
 }
 return s;
}
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2,int){return ShaderlodSpatialSpread(false,vec2(0.f));}
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float d){last_plan_distance=d;return active_plan;}
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2 p,ShaderlodSpatialSurface){
 ++evaluations;last_representative=p;return vec4(float(p.x+1),float(p.y+2),4.f,8.f);
}
ShaderlodSpatialCache shaderlod_spatial_adapter_cache(int side,ivec2 tile){++cache_reads;last_cache_side=side;last_cache_coordinate=tile;return side==2?cache2:cache4;}
ShaderlodSpatialPlan plan(vec3 w){return ShaderlodSpatialPlan(w,1.f,1.f,1.f,w.x>0.f,w.y>0.f,w.z>0.f,0);}
void reset(vec3 w){active_plan=plan(w);faulty_pixel=ivec2(-1);fault_kind=0;surface_reads=evaluations=cache_reads=0;}
void require(bool ok,const char*name){if(!ok){std::cerr<<name<<"\n";std::exit(1);}}
int main(){
 auto limits=shaderlod_region_default_limits();ivec2 size(12,12);int key=17,epoch=123456789;
 require(all(equal(shaderlod_spatial_cache_dimensions(ivec2(9,7),2),ivec2(5,4))),"half odd allocation");
 require(all(equal(shaderlod_spatial_cache_dimensions(ivec2(9,7),4),ivec2(3,2))),"quarter odd allocation");
 for(int side:{2,4}){
  reset(side==2?vec3(0.f,1.f,0.f):vec3(0.f,0.f,1.f));
  auto c=shaderlod_spatial_produce(true,true,side,ivec2(1,1),size,32.f,limits,key,epoch);
  require(c.valid&&evaluations==1&&surface_reads==side*side,"all member reads and one representative");
  float nearest=length(vec3(float(side)*.5f,float(side)*.5f,-100.f));
  require(c.minimum_distance_blocks==nearest&&last_plan_distance==nearest,"minimum distance across all tile members");
  require(all(equal(last_representative,ivec2(side+(side-1)/2))),"fixed representative coordinate");
  if(side==2)cache2=c;else cache4=c;
  evaluations=surface_reads=cache_reads=0;
  auto r=shaderlod_spatial_resolve(true,ivec2(side+1),size,active_plan,32.f,limits,key,epoch);
  require(!r.full_resolution_required&&evaluations==0&&surface_reads==0&&cache_reads==1,"cached resolve avoids evaluation and region reads");
  require(r.linear_quantity.x==c.linear_quantity.x,"same tile reconstruction");
  require(last_cache_side==side&&all(equal(last_cache_coordinate,ivec2(1))),"resolve integer tile coordinate");
  evaluations=0;
  r=shaderlod_spatial_resolve(true,ivec2(side+1),size,active_plan,32.f,limits,key,epoch+1);
  require(r.full_resolution_required&&evaluations==0,"changed snapshot invalidates certificate");
  reset(side==2?vec3(0.f,1.f,0.f):vec3(0.f,0.f,1.f));
  c=shaderlod_spatial_produce(true,true,side,ivec2(2,1),ivec2(side*2+1,side*2),32.f,limits,key,epoch);
  require(!c.valid&&surface_reads==0&&evaluations==0,"odd edge rejects before callback");
  for(int kind:{1,2,3,4})for(int i=0;i<side*side;++i){
   reset(side==2?vec3(0.f,1.f,0.f):vec3(0.f,0.f,1.f));
   fault_kind=kind;faulty_pixel=ivec2(i%side,i/side);
   c=shaderlod_spatial_produce(true,true,side,ivec2(0),size,32.f,limits,key,epoch);
   require(!c.valid&&evaluations==0,"every member mixed/near/protected/plane fallback");
  }
 }
 reset(vec3(.5f,.5f,0.f));
 cache2=shaderlod_spatial_produce(true,true,2,ivec2(1),size,32.f,limits,key,epoch);
 evaluations=surface_reads=0;
 auto r=shaderlod_spatial_resolve(true,ivec2(3),size,active_plan,32.f,limits,key,epoch);
 require(!r.full_resolution_required&&evaluations==1&&surface_reads==1&&r.linear_quantity.x==3.5f,"full half transition weights");
 reset(vec3(0.f,.25f,.75f));
 cache2=shaderlod_spatial_produce(true,true,2,ivec2(2),size,32.f,limits,key,epoch);
 cache4=shaderlod_spatial_produce(true,true,4,ivec2(1),size,32.f,limits,key,epoch);
 evaluations=cache_reads=surface_reads=0;
 r=shaderlod_spatial_resolve(true,ivec2(5),size,active_plan,32.f,limits,key,epoch);
 require(!r.full_resolution_required&&evaluations==0&&surface_reads==0&&cache_reads==2&&r.linear_quantity.x==5.75f,"half quarter tier blend");
 for(int kind=0;kind<9;++kind){
  cache4=ShaderlodSpatialCache(vec4(1.f),true,key,epoch,4,ivec2(1),100.f);
  if(kind==0)cache4.valid=false;if(kind==1)cache4.epoch--;
  if(kind==2)cache4.quantity_key++;if(kind==3)cache4.linear_quantity.x=std::numeric_limits<float>::quiet_NaN();
  if(kind==4)cache4.linear_quantity.w=std::numeric_limits<float>::infinity();
  if(kind==5)cache4.side=2;if(kind==6)cache4.tile_coordinate=ivec2(0);
  if(kind==7)cache4.minimum_distance_blocks=32.f;
  if(kind==8)cache4.minimum_distance_blocks=std::numeric_limits<float>::infinity();
  evaluations=0;r=shaderlod_spatial_resolve(true,ivec2(5),size,active_plan,32.f,limits,key,epoch);
  require(r.full_resolution_required&&evaluations==0,"invalid cache full fallback before full eval");
 }
 reset(vec3(1.f,0.f,0.f));
 r=shaderlod_spatial_resolve(true,ivec2(3),size,active_plan,32.f,limits,key,epoch);
 require(r.full_resolution_required&&surface_reads==0&&cache_reads==0&&evaluations==0,"disabled full baseline no kernel work");
 r=shaderlod_spatial_resolve(true,ivec2(-1),size,active_plan,32.f,limits,key,epoch);require(r.full_resolution_required,"out of bounds full fallback");
 r=shaderlod_spatial_resolve(false,ivec2(3),size,plan(vec3(0.f,1.f,0.f)),32.f,limits,key,epoch);
 require(r.full_resolution_required&&surface_reads==0&&cache_reads==0,"unsupported certificate storage full fallback");
 auto c=shaderlod_spatial_produce(false,true,4,ivec2(0),size,32.f,limits,key,epoch);
 require(!c.valid&&surface_reads==0&&evaluations==0,"disabled producer baseline");
 c=shaderlod_spatial_produce(true,false,4,ivec2(0),size,32.f,limits,key,epoch);
 require(!c.valid&&surface_reads==0&&evaluations==0,"unsupported producer certificate format");
 for(int side:{-1,0,1,3,5}){c=shaderlod_spatial_produce(true,true,side,ivec2(0),size,32.f,limits,key,epoch);require(!c.valid,"illegal side");}
 active_plan=plan(vec3(.5f,.5f,0.f));active_plan.weights.y=std::numeric_limits<float>::infinity();
 r=shaderlod_spatial_resolve(true,ivec2(0),size,active_plan,32.f,limits,key,epoch);require(r.full_resolution_required,"invalid plan");
 std::cout<<"pipeline source executed: 2/4 tiles, all 80 mixed/near/protected/plane faults, odd edges, transitions, invalid cache, full fallback\n";
}
'''

GLSL_ADAPTER = r'''
ShaderlodSpatialPlan shaderlod_spatial_adapter_plan(float d) {
 ShaderlodWorkPolicy p=ShaderlodWorkPolicy(2,2,2,true,2,false,true,vec3(32,96,8),vec2(.25,.0625));
 return shaderlod_spatial_plan_decide(p,true,true,false,d,7);
}
ShaderlodSpatialSurface shaderlod_spatial_adapter_surface(ivec2 p) {
 return ShaderlodSpatialSurface(vec3(vec2(p)*.5,-120.0),vec3(0,0,1),0,vec2(.5,1),false);
}
ShaderlodSpatialSpread shaderlod_spatial_adapter_spread(ivec2 p,int side) {
 return ShaderlodSpatialSpread(false,vec2(0));
}
vec4 shaderlod_spatial_adapter_evaluate_linear(ivec2 p,ShaderlodSpatialSurface s) {
 return vec4(1.0+.01*float(p.x),2,3,0); // declared albedo-independent toy irradiance
}
ShaderlodSpatialCache shaderlod_spatial_adapter_cache(int side,ivec2 tile) {
 // Fake stored current-frame entry; real adapter reads prior producer attachments.
 return ShaderlodSpatialCache(vec4(1,2,3,0),true,17,123456789,side,tile,120.0);
}
layout(location=0) out vec4 output_quantity;
void main() {
 ivec2 pixel=ivec2(gl_FragCoord.xy);ivec2 dimensions=ivec2(9,7);
 ShaderlodRegionLimits limits=shaderlod_region_default_limits();
#if PIPELINE_STAGE == 0
 ShaderlodSpatialCache c=shaderlod_spatial_produce(true,true,2,pixel,dimensions,32.0,limits,17,123456789);
 output_quantity=c.valid?c.linear_quantity:vec4(0);
#elif PIPELINE_STAGE == 1
 ShaderlodSpatialCache c=shaderlod_spatial_produce(true,true,4,pixel,dimensions,32.0,limits,17,123456789);
 output_quantity=c.valid?c.linear_quantity:vec4(0);
#else
 ShaderlodSpatialPlan plan=shaderlod_spatial_adapter_plan(120.0);
 ShaderlodSpatialResult r=shaderlod_spatial_resolve(true,pixel,dimensions,plan,32.0,limits,17,123456789);
 // Full path is untouched adapter shading. Composition inputs remain per pixel.
 vec3 albedo=vec3(.2+.01*float(pixel.x),.4,.8);
 if(r.full_resolution_required) {
  vec3 original=albedo*shaderlod_spatial_adapter_evaluate_linear(pixel,shaderlod_spatial_adapter_surface(pixel)).rgb;
  output_quantity=vec4(original,1);
 } else output_quantity=vec4(albedo*r.linear_quantity.rgb,1);
#endif
}
'''


def expanded(name):
    source = (BASE / name).read_text()
    return re.sub(r'^#include "/include/shaderlod/([^"\n]+)"\s*$',
                  lambda match: expanded(match[1]), source, flags=re.M)


class SpatialPipelineTest(unittest.TestCase):
    def test_owned_scope_and_sampler_free_kernel(self):
        source = PIPELINE.read_text()
        clean = re.sub(r'//[^\n]*', '', source)
        self.assertNotRegex(clean, r'\b(uniform|sampler\w*|texture\w*|texelFetch|dFdx|dFdy|fwidth|discard)\b')
        self.assertIn('SPDX-License-Identifier: MIT', source)
        self.assertIn('shaderlod_surface_region_eligible(true, side', source)
        self.assertLess(source.index('if (!tile.eligible) return result'),
                        source.index('vec4 quantity = shaderlod_spatial_adapter_evaluate_linear'))

    def test_actual_pipeline_and_region_source_host_execution(self):
        compiler = shutil.which('c++')
        self.assertIsNotNone(compiler, 'C++ compiler required for actual owned-source fixtures')
        source = HOST_VECTORS + (BASE / 'surface_region_continuity.glsl').read_text() + PIPELINE.read_text() + HOST_ADAPTER
        # GLSL accepts unsuffixed floating constants as float; enforce that on host.
        source = re.sub(r'(?<![\w.])(\d+\.\d*(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)(?![\w.])', r'\1f', source)
        with tempfile.TemporaryDirectory(prefix='shaderlod-spatial-host-') as temporary:
            folder = Path(temporary); path = folder / 'pipeline.cpp'; path.write_text(source)
            result = subprocess.run([compiler, '-std=c++20', '-O0', str(path), '-o', str(folder / 'pipeline')], capture_output=True, text=True, timeout=30)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            result = subprocess.run([str(folder / 'pipeline')], capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertIn('pipeline source executed', result.stdout)

    def test_complete_fake_adapter_glsl410_semantic_compile(self):
        compiler = shutil.which('glslangValidator')
        self.assertIsNotNone(compiler, 'glslangValidator required for complete semantic fixtures')
        sources = '\n'.join(expanded(name) for name in ('work_policy.glsl', 'spatial_plan.glsl', 'surface_region_continuity.glsl', 'spatial_pipeline.glsl'))
        # Repeated inclusion also checks the option-safe guards.
        sources += '\n' + PIPELINE.read_text()
        with tempfile.TemporaryDirectory(prefix='shaderlod-spatial-glsl-') as temporary:
            for stage in (0, 1, 2):
                path = Path(temporary) / f'fake-adapter-{stage}.frag'
                path.write_text(f'#version 410 core\n#define PIPELINE_STAGE {stage}\n' + sources + GLSL_ADAPTER)
                result = subprocess.run([compiler, '-S', 'frag', str(path)], capture_output=True, text=True, timeout=15)
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
