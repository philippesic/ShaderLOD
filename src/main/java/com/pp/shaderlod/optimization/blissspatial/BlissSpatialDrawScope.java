package com.pp.shaderlod.optimization.blissspatial;
/** Existing native draw boundary with unconditional state recovery, also when preparation/draw throws. */
public final class BlissSpatialDrawScope{private BlissSpatialDrawScope(){}public static void draw(Runnable before,Runnable nativeDraw,Runnable returned,Runnable abort,Runnable restore){boolean success=false;try{before.run();nativeDraw.run();returned.run();success=true;}finally{try{if(!success)abort.run();}finally{restore.run();}}}
public static void draw(Runnable before,int mode,long counts,int type,long indices,int drawcount,long bases,com.pp.shaderlod.optimization.nativechain.NativeDraw nativeDraw,Runnable returned,Runnable abort,Runnable restore){boolean success=false;try{before.run();nativeDraw.draw(mode,counts,type,indices,drawcount,bases);returned.run();success=true;}finally{try{if(!success)abort.run();}finally{restore.run();}}}
}
