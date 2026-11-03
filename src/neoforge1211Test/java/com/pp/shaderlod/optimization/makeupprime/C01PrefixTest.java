package com.pp.shaderlod.optimization.makeupprime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class C01PrefixTest {
 @Test void finiteAndDefaultOffContract(){
  C01Prefix.validate("off",0,0);C01Prefix.validate("observe",4,20000);C01Prefix.validate("conditional",256,1);
  assertThrows(IllegalArgumentException.class,()->C01Prefix.validate("enabled",4,1));
  assertThrows(IllegalArgumentException.class,()->C01Prefix.validate("observe",-1,1));
  assertThrows(IllegalArgumentException.class,()->C01Prefix.validate("observe",4,20001));
 }
 @Test void rejectMutableVertexSideEffects(){
  assertTrue(C01Prefix.sideEffectFree("#version 330\nuniform sampler2D tex; void main(){gl_Position=vec4(0);}",true));
  for(String operation:new String[]{"buffer Data {}", "imageStore", "uimage2D", "atomicAdd", "atomicCounterIncrement", "memoryBarrier", "barrier", "gl_ClipDistance"})
   assertFalse(C01Prefix.sideEffectFree("#version 330\n"+operation,true),operation);
 }
 @Test void fragmentMustRetainPinnedOpaqueContract(){
  assertFalse(C01Prefix.sideEffectFree("#version 330\nvoid main(){}",false));
  assertFalse(C01Prefix.sideEffectFree("#version 330\nvoid main(){gl_FragDepth=1;}",false));
 }
}
