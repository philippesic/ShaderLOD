import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


class NativeCpuCostTest(unittest.TestCase):
    def test_disabled_calibration_phase_bounds_and_stop(self):
        jdk = Path(os.environ.get('SHADERLOD_TEST_JDK', ROOT / 'dependencies/runtime/asahi-desktop-java-21.0.6.0.7-6.fc42-r1/usr/lib/jvm/java-21-openjdk'))
        fixture = '''import com.pp.shaderlod.optimization.NativeAdmissionCost;
import java.util.Map;
public class CostFixture {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static Map<?,?> row(String name){return (Map<?,?>)NativeAdmissionCost.snapshot().get(name);}
 public static void main(String[] args){
  if(!NativeAdmissionCost.ENABLED){check(NativeAdmissionCost.wallStart()==0);return;}
  NativeAdmissionCost.observe(true);
  check(row("clock_baseline").get("calls").equals(4096L));
  for(int phase=0;phase<8;phase++){
   long wall=NativeAdmissionCost.wallStart(),cpu=NativeAdmissionCost.cpuStart(wall);
   NativeAdmissionCost.finish(phase,wall,cpu,true);
  }
  check(row("compactor_body").get("calls").equals(1L));
  check(row("native_submission_delegate").get("accepted").equals(1L));
  NativeAdmissionCost.observe(false);check(NativeAdmissionCost.wallStart()==0);
  NativeAdmissionCost.observe(true);
  check(row("compactor_body").get("calls").equals(0L));
  long wall=NativeAdmissionCost.wallStart(),cpu=NativeAdmissionCost.cpuStart(wall);
  NativeAdmissionCost.finish(99,wall,cpu,true);
  check(!NativeAdmissionCost.observing());check(!NativeAdmissionCost.snapshot().get("error").equals(""));
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            work = Path(tmp)
            source = work / 'CostFixture.java'
            source.write_text(fixture)
            subprocess.run([str(jdk / 'bin/javac'), '-d', str(work), str(source),
                            str(ROOT / 'src/main/java/com/pp/shaderlod/optimization/NativeAdmissionCost.java')],
                           check=True, capture_output=True, text=True)
            for options in [[], ['-Dshaderlod.dev.cpuAdmissionCost=true']]:
                subprocess.run([str(jdk / 'bin/java'), *options, '-cp', str(work), 'CostFixture'],
                               check=True, capture_output=True, text=True)
