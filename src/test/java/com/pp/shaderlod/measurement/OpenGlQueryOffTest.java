package com.pp.shaderlod.measurement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OpenGlQueryOffTest {
 @Test void diagnosticQueryOffNeedsNoGlContextAndStillPublishesCpuFrameClocks(){
  String key="shaderlod.dev.disableGpuQueries",old=System.getProperty(key);
  try{
   System.setProperty(key,"true");var backend=new OpenGlElapsedBackend();
   // Absence of a GL context makes any accidental OpenGL call fail this test.
   System.clearProperty(key);assertTrue(backend.diagnosticQueryOff(),"mode is frozen when the backend is constructed");
   assertEquals(0,backend.counterBits());assertTrue(backend.status().contains("diagnostic"));
   long[] now={0};var frames=new FrameMeasurement(backend,()->now[0]+=1_000_000,()->0,()->0);
   frames.enabled(true);frames.begin();frames.end();frames.begin();frames.end();
   var samples=frames.drain();assertEquals(2,samples.size());
   assertNull(samples.get(1).gpuNs());assertNotNull(samples.get(1).frameWallNs());
   assertEquals(0L,samples.get(1).renderCpuNs());frames.enabled(false);
  }finally{if(old==null)System.clearProperty(key);else System.setProperty(key,old);}
 }
}
