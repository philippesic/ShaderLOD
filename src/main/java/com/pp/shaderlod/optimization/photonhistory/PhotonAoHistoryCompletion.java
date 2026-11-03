package com.pp.shaderlod.optimization.photonhistory;
import java.util.function.Supplier;
/** The production AO-owner completion state. Diagnostics cannot retain a prior completed history. */
public final class PhotonAoHistoryCompletion {
 private PhotonAoHistoryContract.Frame current;
 public PhotonAoHistoryContract.Frame current(){return current;}
 public void clear(){current=null;}
 public void finish(PhotonAoHistoryContract.Frame expected,Supplier<PhotonAoHistoryContract.Frame> observation,Runnable revoke){
  clear();
  try{var observed=observation.get();if(expected!=null&&expected.equals(observed)){current=observed;return;}}
  catch(RuntimeException|LinkageError unavailable){/* Native draw already returned; observation is diagnostic. */}
  try{revoke.run();}catch(RuntimeException|LinkageError unavailable){/* Preserve native draw; permission write is best effort if GL is unavailable. */}
 }
}
