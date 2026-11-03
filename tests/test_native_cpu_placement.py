import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]

class NativeCpuPlacementTest(unittest.TestCase):
    def test_boundaries_errors_disabled_and_actual_linux_backend(self):
        jdk=Path(os.environ.get('SHADERLOD_TEST_JDK',ROOT/'dependencies/runtime/asahi-desktop-java-21.0.6.0.7-6.fc42-r1/usr/lib/jvm/java-21-openjdk'))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl/3.3.3'
        jars=[next(cache.glob('*/lwjgl-3.3.3.jar')),ROOT/'dependencies/runtime/linux-arm64/lwjgl-3.3.3-natives-linux-arm64.jar']
        fixture='''package com.pp.shaderlod.dev;
import java.util.*;
public final class PlacementFixture {
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args){
  if(args.length>0){NativeCpuPlacement.warm();var p=new NativeCpuPlacement();p.begin();p.end();var m=p.snapshot();
   check(m.get("unknown_heads").equals(0L)&&m.get("unknown_tails").equals(0L));check(m.get("error").equals(""));return;}
  var off=new NativeCpuPlacement(false,()->{throw new AssertionError("Disabled reader called");});off.begin();off.end();check(off.snapshot().get("starts").equals(0L));
  int[] cpus={0,0,4,5};int[] index={0};var p=new NativeCpuPlacement(true,()->cpus[index[0]++]);p.begin();p.end();p.begin();p.end();var m=p.snapshot();
  check(m.get("starts").equals(2L)&&m.get("ends").equals(2L));check(m.get("head_cpus").equals(Map.of("0",1L,"4",1L)));
  check(m.get("tail_cpus").equals(Map.of("0",1L,"5",1L))&&m.get("changed_boundary_cpu").equals(1L));
  var unknown=new NativeCpuPlacement(true,()->-1);unknown.begin();unknown.end();check(unknown.snapshot().get("unknown_heads").equals(1L));
  var failed=new NativeCpuPlacement(true,()->{throw new IllegalStateException();});failed.begin();failed.end();check(!failed.snapshot().get("error").equals(""));
  var nested=new NativeCpuPlacement(true,()->4);nested.begin();nested.begin();nested.end();check(nested.snapshot().get("error").toString().contains("Nested"));
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            work=Path(tmp);source=work/'PlacementFixture.java';source.write_text(fixture)
            cp=os.pathsep.join(str(p) for p in jars)
            subprocess.run([str(jdk/'bin/javac'),'-cp',cp,'-d',str(work),str(source),str(ROOT/'src/devClient/java/com/pp/shaderlod/dev/NativeCpuPlacement.java')],check=True,capture_output=True,text=True)
            run_cp=os.pathsep.join([str(work),cp])
            subprocess.run([str(jdk/'bin/java'),'-cp',run_cp,'com.pp.shaderlod.dev.PlacementFixture'],check=True,capture_output=True,text=True)
            # Actual selected architecture/JDK/native loader; no graphical context or affinity mutation.
            subprocess.run([str(jdk/'bin/java'),'-Dshaderlod.dev.cpuPlacement=true',
                            '-Dorg.lwjgl.librarypath='+str(ROOT/'dependencies/runtime/linux-arm64/natives'),
                            '-cp',run_cp,'com.pp.shaderlod.dev.PlacementFixture','native'],check=True,capture_output=True,text=True)
