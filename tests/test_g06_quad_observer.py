"""Bounded G06 dev-observer contract and native-hook joins; no game or GL context."""
from pathlib import Path
import hashlib
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
from pinned_tools import java_tool
BASE = ROOT / 'src/devClient/java/com/pp/shaderlod/dev'

HARNESS = r'''
package com.pp.shaderlod.dev.timing;
import com.google.gson.*;
class QuadWindowChecks {
 static JsonObject allocation(){return JsonParser.parseString("{\"level\":0,\"width\":960,\"height\":600,\"internal_format\":33328,\"status\":\"queried_without_gl_error_consumption\"}").getAsJsonObject();}
 static JsonObject event(boolean producer){
  var e=new JsonObject();e.addProperty("linked",true);
  var draw=new JsonObject();draw.addProperty("draw_framebuffer",producer?11:12);
  draw.add("viewport",JsonParser.parseString(producer?"[0,0,960,600]":"[0,0,1920,1200]"));
  var attachments=new JsonArray();var output=new JsonObject();output.addProperty("object_type",5890);output.addProperty("texture_level",0);output.addProperty("texture",producer?77:33);output.add("allocation",allocation());attachments.add(output);draw.add("color_attachments",attachments);e.add("draw_state",draw);
  var samplers=new JsonObject();var sampler=new JsonObject();sampler.addProperty("active_uniform_size",1);sampler.addProperty("active_uniform_type",35678);sampler.addProperty("uniform_location",8);sampler.addProperty("texture_target",3553);sampler.addProperty("bound_texture",77);sampler.add("texture_level_zero",allocation());var parameters=new JsonObject();parameters.addProperty("base_level",0);sampler.add("texture_parameters",parameters);samplers.add("colortex8",sampler);e.add("samplers",samplers);return e;
 }
 static QuadSamplerWindow window(){return new QuadSamplerWindow(0,new JsonObject());}
 static void producer(QuadSamplerWindow w){w.before("deferred",101,99,event(true),1);w.returned("deferred",101,99,2);}
 static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
 static void refused(QuadSamplerWindow w){require(w.snapshot(9).get("status").getAsString().equals("REFUSED"),w.snapshot(9).toString());require(!w.snapshot(9).get("physical_join").getAsBoolean(),"false positive");}
 public static void main(String[]args){
  require(QuadSamplerWindow.PROGRAM_LIMIT==64,"global cap changed");
  var w=window();producer(w);w.before("deferred1",102,99,event(false),3);require(!w.snapshot(4).get("physical_join").getAsBoolean(),"HEAD alone accepted");w.returned("deferred1",102,99,5);require(w.snapshot(6).get("physical_join").getAsBoolean(),"valid join refused");require(!w.snapshot(6).get("observing").getAsBoolean(),"not finite");
  for(int defect=0;defect<13;defect++){
   w=window();producer(w);var e=event(false);var sampler=e.getAsJsonObject("samplers").getAsJsonObject("colortex8");
   switch(defect){
    case 0->sampler.addProperty("bound_texture",78);
    case 1->sampler.addProperty("uniform_location",-1);
    case 2->sampler.addProperty("active_uniform_size",2);
    case 3->sampler.addProperty("active_uniform_type",35679);
    case 4->sampler.getAsJsonObject("texture_level_zero").addProperty("internal_format",32856);
    case 5->sampler.getAsJsonObject("texture_level_zero").addProperty("width",1920);
    case 6->sampler.getAsJsonObject("texture_level_zero").addProperty("status","unknown");
    case 7->sampler.getAsJsonObject("texture_parameters").addProperty("base_level",1);
    case 8->e.getAsJsonObject("draw_state").getAsJsonArray("color_attachments").get(0).getAsJsonObject().addProperty("texture",77);
    case 9->e.getAsJsonObject("draw_state").addProperty("draw_framebuffer",0);
    case 10->e.addProperty("linked",false);
    case 11->e.getAsJsonObject("draw_state").add("viewport",JsonParser.parseString("[0,0,960,600]"));
    case 12->sampler.remove("texture_level_zero");
   }
   w.before("deferred1",102,99,e,3);refused(w);
  }
  w=window();w.before("deferred",101,99,event(true),1);w.before("deferred1",102,99,event(false),2);refused(w);
  w=window();producer(w);w.before("deferred1",102,100,event(false),3);refused(w);
  w=window();producer(w);w.before("deferred1",101,99,event(false),3);refused(w);
  w=window();var bad=event(true);bad.getAsJsonObject("draw_state").getAsJsonArray("color_attachments").get(0).getAsJsonObject().addProperty("texture_level",1);w.before("deferred",101,99,bad,1);refused(w);
  w=window();require(!w.active(QuadSamplerWindow.DURATION_NS+1),"deadline");refused(w);
  w=window();w.refuse("BOUND_CONTEXT_CHANGED");refused(w);
  w=window();producer(w);w.returned("deferred1",102,99,3);refused(w);
  w=window();var copy=w.snapshot(0);copy.addProperty("physical_join",true);require(!w.snapshot(0).get("physical_join").getAsBoolean(),"mutable receipt");
  System.out.println("PASS valid producer HEAD/RETURN -> consumer HEAD/RETURN join, 20 refusal/lifetime cases, finite64 cap, immutable receipt; synthetic getters only");
 }
}
'''


