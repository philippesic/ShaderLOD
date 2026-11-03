package com.pp.shaderlod.solasprefix;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
/** Exactly one native uniform owns each signal. Never run detached or foreign-owner listeners. */
public final class SolasPrefixOwnerSignalR8 implements ValueUpdateNotifier {
 private final Object owner;
 private final int program;
 private Runnable listener;
 public SolasPrefixOwnerSignalR8(Object owner,int program){this.owner=owner;this.program=program;}
 public void setListener(Runnable next){listener=next;}
 public boolean fire(Object boundOwner,int boundProgram){
  if(listener==null||owner!=boundOwner||program!=boundProgram)return false;
  listener.run();return true;
 }
}
