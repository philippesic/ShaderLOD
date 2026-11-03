"""G04 changed mechanism: exact native path extraction and real GLSL checks."""
import importlib.util,sys,subprocess,tempfile,re,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'));sys.path.insert(0,str(ROOT/'tests'))
import build_makeup_translucent_fastpath as m
import test_makeup_unused_sky as old

class TranslucentFastpathTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.native=m.read_archive(old.BASE);cls.on=m.patch(cls.native);cls.cue=m.patch(cls.native,True)
    def preprocess(self,files,option=1,entry='shaders/world0/gbuffers_water.fsh',macros=None):
        # Existing expansion helper takes the original G04 option, so supply
        # this separate option through its native option overrides.
        return old.preprocess(files,entry,0,macros,{m.OPTION:option})
    def test_native_dataflow_and_primitive_class_certificate(self):
        source=self.native[m.CALLER].decode();ordinary,suffix=m.native_parts(source)
        self.assertIn('blockColor = texture2D(tex, texcoord);',ordinary)
        self.assertIn('get_colored_shadow(shadow_real_pos, dither)',ordinary)
        self.assertIn('blockColor.rgb *= mix(realLight',ordinary)
        self.assertEqual(suffix,'    #include "/src/finalcolor.glsl"\n    #include "/src/writebuffers.glsl"')
        vertex=self.native['shaders/common/water_blocks_vertex.glsl'].decode()
        self.assertEqual(re.findall(r'blockType = ([0-9.]+);',vertex),['0.0','3.0','2.0'])
        on=self.on[m.CALLER].decode();begin=on.index('    #if '+m.CONDITION);end=on.index('    #ifdef VANILLA_WATER',begin)
        fast=on[begin:end];self.assertIn(ordinary,fast);self.assertIn(suffix,fast)
        self.assertNotIn('normal_waves(',fast);self.assertNotIn('get_cloud(',fast)
        # Removing the one added early branch recovers the whole original body.
        self.assertEqual(on[:begin]+on[end:],source)
    def test_off_other_entries_and_unsupported_tuple_token_identical(self):
        entries=['shaders/world0/gbuffers_water.fsh','shaders/world0/gbuffers_hand_water.fsh','shaders/world0/gbuffers_water.vsh','shaders/world0/deferred.fsh','shaders/world1/gbuffers_water.fsh','shaders/world-1/gbuffers_water.fsh','shaders/gbuffers_water.fsh']
        for entry in entries:
            with self.subTest(entry=entry):
                a=self.preprocess(self.on,0,entry);b=self.preprocess(self.native,0,entry);self.assertEqual(old.normalized(a),old.normalized(b))
                if entry!=entries[0]:self.assertEqual(old.normalized(self.preprocess(self.on,1,entry)),old.normalized(b))
        for key,value in [('DISTANT_HORIZONS',1),('VOXY',1),('VOXY_PATCH',1),('MC_VERSION',12102),('IRIS_VERSION',10811),('MC_OS_MAC',0)]:
            macros=dict(old.MACROS,**{key:value})
            if key=='MC_OS_MAC':del macros[key]
            with self.subTest(macros=macros):self.assertEqual(old.normalized(self.preprocess(self.on,macros=macros)),old.normalized(self.preprocess(self.native,macros=macros)))
    def test_changed_members_and_licensing_bounded(self):
        self.assertEqual(sorted(n for n in self.native if self.native[n]!=self.on[n]),sorted([m.CONFIG,m.CALLER,m.PROPS]))
        self.assertEqual(self.native['LICENSE'],self.on['LICENSE'])
        self.assertIn(f'#define {m.OPTION} 0',self.on[m.CONFIG].decode())
    def test_actual_native_control_on_cue_expanded_glsl(self):
        with tempfile.TemporaryDirectory() as d:
            for label,files,mode in [('native',self.native,0),('off',self.on,0),('on',self.on,1),('cue',self.cue,1)]:
                p=Path(d)/(label+'.frag');p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+self.preprocess(files,mode))
                run=subprocess.run(['glslangValidator','-S','frag',str(p)],capture_output=True,text=True)
                self.assertEqual(run.returncode,0,run.stdout+run.stderr)
    def test_vertex_skip_preserves_native_light_and_all_refused_graphs(self):
        files=m.patch(self.native,vertex_skip=True)
        self.assertEqual(files[m.CALLER],self.on[m.CALLER])
        for macros in [old.MACROS,dict(old.MACROS,DISTANT_HORIZONS=1),dict(old.MACROS,IRIS_VERSION=10811)]:
            mode=0 if macros==old.MACROS else 1
            for suffix in ['fsh','vsh']:
                entry='shaders/world0/gbuffers_water.'+suffix
                self.assertEqual(old.normalized(self.preprocess(files,mode,entry,macros)),old.normalized(self.preprocess(self.native,mode,entry,macros)))
        text=self.preprocess(files,1,'shaders/world0/gbuffers_water.vsh')
        self.assertEqual(re.findall(r'blockType = ([0-9.]+);',text),['0.0','3.0','2.0'])
        self.assertLess(text.index('blockType = 0.0'),text.index('horizonSkyColor = vec3(0.0)'))
        self.assertIn('vec3 omniColor = mix(ZenithSkyColorRGB',text)
        self.assertIn('frogAdjust = pow(',text)
        self.assertIn('if (blockType == 0.0)',text)
        with tempfile.TemporaryDirectory() as d:
            paths=[]
            for suffix,stage in [('fsh','frag'),('vsh','vert')]:
                text=self.preprocess(files,1,'shaders/world0/gbuffers_water.'+suffix)
                p=Path(d)/('shader.'+stage);p.write_text('#version 120\n#extension GL_ARB_shader_texture_lod : enable\n'+text);paths.append(str(p))
            run=subprocess.run(['glslangValidator','-l',*paths],capture_output=True,text=True)
            self.assertEqual(run.returncode,0,run.stdout+run.stderr)

if __name__=='__main__':unittest.main()
