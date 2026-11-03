package com.pp.shaderlod.optimization.depthprime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DepthProgramLeaseTest {
 static final class Program {int id;Program(int id){this.id=id;}int handle(){if(id<0)throw new IllegalStateException("deleted");return id;}}
 @Test void exactPairOwnerPassEpochAndHandleAreRequired(){
  Object pipeline=new Object(),owner=new Object(),pass=new Object();var nativeProgram=new Program(5);var depth=new Program(7);
  var proof=new DepthProgramLease<>(pipeline,owner,pass,nativeProgram,depth,12,Program::handle);
  assertTrue(proof.current(pipeline,owner,pass,nativeProgram,depth,12));
  assertFalse(proof.current(new Object(),owner,pass,nativeProgram,depth,12));
  assertFalse(proof.current(pipeline,new Object(),pass,nativeProgram,depth,12));
  assertFalse(proof.current(pipeline,owner,new Object(),nativeProgram,depth,12));
  assertFalse(proof.current(pipeline,owner,pass,nativeProgram,depth,13));
  assertFalse(proof.current(pipeline,owner,pass,new Program(5),depth,12));
  assertFalse(proof.current(pipeline,owner,pass,nativeProgram,new Program(7),12));
  nativeProgram.id=9;assertFalse(proof.current(pipeline,owner,pass,nativeProgram,depth,12));
  nativeProgram.id=5;depth.id=-1;assertFalse(proof.current(pipeline,owner,pass,nativeProgram,depth,12));
 }
 @Test void reloadNeedsNewPairAndEqualProgramNamesAreNeverEnough(){
  Object p=new Object(),o=new Object(),pass=new Object();var nativeProgram=new Program(5);var depth=new Program(7);
  var old=new DepthProgramLease<>(p,o,pass,nativeProgram,depth,12,Program::handle);
  nativeProgram.id=-1;assertFalse(old.current(p,o,pass,nativeProgram,depth,12));
  var replacement=new Program(5);assertFalse(old.current(p,o,pass,replacement,depth,12));
  var fresh=new DepthProgramLease<>(p,o,pass,replacement,depth,13,Program::handle);
  assertTrue(fresh.current(p,o,pass,replacement,depth,13));
  var same=new DepthProgramLease<>(p,o,pass,replacement,new Program(5),13,Program::handle);
  assertFalse(same.current(p,o,pass,replacement,depth,13));
 }
}
