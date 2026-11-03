import subprocess
import tempfile
import unittest
from pathlib import Path
from pinned_tools import java_tool

class NativeAdmissionCostTest(unittest.TestCase):
    def test_disabled_and_armed_clock_bounds_do_not_grant_permission(self):
        root=Path(__file__).resolve().parents[1]
        source=root/'src/main/java/com/pp/shaderlod/optimization/NativeAdmissionCost.java'
        fixture='''import com.pp.shaderlod.optimization.NativeAdmissionCost;
import java.lang.management.ManagementFactory;
import java.util.Map;
public class Fixture {
 public static void main(String[] args) {
  if(!NativeAdmissionCost.ENABLED){
   if(NativeAdmissionCost.wallStart()!=0)throw new AssertionError();
   try{NativeAdmissionCost.observe(true);throw new AssertionError();}catch(IllegalStateException expected){}
   return;
  }
  ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(true);
  NativeAdmissionCost.observe(true);
  long w=NativeAdmissionCost.wallStart(),c=NativeAdmissionCost.cpuStart(w);
  for(int i=0;i<10000;i++)Thread.onSpinWait();
  NativeAdmissionCost.finish(0,w,c,false);
  Map<?,?> r=(Map<?,?>)NativeAdmissionCost.snapshot().get("compaction");
  if(!r.get("calls").equals(1L)||!r.get("accepted").equals(0L)||(long)r.get("wall_ns")<=0||(long)r.get("thread_cpu_ns")<=0)throw new AssertionError(r);
  NativeAdmissionCost.observe(false);
  if(NativeAdmissionCost.wallStart()!=0)throw new AssertionError();
  NativeAdmissionCost.observe(true);
  w=NativeAdmissionCost.wallStart();c=NativeAdmissionCost.cpuStart(w);
  NativeAdmissionCost.finish(3,w,c,true);
  if(NativeAdmissionCost.observing()||NativeAdmissionCost.snapshot().get("error").equals(""))throw new AssertionError();
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            f=Path(tmp)/'Fixture.java';f.write_text(fixture)
            subprocess.run([java_tool('javac'),'-d',tmp,str(source),str(f)],check=True,capture_output=True)
            for properties in [[],['-Dshaderlod.dev.cpuAdmissionCost=true']]:
                subprocess.run([java_tool('java'),*properties,'-cp',tmp,'Fixture'],check=True,capture_output=True)
