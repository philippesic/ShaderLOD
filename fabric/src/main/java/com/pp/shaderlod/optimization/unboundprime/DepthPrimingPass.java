package com.pp.shaderlod.optimization.unboundprime;
/** One production native-submission scope. Only verified, recoverable pre-submit faults may fall through. */
public final class DepthPrimingPass {
 private DepthPrimingPass(){}
 public interface Work {boolean prepare();void prime(Submission submission);void restore();void verify();void disable();}
 public static final class Submission {private boolean submitted;public boolean submitted(){return submitted;}public void draw(Runnable call){submitted=true;call.run();}}
 private static Throwable append(Throwable primary,Throwable extra){if(primary==null)return extra;if(primary!=extra)try{primary.addSuppressed(extra);}catch(Throwable cannotRecord){/* Keep exact primary; low-memory diagnostics must not skip later cleanup. */}return primary;}
 @SuppressWarnings("unchecked") private static <T extends Throwable> void rethrow(Throwable original)throws T{throw (T)original;}
 public static boolean run(Work work){var scope=new Submission();Throwable failure=null;boolean ready=false,restored=false,verified=false,disabled=false;
  try{ready=work.prepare();if(ready)work.prime(scope);}catch(Throwable original){failure=original;}
  finally{
   try{work.restore();restored=true;}catch(Throwable cleanup){failure=append(failure,cleanup);}
   try{work.verify();verified=true;}catch(Throwable cleanup){failure=append(failure,cleanup);}
   if(failure!=null)try{work.disable();disabled=true;}catch(Throwable cleanup){failure=append(failure,cleanup);}
  }
  if(failure!=null){if(!scope.submitted()&&restored&&verified&&disabled&&(failure instanceof RuntimeException||failure instanceof LinkageError))return false;DepthPrimingPass.<RuntimeException>rethrow(failure);}
  return ready;
 }
}
