"""G10 cheaper certificate and native-coordinate reuse bounds, no GL context."""
import sys,unittest,random,subprocess,tempfile,re
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'));sys.path.insert(0,str(ROOT/'scripts'))
import test_makeup_bloom_pairing as old
m=old.m

class BloomReuseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.native=m.read_archive(ROOT/'dependencies/upstream/other-pack-research/MakeUp-UltraFast-9.5g.zip');cls.files=m.patch(cls.native)
    def test_fp32_new_accepted_footprints_all_stay_in_one_native_bilinear_cell(self):
        rng=np.random.default_rng(10029);accepted=0
        for size in [(240,150),(119,75),(1920,1200),(3839,2159),(1,1),(4096,4096)]:
            dims=np.array(size,dtype=np.float32);coords=rng.uniform(0,1,(24000,2)).astype(np.float32);v=rng.uniform(-.7,.7,(24000,2)).astype(np.float32)/dims
            o=np.stack([-v[:,1]*np.float32(1.25),v[:,0]*np.float32(1.25)],axis=1);radius=np.maximum(abs(v),abs(o));center=coords*dims-np.float32(.5);reach=radius*dims;phase=center-np.floor(center)
            allow=np.all((center-reach>np.float32(.02))&(center+reach<dims-np.float32(1.02))&(phase>=reach+np.float32(.02))&(phase<np.float32(.98)-reach),axis=1)
            accepted+=int(allow.sum())
            if not allow.any():continue
            cells=[np.floor((coords[allow]+offset[allow])*dims-np.float32(.5)) for offset in [v,-v,o,-o]]
            for cell in cells[1:]:self.assertTrue(np.array_equal(cells[0],cell))
        self.assertGreater(accepted,1000)
    def test_exact_reuse_retains_native_coordinates_and_addition_order(self):
        src=old.preprocess(self.files,mode=3)
        self.assertNotIn('float g10Level',src)
        for term in ['all(equal(g10A, g10B))','all(equal(g10A, g10C))','all(equal(g10A, g10D))']:self.assertIn(term,src)
        self.assertEqual(src.count('blurSample += g10Sample;'),4)
        self.assertNotIn('g10Sample *',src)
        rng=np.random.default_rng(103);sample=rng.uniform(0,50,(20000,3)).astype(np.float32);prior=rng.uniform(0,300,(20000,3)).astype(np.float32)
        native=prior.copy();reuse=prior.copy()
        for _ in range(4):native+=sample.copy();reuse+=sample
        self.assertTrue(np.array_equal(native.view(np.uint32),reuse.view(np.uint32)))
    def test_new_modes_fallback_native_and_actual_fragment_compile(self):
        with tempfile.TemporaryDirectory() as tmp:
            for mode in [3,4]:
                for opts in [{},{'extra':{'DISTANT_HORIZONS':1}},{'dimension':'world1'}]:
                    src=old.preprocess(self.files,mode=mode,**opts)
                    if opts:self.assertEqual(re.sub(r'\s+',' ',src).strip(),re.sub(r'\s+',' ',old.preprocess(self.native,mode=mode,**opts)).strip())
                    p=Path(tmp)/'shader.frag';p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+src);r=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stdout+r.stderr)

if __name__=='__main__':unittest.main()
