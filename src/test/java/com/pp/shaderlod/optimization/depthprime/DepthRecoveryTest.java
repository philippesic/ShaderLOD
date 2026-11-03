package com.pp.shaderlod.optimization.depthprime;
import com.pp.shaderlod.optimization.unboundprime.DepthPrimingPass;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepthRecoveryTest {
 static final class Work implements DepthPrimingPass.Work {
  final List<String> events=new ArrayList<>();boolean prepareThrow,drawThrow,restoreThrow,ready=true;
  public boolean prepare(){events.add("prepare");if(prepareThrow)throw new IllegalStateException("prepare");return ready;}
  public void prime(DepthPrimingPass.Submission s){s.draw(()->{events.add("submit");if(drawThrow)throw new IllegalStateException("draw");});}
  public void restore(){events.add("restore");if(restoreThrow)throw new IllegalStateException("restore");}
  public void verify(){events.add("verify");}
  public void disable(){events.add("disable");}
 }
 @Test void preSubmissionFailureRestoresVerifiesAndDisablesBeforeNativeFallback(){var w=new Work();w.prepareThrow=true;assertFalse(DepthPrimingPass.run(w));assertEquals(List.of("prepare","restore","verify","disable"),w.events);}
 @Test void afterSubmissionOrFailedRestoreCannotFallThroughToNative(){var w=new Work();w.drawThrow=true;assertThrows(IllegalStateException.class,()->DepthPrimingPass.run(w));assertEquals(List.of("prepare","submit","restore","verify","disable"),w.events);var broken=new Work();broken.prepareThrow=true;broken.restoreThrow=true;assertThrows(IllegalStateException.class,()->DepthPrimingPass.run(broken));assertEquals(List.of("prepare","restore","verify","disable"),broken.events);}
 @Test void refusedPrepareStillRestoresAndSuccessRetainsNativePass(){var w=new Work();w.ready=false;assertFalse(DepthPrimingPass.run(w));assertEquals(List.of("prepare","restore","verify"),w.events);var ok=new Work();assertTrue(DepthPrimingPass.run(ok));assertEquals(List.of("prepare","submit","restore","verify"),ok.events);}
}
