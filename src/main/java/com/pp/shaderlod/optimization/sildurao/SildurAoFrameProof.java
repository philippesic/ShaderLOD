package com.pp.shaderlod.optimization.sildurao;
import java.util.*;import java.util.function.Supplier;
/** One full byte/source proof per immutable native frame; never reused across frames. */
public final class SildurAoFrameProof {
 public record Key(Object pack,Object pipeline,Object targets,long frame,long source,long flags,int width,int height,String selected,Object controls){public Key(Object pack,Object pipeline,Object targets,long frame,long source,long flags,int width,int height,String selected){this(pack,pipeline,targets,frame,source,flags,width,height,selected,null);}}
 private Key key;private Map<String,String> options;private boolean attempted;
 public void reset(){key=null;options=null;attempted=false;}
 public boolean qualify(Key next,boolean requested,boolean loadedHalf,Supplier<Map<String,String>> actualVerifier){
  if(!requested||!loadedHalf||next==null)return false;
  if(attempted)return same(next)&&options!=null;
  attempted=true;key=next;Map<String,String> actual;try{actual=actualVerifier.get();}catch(RuntimeException|LinkageError unavailable){return false;}if(!SildurAoContract.options(actual)||!"1".equals(actual.get(SildurAoContract.OPTION)))return false;options=Map.copyOf(actual);return true;
 }
 public boolean current(Key next,Map<String,String> actual){return same(next)&&options!=null&&options.equals(actual);}
 private boolean same(Key n){return key!=null&&n!=null&&key.pack()==n.pack()&&key.pipeline()==n.pipeline()&&key.targets()==n.targets()&&key.frame()==n.frame()&&key.source()==n.source()&&key.flags()==n.flags()&&key.width()==n.width()&&key.height()==n.height()&&Objects.equals(key.selected(),n.selected())&&Objects.equals(key.controls(),n.controls());}
}
