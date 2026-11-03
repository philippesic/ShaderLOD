package com.pp.shaderlod.optimization.solasao;
import java.util.*;import java.util.function.Supplier;
/** One full byte/source proof per immutable native frame; never reused across frames. */
public final class SolasAoFrameProof {
 public record Key(Object pack,Object pipeline,Object targets,long frame,long source,long flags,int width,int height,String selected,Object controls){public Key(Object pack,Object pipeline,Object targets,long frame,long source,long flags,int width,int height,String selected){this(pack,pipeline,targets,frame,source,flags,width,height,selected,null);}}
 private Key key;private Map<String,String> options;private boolean attempted;
 public void reset(){key=null;options=null;attempted=false;}
 public boolean qualify(Key next,boolean requested,boolean loadedHalf,Supplier<Map<String,String>> actualVerifier){
  if(!requested||!loadedHalf||next==null)return false;
  if(attempted)return same(next)&&options!=null;
  attempted=true;key=next;Map<String,String> actual;try{actual=actualVerifier.get();}catch(RuntimeException|LinkageError unavailable){return false;}if(!SolasAoContract.options(actual)||!"1".equals(actual.get(SolasAoContract.OPTION)))return false;options=Map.copyOf(actual);return true;
 }
 public boolean current(Key next,Map<String,String> actual){return currentFailure(next,actual)==null;}
 /** Null is current. Detail is allocated only after a failed comparison, before the caller resets this proof. */
 public String currentFailure(Key n,Map<String,String> actual){
  if(key==null)return "key missing";if(n==null)return "current key missing";
  if(key.pack()!=n.pack())return "pack identity";
  if(key.pipeline()!=n.pipeline())return "pipeline identity";
  if(key.targets()!=n.targets())return "targets identity";
  if(key.frame()!=n.frame())return "frame serial";
  if(key.source()!=n.source())return "source generation";
  if(key.flags()!=n.flags())return "flag generation";
  if(key.width()!=n.width())return "width";
  if(key.height()!=n.height())return "height";
  if(!Objects.equals(key.selected(),n.selected()))return "selected path";
  if(!Objects.equals(key.controls(),n.controls()))return "controls";
  if(options==null)return "qualified options missing";
  if(options.equals(actual))return null;
  if(actual==null)return "current options missing";
  var changed=new TreeSet<String>();changed.addAll(options.keySet());changed.addAll(actual.keySet());
  changed.removeIf(k->options.containsKey(k)==actual.containsKey(k)&&Objects.equals(options.get(k),actual.get(k)));
  return "options changed keys="+changed;
 }
 private boolean same(Key n){return key!=null&&n!=null&&key.pack()==n.pack()&&key.pipeline()==n.pipeline()&&key.targets()==n.targets()&&key.frame()==n.frame()&&key.source()==n.source()&&key.flags()==n.flags()&&key.width()==n.width()&&key.height()==n.height()&&Objects.equals(key.selected(),n.selected())&&Objects.equals(key.controls(),n.controls());}
}
