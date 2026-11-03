package com.pp.shaderlod.bslprefix;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
public class BslPrefixRayProbeTest {
 @Test void actualLinkedFunctionsPreserveCompoundOperators()throws Exception{
  String path=System.getenv("SHADERLOD_G09_PROBE_FIXTURE");assumeTrue(path!=null,"Explicit actual linked fragment fixture required");
  String actual=Files.readString(Path.of(path));
  assertTrue(BslPrefixSourceR8.matches(actual,com.pp.shaderlod.optimization.bslprefix.BslPrefixProfile.functions()));
  String probe=BslPrefixRayProbe.shader(actual);
  assertTrue(probe.contains("i++"));assertTrue(probe.contains("&&"));assertTrue(probe.contains("<="));assertTrue(probe.contains("!="));
  assertFalse(probe.contains("i + +"));assertFalse(probe.contains("& &"));assertFalse(probe.contains("< ="));
  assertTrue(probe.contains("coord += stepDir;"));assertTrue(probe.contains("currentStep -= sampleStep;"));
  assertTrue(probe.contains("shaderlodPomSamples == 128"));
 }
}
