import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]


class NativeRenderAffinityTest(unittest.TestCase):
    def test_refusals_restore_and_real_caller_only_native_mask(self):
        jdk=Path(os.environ.get('SHADERLOD_TEST_JDK',ROOT/'dependencies/runtime/asahi-desktop-java-21.0.6.0.7-6.fc42-r1/usr/lib/jvm/java-21-openjdk'))
        cache=Path.home()/'.gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl/3.3.3'
        jars=[next(cache.glob('*/lwjgl-3.3.3.jar')),ROOT/'dependencies/runtime/linux-arm64/lwjgl-3.3.3-natives-linux-arm64.jar']
        fixture='''package com.pp.shaderlod.dev;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class AffinityFixture {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static final class Fake implements NativeRenderAffinity.Backend {
  byte[] mask=new byte[128];int writes;boolean fail;
  Fake(){mask[0]=24;}
  public byte[] read(){return mask.clone();}
  public void write(byte[] m){writes++;mask=m.clone();if(fail){fail=false;throw new IllegalStateException("partial failure");}}
 }
 public static void main(String[] args)throws Exception {
  if(args.length==0){
   var f=new Fake();var off=NativeRenderAffinity.open(-1,f);off.close();check(f.writes==0);
   var p=NativeRenderAffinity.open(4,f);check(f.mask[0]==16);check(p.snapshot().get("original_cpus").equals(List.of(3,4)));
   try{NativeRenderAffinity.open(4,f);throw new AssertionError();}catch(IllegalStateException expected){}
   p.close();p.close();check(f.mask[0]==24&&p.snapshot().get("restored").equals(true));
   try{NativeRenderAffinity.open(5,f);throw new AssertionError();}catch(IllegalStateException expected){}
   check(f.mask[0]==24);f.fail=true;
   try{NativeRenderAffinity.open(4,f);throw new AssertionError();}catch(IllegalStateException expected){}
   check(f.mask[0]==24);return;
  }
  int cpu=Integer.parseInt(args[0]);System.setProperty("shaderlod.dev.renderCpu",args[0]);
  var cls=Class.forName("com.pp.shaderlod.dev.NativeRenderAffinity$Linux");var field=cls.getDeclaredField("INSTANCE");field.setAccessible(true);
  var system=(NativeRenderAffinity.Backend)field.get(null);byte[] original=system.read();
  var ready=new CountDownLatch(1);var proceed=new CountDownLatch(1);var same=new AtomicBoolean();
  Thread existing=new Thread(()->{try{ready.countDown();proceed.await();same.set(Arrays.equals(original,system.read()));}catch(Exception e){throw new RuntimeException(e);}});
  existing.start();ready.await();
  var pin=NativeRenderAffinity.open();
  try{byte[] target=new byte[128];target[cpu/8]=(byte)(1<<(cpu%8));check(Arrays.equals(target,system.read()));proceed.countDown();existing.join();check(same.get());}
  finally{proceed.countDown();existing.join();pin.close();}
  check(Arrays.equals(original,system.read()));
  if(((List<?>)pin.snapshot().get("original_cpus")).size()>1){
   var hold=new CountDownLatch(1);var childReady=new CountDownLatch(1);var second=NativeRenderAffinity.open();
   Thread child=new Thread(()->{childReady.countDown();try{hold.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   child.start();childReady.await();boolean refused=false;
   try{second.close();}catch(IllegalStateException expected){refused=true;}
   finally{hold.countDown();child.join();}
   check(refused&&second.snapshot().get("restored").equals(true));check(Arrays.equals(original,system.read()));
  }
 }
}'''
        with tempfile.TemporaryDirectory() as tmp:
            work=Path(tmp);source=work/'AffinityFixture.java';source.write_text(fixture)
            cp=os.pathsep.join(map(str,jars));run_cp=os.pathsep.join([str(work),cp])
            subprocess.run([str(jdk/'bin/javac'),'-cp',cp,'-d',str(work),str(source),str(ROOT/'src/devClient/java/com/pp/shaderlod/dev/NativeRenderAffinity.java')],check=True,capture_output=True,text=True)
            subprocess.run([str(jdk/'bin/java'),'-cp',run_cp,'com.pp.shaderlod.dev.AffinityFixture'],check=True,capture_output=True,text=True)
            allowed=os.sched_getaffinity(0);cpu=4 if 4 in allowed else min(allowed)
            subprocess.run([str(jdk/'bin/java'),'-Dorg.lwjgl.librarypath='+str(ROOT/'dependencies/runtime/linux-arm64/natives'),'-cp',run_cp,'com.pp.shaderlod.dev.AffinityFixture',str(cpu)],check=True,capture_output=True,text=True)
