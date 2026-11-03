package com.pp.shaderlod.optimization.makeupprime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL33C;

/** Diagnostic only: one whole SOLID-pass query; results are polled only after the arm. */
final class C01AsyncWitness {
 private static final int CAPACITY=64;
 private final int[] ids=new int[CAPACITY],frameIds=new int[CAPACITY];
 private final long[] expected=new long[CAPACITY],proxy=new long[CAPACITY];
 private final boolean[] pending=new boolean[CAPACITY],valid=new boolean[CAPACITY];
 private final List<Map<String,Object>> records=new ArrayList<>();
 private int active=-1,cursor,dropped;
 C01AsyncWitness(){GL15C.glGenQueries(ids);}
 int activeQuery(){return active<0?0:ids[active];}
 void begin(){
  if(GL15C.glGetQueryi(GL30C.GL_PRIMITIVES_GENERATED,GL15C.GL_CURRENT_QUERY)!=0){dropped++;return;}
  for(int n=0;n<CAPACITY;n++){
   int i=(cursor+n)%CAPACITY;if(pending[i])continue;
   active=i;cursor=(i+1)%CAPACITY;GL15C.glBeginQuery(GL30C.GL_PRIMITIVES_GENERATED,ids[i]);return;
  }
  dropped++;
 }
 void finish(int frame,long nativeIndices,long queries,boolean admitted){
  if(active<0)return;
  GL15C.glEndQuery(GL30C.GL_PRIMITIVES_GENERATED);
  frameIds[active]=frame;expected[active]=nativeIndices/3;proxy[active]=queries*2;
  valid[active]=admitted&&nativeIndices%3==0;pending[active]=true;active=-1;
 }
 void drain(){
  // Only called after finite submission ends. Even AVAILABLE may implicitly flush GL.
  for(int i=0;i<CAPACITY;i++)if(pending[i]&&GL15C.glGetQueryObjecti(ids[i],GL15C.GL_QUERY_RESULT_AVAILABLE)!=0){
   long actual=GL33C.glGetQueryObjectui64(ids[i],GL15C.GL_QUERY_RESULT);
   long skipped=expected[i]+proxy[i]-actual;
   if(records.size()<20000)records.add(Map.of("frame",frameIds[i],"native_expected_primitives",expected[i],"proxy_primitives",proxy[i],"actual_total_primitives",actual,"suppressed_native_primitives",skipped,"admitted",valid[i],"count_consistent",skipped>=0&&skipped<=expected[i]));
   pending[i]=false;
  }
 }
 Map<String,Object> snapshot(boolean finished){if(finished)drain();int n=0;for(boolean b:pending)if(b)n++;return Map.of("records",List.copyOf(records),"pending",n,"dropped_frames",dropped,"method","One whole SOLID-pass PRIMITIVES_GENERATED query; no result or availability polls during finite submission; AVAILABLE then result only after arm ends. Native index/3 plus two proxy triangles/query minus actual total. Diagnostic instrumentation, separate from lean timing.");}
 void close(){if(active>=0)GL15C.glEndQuery(GL30C.GL_PRIMITIVES_GENERATED);GL15C.glDeleteQueries(ids);active=-1;}
}