class QuadObserverTests(unittest.TestCase):
    def test_actual_pure_window_contract(self):
        JAVA = Path(java_tool('javac')).parent
        jars = list((Path.home() / '.gradle/caches/modules-2/files-2.1').glob('com.google.code.gson/gson/2.10.1/*/gson-2.10.1.jar'))
        self.assertEqual(len(jars), 1)
        with tempfile.TemporaryDirectory(prefix='g06-quad-window-') as d:
            d = Path(d)
            harness = d / 'QuadWindowChecks.java'
            harness.write_text(HARNESS)
            command = [str(JAVA / 'javac'), '-proc:none', '-cp', str(jars[0]), '-d', str(d),
                       str(BASE / 'timing/QuadSamplerWindow.java'), str(harness)]
            p = subprocess.run(command, capture_output=True, text=True, timeout=30)
            self.assertEqual(p.returncode, 0, p.stderr)
            p = subprocess.run([str(JAVA / 'java'), '-cp', str(d)+':'+str(jars[0]), 'com.pp.shaderlod.dev.timing.QuadWindowChecks'], capture_output=True, text=True, timeout=10)
            self.assertEqual(p.returncode, 0, p.stdout+p.stderr)
            print(p.stdout.strip())

    def test_legacy_capture_body_and_cap_remain_unchanged(self):
        name = 'src/devClient/java/com/pp/shaderlod/dev/timing/CloudSamplerStateProbe.java'
        new = (ROOT / name).read_text()
        self.assertIn('private static final int PROGRAM_LIMIT = 64;', new)
        self.assertIn('if (quadWindow != null) return;\n        beforeDraw("sodium-raster-submission");', new)
        bounded = new[new.index('    private static void boundedQuad('):new.index('    /** Read-only bindings')]
        self.assertNotIn('attachedStageSource(', bounded)
        self.assertIn('finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }', new)

    def test_native_name_and_quad_return_hooks_preserve_existing_calls(self):
        names = (BASE / 'mixin/CompositeRendererTimingMixin.java').read_text()
        quad = (BASE / 'mixin/CloudSamplerStateMixin.java').read_text()
        self.assertIn('Boolean.getBoolean("shaderlod.dev.cloudSamplerProbe")', names)
        self.assertIn('CloudSamplerStateProbe.beginNamedPass(name)', names)
        self.assertIn('CloudSamplerStateProbe.endNamedPass()', names)
        for call in ['GLDebug.pushGroup(id, name)', 'GLDebug.popGroup()', 'CpuTimers.beginIrisGroup(name)', 'GpuTimers.endIrisGroup()', 'ComplementaryRasterR1.debugGroup(name)']:
            self.assertIn(call, names)
        self.assertIn('@At("RETURN")', quad)
        self.assertEqual(quad.count('CloudSamplerStateProbe.beforeQuadDraw();'), 1)
        self.assertEqual(quad.count('CloudSamplerStateProbe.afterQuadDraw();'), 1)

    def test_bound_control_is_read_only_and_keeps_native_control_apis(self):
        source = (BASE / 'BenchmarkControlProbe.java').read_text()
        for token in ['case "g06_sampler_observe"', '"Select mode2 before arming; this action never reloads"',
                      'LoadedArtifactEvidence.fileSha256(file).equals(pin)', 'nativeOptions.size()!=100',
                      'quadCapturePresent()', 'coreOff/empty requests', 'camera.getX()==x']:
            self.assertIn(token, source)
        for native_api in ['private static void diagnosticPack(', 'private void advanceMotion(', 'case \"window_start\"']:
            self.assertIn(native_api, source)
        arm = source[source.index('    private JsonObject g06SamplerObserve('):source.index('    private void advanceMotion(')]
        self.assertNotIn('Iris.reload()', arm)
        self.assertNotIn('clearDiagnosticRequests()', arm)


if __name__ == '__main__':
    unittest.main()
