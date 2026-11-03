package com.pp.shaderlod.optimization.depthprime;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepthShaderInputsTest {
 static final class Shader {int id;Shader(int id){this.id=id;}int handle(){if(id<0)throw new IllegalStateException("wrapper deleted");return id;}}
 final Map<Integer,String> driver=new HashMap<>(Map.of(1,"native vertex",2,"native fragment"));
 final Set<Integer> compiled=new HashSet<>(Set.of(1,2));
 final DepthShaderInputs<String,Shader> proof=new DepthShaderInputs<>(Shader::handle,driver::get,compiled::contains);
 Map<String,String> text(){var m=new HashMap<String,String>();m.put("vertex","native vertex");m.put("fragment","native fragment");m.put("geometry",null);return m;}
 Map<String,Shader> shaders(){return new HashMap<>(Map.of("vertex",new Shader(1),"fragment",new Shader(2)));}
 @Test void captureSurvivesWrapperDeletionButRejectsDriverMutation(){
  var shaders=shaders();assertTrue(proof.capture(text(),shaders));
  shaders.values().forEach(s->s.id=-1);
  assertEquals(Map.of("vertex",1,"fragment",2),proof.current(shaders));
  assertTrue(proof.unchangedDriverInput(shaders));
  driver.put(2,"different fragment");assertFalse(proof.unchangedDriverInput(shaders));
  driver.put(2,"native fragment");compiled.remove(1);assertFalse(proof.unchangedDriverInput(shaders));
 }
 @Test void incompleteForeignOrMutatedCapturesRefuse(){
  var shaders=shaders();assertTrue(proof.capture(text(),shaders));
  assertNull(proof.current(new HashMap<>(shaders)));
  shaders.put("vertex",new Shader(1));assertNull(proof.current(shaders));
  assertFalse(proof.unchangedDriverInput(shaders));
  shaders.remove("fragment");assertFalse(proof.capture(text(),shaders));assertNull(proof.current(shaders));
  shaders=shaders();compiled.remove(2);assertFalse(proof.capture(text(),shaders));
  compiled.add(2);driver.put(2,"wrong source");assertFalse(proof.capture(text(),shaders));
  driver.put(2,"native fragment");assertTrue(proof.capture(text(),shaders));
  assertFalse(proof.capture(Map.of("vertex","native vertex"),shaders));assertNull(proof.current(shaders));
 }
 @Test void invalidationAndRecoveryCannotReusePreviousMaps(){
  var shaders=shaders();assertTrue(proof.capture(text(),shaders));proof.clear();
  assertNull(proof.current(shaders));assertFalse(proof.unchangedDriverInput(shaders));
  assertTrue(proof.capture(text(),shaders));assertTrue(proof.unchangedDriverInput(shaders));
  shaders.put("fragment",new Shader(1));assertFalse(proof.capture(text(),shaders));
 }
}
