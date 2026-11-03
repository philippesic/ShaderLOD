"""Pinned bytecode/source and real plugin-gating fixtures; no GL context/client.

The javac fixture executes the real plugin with only interface stubs. The optional
local-artifact fixture inspects locked bytecode, not a transformed runtime class.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MIXIN = ROOT / 'src/devClient/java/com/pp/shaderlod/dev/mixin/RasterSamplerStateMixin.java'
PLUGIN = MIXIN.with_name('DevEvidenceMixinPlugin.java')
RESOURCE = ROOT / 'src/devClient/resources/shaderlod-dev.mixins.json'
SODIUM_SHA256 = '7acd7af2ef75ec883a6872c17494ead65e38025151eb08a1d60e36d34377f403'
DRAW_CLASS_SHA256 = '60d7958e257a4ee5c22e9ca299dd8ca124c2f113d0d970b731526f510dae538b'
DRAW_CLASS = 'net.caffeinemc.mods.sodium.client.gl.device.GLRenderDevice$ImmediateDrawCommandList'
METHOD_DESCRIPTOR = '(Lnet/caffeinemc/mods/sodium/client/gl/device/MultiDrawBatch;Lnet/caffeinemc/mods/sodium/client/gl/tessellation/GlIndexType;)V'
NATIVE_DESCRIPTOR = '(IJIJIJ)V'


class RasterReadbackHookTest(unittest.TestCase):
    def test_hooks_bracket_one_native_call_with_existing_observers(self):
        source = MIXIN.read_text()
        self.assertIn('targets = "' + DRAW_CLASS + '"', source)
        self.assertIn('method = "multiDrawElementsBaseVertex' + METHOD_DESCRIPTOR + '"', source)
        self.assertIn('target = "Lorg/lwjgl/opengl/GL32C;nglMultiDrawElementsBaseVertex' + NATIVE_DESCRIPTOR + '"', source)
        self.assertIn('shift = At.Shift.BEFORE', source)
        self.assertIn('require = 1, allow = 1, remap = false', source)
        self.assertEqual(2, source.count('@Inject('))
        self.assertEqual(1,source.count('shift = At.Shift.BEFORE'))
        self.assertEqual(1,source.count('shift = At.Shift.AFTER'))
        self.assertNotIn('@At("HEAD")', source)
        body = re.search(r'private void shaderlod\$observeRasterSamplers\(CallbackInfo callback\) \{([^}]*)}', source)
        self.assertEqual(['com.pp.shaderlod.dev.BslMaterialProbe.beforeDraw();',
                          'CloudSamplerStateProbe.beforeRasterDraw();',
                          'com.pp.shaderlod.dev.ComplementaryRasterActivationProbe.beforeOpaqueDraw();',
                          'com.pp.shaderlod.dev.G04NativeWriter.before();'],
                         [line.strip() for line in body.group(1).splitlines() if line.strip()])
        self.assertIn('com.pp.shaderlod.dev.G04NativeWriter.returned();',source)
        self.assertNotRegex(source, r'\b(GL\w*|GpuTimers|ShadowRenderer|Supplier)\.')
        data = json.loads(RESOURCE.read_text())
        self.assertEqual(1, data['client'].count('RasterSamplerStateMixin'))
        self.assertIn('CloudSamplerStateMixin', data['client'])
        self.assertIn('DhDrawEvidenceMixin', data['client'])
        self.assertEqual('com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin', data['plugin'])
        self.assertTrue(data['required'])

    def test_actual_plugin_raster_and_dh_property_gates(self):
        if not shutil.which('javac') or not shutil.which('java'):
            self.skipTest('JDK required for real plugin fixture')
        with tempfile.TemporaryDirectory(prefix='shaderlod-raster-plugin-') as directory:
            folder = Path(directory)
            files = {
                # Unrelated native-chain policy is outside this raster/DH gate fixture.
                'com/pp/shaderlod/optimization/nativechain/NativeChainAdmission.java': 'package com.pp.shaderlod.optimization.nativechain; public class NativeChainAdmission {public static boolean useReplacement(){return false;}}',
                'org/objectweb/asm/tree/ClassNode.java': 'package org.objectweb.asm.tree; public class ClassNode {}',
                'org/spongepowered/asm/mixin/extensibility/IMixinInfo.java': 'package org.spongepowered.asm.mixin.extensibility; public interface IMixinInfo {}',
                'org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin.java': '''package org.spongepowered.asm.mixin.extensibility;
import java.util.List; import java.util.Set; import org.objectweb.asm.tree.ClassNode;
public interface IMixinConfigPlugin {
void onLoad(String s); String getRefMapperConfig(); boolean shouldApplyMixin(String t, String m);
void acceptTargets(Set<String> a, Set<String> b); List<String> getMixins();
void preApply(String t, ClassNode c, String m, IMixinInfo i);
void postApply(String t, ClassNode c, String m, IMixinInfo i);
}''',
                'PluginFixture.java': '''import com.pp.shaderlod.dev.mixin.DevEvidenceMixinPlugin;
public final class PluginFixture {
 public static void main(String[] args) {
  var plugin = new DevEvidenceMixinPlugin();
  String prefix="com.pp.shaderlod.dev.mixin.";
  for (boolean sampler : new boolean[]{false,true}) {
   for (boolean dh : new boolean[]{false,true}) {
    System.setProperty("shaderlod.dev.cloudSamplerProbe", Boolean.toString(sampler));
    System.setProperty("shaderlod.dev.dhDrawEvidence", Boolean.toString(dh));
    if (plugin.shouldApplyMixin("target", prefix+"RasterSamplerStateMixin") != sampler) throw new AssertionError("raster gate");
    if (plugin.shouldApplyMixin("target", prefix+"DhDrawEvidenceMixin") != dh) throw new AssertionError("DH gate");

   }
  }
  System.clearProperty("shaderlod.dev.cloudSamplerProbe");
  System.clearProperty("shaderlod.dev.dhDrawEvidence");
  if (plugin.shouldApplyMixin("target", prefix+"RasterSamplerStateMixin")) throw new AssertionError("unset raster gate");
  if (plugin.shouldApplyMixin("target", prefix+"DhDrawEvidenceMixin")) throw new AssertionError("unset DH gate");
 }
}''',
            }
            for relative, source in files.items():
                target = folder / relative; target.parent.mkdir(parents=True, exist_ok=True); target.write_text(source)
            sources = [str(folder / relative) for relative in files]
            compilation = subprocess.run(['javac', '-d', str(folder), str(PLUGIN), str(ROOT / 'src/devClient/java/com/pp/shaderlod/dev/ShaderAnimationClock.java'),
                            *sources], capture_output=True, text=True)
            self.assertEqual(compilation.returncode,0,compilation.stdout+compilation.stderr)
            subprocess.run(['java', '-cp', str(folder), 'PluginFixture', *json.loads(RESOURCE.read_text())['client']],
                           check=True, capture_output=True, text=True)

    def test_local_pinned_submission_instruction(self):
        runtime = Path(os.environ.get('SHADERLOD_PINNED_RUNTIME_DIR', ROOT / 'dependencies/runtime'))
        jar = runtime / 'sodium-neoforge-0.6.13+mc1.21.1.jar'
        if not jar.exists() or not shutil.which('javap'):
            self.skipTest('Pinned local Sodium/JDK required for bytecode fixture')
        self.assertEqual(SODIUM_SHA256, hashlib.sha256(jar.read_bytes()).hexdigest())
        with zipfile.ZipFile(jar) as archive:
            self.assertEqual(DRAW_CLASS_SHA256, hashlib.sha256(archive.read(DRAW_CLASS.replace('.', '/') + '.class')).hexdigest())
        output = subprocess.check_output(['javap', '-classpath', str(jar), '-p', '-s', '-c', DRAW_CLASS], text=True)
        body = output.split('  public void multiDrawElementsBaseVertex(', 1)[1].split('  public void endTessellating()', 1)[0]
        self.assertIn('descriptor: ' + METHOD_DESCRIPTOR, body)
        native = re.findall(r'\d+: invokestatic\s+#[0-9]+\s+// Method org/lwjgl/opengl/GL32C\.nglMultiDrawElementsBaseVertex:(\S+)', body)
        self.assertEqual([NATIVE_DESCRIPTOR], native)
        self.assertNotRegex(body, r'glUseProgram|\.bind:|setupState:')
        self.assertRegex(body, r'37: invokestatic')


if __name__ == '__main__':
    unittest.main()
