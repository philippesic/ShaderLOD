package com.pp.shaderlod.optimization;
import java.nio.file.Path;import java.util.*;
import com.pp.shaderlod.optimization.sourcecache.EarlyAlphaSourceCache;
/** Tests load verification bytes directly; this never fabricates a published rendering receipt. */
final class LoadSourceValidationFixture {
 static boolean check(EarlyAlphaSourceCache cache,Class<?> owner,Path dir,String name,String family,Map<String,String> options,Object pack,Object values,Object declared,Object pipeline,Object world,long epoch,Object core,Collection<String> resources,EarlyAlphaSourceCache.Validation validation)throws Exception {
  var method=EarlyAlphaSourceCache.class.getDeclaredMethod("validateAtLoad",Class.class,Path.class,String.class,String.class,Map.class,Object.class,Object.class,Object.class,Object.class,Object.class,long.class,Object.class,Collection.class,EarlyAlphaSourceCache.Validation.class);
  method.setAccessible(true);
  try{return (boolean)method.invoke(cache,owner,dir,name,family,options,pack,values,declared,pipeline,world,epoch,core,resources,validation);}
  catch(java.lang.reflect.InvocationTargetException failure){var cause=failure.getCause();if(cause instanceof Exception e)throw e;if(cause instanceof Error e)throw e;throw failure;}
 }
}
