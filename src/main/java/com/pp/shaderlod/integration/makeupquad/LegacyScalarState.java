package com.pp.shaderlod.integration.makeupquad;
/** Scalar from the actual committed native count writes; no command/index storage retained. */
public final class LegacyScalarState {
 /** Allocation-free publication target for a batch that has never admitted scalar recording. */
 public static final LegacyScalarState INACTIVE = new LegacyScalarState(false);
 /** Batch-local recording scope; the native helper already carries this exact batch argument. */
 public interface Owner {
  void shaderlod$enterScalarFill();
  void shaderlod$beginScalar(long generation);
  void shaderlod$beginScalar(long generation,boolean compactionOnly);
  LegacyScalarState shaderlod$recordingScalar();
  void shaderlod$leaveScalarFill(boolean completed);
 }
 private final boolean writable;
 private boolean active,finished,compactionOnly,published;
 private int observed,maximum,capacity;
 private long generation;
 public LegacyScalarState(){this(true);}
 private LegacyScalarState(boolean writable){this.writable=writable;}
 public void invalidate(){active=finished=compactionOnly=published=false;observed=maximum=0;}
 public void begin(boolean allowed,int capacity,long generation){begin(allowed,capacity,generation,false);}
 /** Compaction-only fills require fresh checked publication before successful scope completion. */
 public void begin(boolean allowed,int capacity,long generation,boolean compactionOnly){if(!writable)return;invalidate();this.active=allowed&&capacity>=0;this.capacity=capacity;this.generation=generation;this.compactionOnly=compactionOnly;}
 public boolean recording(){return active&&!finished&&!compactionOnly;}
 public void emitted(int value){if(!active)return;if(compactionOnly||observed>=capacity){invalidate();return;}maximum=Math.max(maximum,value);observed++;}
 public void finish(int nativeSize){
  if(compactionOnly&&(!published||nativeSize!=observed||nativeSize<0||nativeSize>capacity)){invalidate();return;}
  finished=active&&nativeSize==observed&&nativeSize>=0&&nativeSize<=capacity;
 }
 public boolean reusable(int nativeSize,long generation){return finished&&active&&nativeSize==observed&&generation==this.generation;}
 public int maximum(){if(!finished)throw new IllegalStateException("Unfinished native scalar");return maximum;}

 /** Publish only a complete coalescer proof, never a pre-merge maximum or refused result.
  * Per-face recording supports either RETURN-hook order. Compaction-only mode accepts one
  * publication while pending, then requires scope completion; a lone/late stamp cannot finish it.
  * The caller must pass the actual same-call native before/after sizes and freshly checked maximum.
  */
 public boolean compacted(int beforeSize,int afterSize,int checkedMaximum,long currentGeneration) {
  if(!active)return false;
  if(generation!=currentGeneration||(compactionOnly?(published||finished):observed!=beforeSize)||beforeSize<0||beforeSize>capacity
      ||afterSize<0||afterSize>beforeSize||!NativeIndexGrowth.requestSafe(checkedMaximum)
      ||(afterSize==0)!=(checkedMaximum==0)||(beforeSize==0)!=(afterSize==0)) {
   invalidate();return false;
  }
  observed=afterSize;maximum=checkedMaximum;published=true;return true;
 }
}
