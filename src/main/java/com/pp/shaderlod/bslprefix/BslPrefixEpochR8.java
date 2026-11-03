package com.pp.shaderlod.bslprefix;

/** Single render-lane capture ledger. Capture tokens are unique and one-shot.
 * No object/ID switch or failed recapture can revive a retired proof. */
public final class BslPrefixEpochR8 {
 public record Context(Object pipeline,Object world,Object resources,Object pack,Object normal,Object format) {
  boolean valid(){return pipeline!=null&&world!=null&&resources!=null&&pack!=null&&normal!=null&&format!=null;}
  boolean same(Context b){return b!=null&&pipeline==b.pipeline&&world==b.world&&resources==b.resources&&pack==b.pack&&normal==b.normal&&format==b.format;}
 }
 public record Token(long generation,Context context) {}
 private long generation;
 private Token current;
 private boolean installed,consumed;
 public synchronized Token begin(Context context){
  invalidate(); if(context==null||!context.valid())throw new IllegalArgumentException("current objects required");
  return current=new Token(generation,context);
 }
 public synchronized boolean install(Token token,Context context){
  if(token==null||token!=current||consumed||!unchanged(token,context))return false;
  consumed=true;installed=true;return true;
 }
 public synchronized boolean unchanged(Token token,Context context){
  return token!=null&&token==current&&token.generation==generation&&token.context.same(context);
 }
 public synchronized boolean active(Token token,Context context){return installed&&unchanged(token,context);}
 public synchronized void invalidate(){generation=Math.incrementExact(generation);current=null;installed=false;consumed=false;}
 public synchronized boolean concerns(Object object){return current!=null&&object!=null&&(object==current.context.normal||object==current.context.resources||object==current.context.pipeline);}
}
