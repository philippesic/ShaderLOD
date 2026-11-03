package com.pp.shaderlod.optimization;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.pp.shaderlod.optimization.makeupearlyalpha.MakeUpPostSampleAlphaWitness;
class MakeUpPostSampleAlphaWitnessTest {
 @Test void untimedObserverRequiresBenchmarkAndInvalidationDisarms() {
  String old=System.getProperty("shaderlod.dev.benchmarkControlProbe");
  try {
   MakeUpPostSampleAlphaWitness.invalidate();System.clearProperty("shaderlod.dev.benchmarkControlProbe");
   assertThrows(IllegalStateException.class,()->MakeUpPostSampleAlphaWitness.observe(true,true));
   System.setProperty("shaderlod.dev.benchmarkControlProbe","fixture");MakeUpPostSampleAlphaWitness.observe(true,true);
   assertTrue(MakeUpPostSampleAlphaWitness.observing());MakeUpPostSampleAlphaWitness.completedDraw(5);
   assertEquals(0L,MakeUpPostSampleAlphaWitness.snapshot().get("completed_batches"));
   MakeUpPostSampleAlphaWitness.invalidate();assertFalse(MakeUpPostSampleAlphaWitness.observing());
   assertEquals(0,MakeUpPostSampleAlphaWitness.snapshot().get("linked_programs"));
   MakeUpPostSampleAlphaWitness.observe(true,true);MakeUpPostSampleAlphaWitness.destroy(new Object());assertFalse(MakeUpPostSampleAlphaWitness.observing());
  } finally {MakeUpPostSampleAlphaWitness.invalidate();if(old==null)System.clearProperty("shaderlod.dev.benchmarkControlProbe");else System.setProperty("shaderlod.dev.benchmarkControlProbe",old);}
 }
}
